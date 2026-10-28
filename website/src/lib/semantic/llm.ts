import 'server-only'
import Anthropic from '@anthropic-ai/sdk'
import { zodOutputFormat } from '@anthropic-ai/sdk/helpers/zod'
import { z } from 'zod'
import { SCAM_TYPES } from '@/lib/types'

/** The phone gets an answer in < 2.5 s, so the LLM gets 2 s and no retries. */
export const LLM_TIMEOUT_MS = 2000

export const LlmVerdict = z.object({
  fraud_probability: z.number().min(0).max(1),
  scam_type: z.enum(SCAM_TYPES),
  reason: z.string().max(200),
})
export type LlmVerdict = z.infer<typeof LlmVerdict>

export const SYSTEM_PROMPT = `You are an Indian cyber-fraud analyst. You receive a short, PII-scrubbed snippet of what a caller said to a citizen during a live phone call (English, Hindi or Hinglish; speech-to-text, so expect errors). Estimate how likely the call is a fraud attempt.

Indian scam patterns to recognise:
- digital_arrest: caller poses as CBI, police, customs, ED, NCB or RBI; claims a parcel with drugs, money laundering or an arrest warrant; demands the victim stay on a video call, tell no one, and move money for "verification".
- otp_theft: asks the victim to read out or share an OTP or verification code.
- kyc_bank_impersonation: poses as a bank; KYC expired, card or account will be blocked; asks for card number, CVV, PIN or password.
- remote_access: asks to install AnyDesk, TeamViewer, QuickSupport or an APK, or to share the screen or a code shown on screen.
- refund_lottery_qr: refund, cashback, lottery or prize that needs a QR scan, a UPI PIN or an upfront fee.
- utility_disconnection: electricity, gas or SIM will be cut tonight unless the victim pays or calls a number.
- wrong_number_distraction: "sorry, wrong number" followed by a request for a code that arrived by mistake.
- voice_clone_impersonation: a relative in an emergency asking for money urgently and in secret.
- other: job/task/investment/loan fees and other fraud.

Avoid false positives. Real banks, police and courts never ask for an OTP, PIN, CVV, password, remote access, money transfers for verification, or secrecy over the phone. A delivery agent asking for the delivery OTP at the door, a family member casually talking about money, a bank's genuine reminder without any request for credentials, or an ordinary conversation are normally genuine: give them a low probability (below 0.3) and scam_type "other".

Return fraud_probability between 0 and 1, the closest scam_type, and a reason of at most 12 words naming the tactics you saw.`

let client: Anthropic | null = null

async function anthropicVerdict(text: string, apiKey: string, model: string): Promise<LlmVerdict | null> {
  client ??= new Anthropic({ apiKey })
  const response = await client.messages.parse(
    {
      model,
      max_tokens: 256,
      // Latency first: a 2 s budget leaves no room for thinking on a one-shot classification.
      thinking: { type: 'disabled' },
      system: SYSTEM_PROMPT,
      messages: [{ role: 'user', content: `Call snippet:\n"""\n${text}\n"""` }],
      output_config: { format: zodOutputFormat(LlmVerdict), effort: 'low' },
    },
    { timeout: LLM_TIMEOUT_MS, maxRetries: 0 },
  )
  if (response.stop_reason !== 'end_turn') return null // refusal, max_tokens, …
  return response.parsed_output ?? null
}

async function geminiVerdict(text: string, apiKey: string, model: string): Promise<LlmVerdict | null> {
  const res = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'x-goog-api-key': apiKey },
    signal: AbortSignal.timeout(LLM_TIMEOUT_MS),
    body: JSON.stringify({
      systemInstruction: { parts: [{ text: SYSTEM_PROMPT }] },
      contents: [{ role: 'user', parts: [{ text: `Call snippet:\n"""\n${text}\n"""` }] }],
      generationConfig: {
        responseMimeType: 'application/json',
        responseSchema: {
          type: 'OBJECT',
          properties: {
            fraud_probability: { type: 'NUMBER' },
            scam_type: { type: 'STRING', enum: [...SCAM_TYPES] },
            reason: { type: 'STRING' },
          },
          required: ['fraud_probability', 'scam_type', 'reason'],
        },
        maxOutputTokens: 256,
      },
    }),
  })
  if (!res.ok) return null
  const body = (await res.json()) as { candidates?: { content?: { parts?: { text?: string }[] } }[] }
  const raw = body.candidates?.[0]?.content?.parts?.[0]?.text
  if (!raw) return null
  const parsed = LlmVerdict.safeParse(JSON.parse(raw))
  return parsed.success ? parsed.data : null
}

/**
 * LLM second opinion on a scrubbed snippet. Returns null on any failure (no key, timeout, refusal,
 * bad JSON) — the endpoint then answers with the similarity score alone. Never logs the text.
 */
export async function llmVerdict(text: string): Promise<LlmVerdict | null> {
  const provider = (process.env.LLM_PROVIDER ?? 'anthropic').toLowerCase()
  const apiKey = process.env.LLM_API_KEY
  if (!apiKey || provider === 'none') return null
  try {
    if (provider === 'gemini') return await geminiVerdict(text, apiKey, process.env.LLM_MODEL || 'gemini-2.5-flash')
    return await anthropicVerdict(text, apiKey, process.env.LLM_MODEL || 'claude-haiku-5-5')
  } catch {
    return null
  }
}
