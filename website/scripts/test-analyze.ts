// Scores 15 scam and 10 genuine call snippets.
//   npm run test:analyze                       → calls the model in-process (similarity + LLM if LLM_API_KEY is set)
//   npm run test:analyze -- http://localhost:3000/api/analyze <token>   → calls the running endpoint
import { performance } from 'node:perf_hooks'

const SCAM = [
  'Sir this is CBI, a parcel in your name has been seized with drugs. You are under digital arrest.',
  'Aapke naam pe parcel mein drugs mile hain, call mat kaatna warna police aayegi.',
  'Please do not tell your family. Stay on the video call until the inquiry is complete.',
  'Transfer the amount to the RBI verification account, it will be refunded in 24 hours.',
  'Madam, I sent an OTP by mistake to your number. Can you please read it out to me?',
  'Aapke phone pe jo OTP aaya hai woh bata dijiye, transaction cancel ho jayega.',
  'Your KYC has expired. Your SBI account will be blocked today unless you verify now.',
  'Main bank se bol raha hoon, apna card number aur CVV bataiye.',
  'Please install AnyDesk from the Play Store and tell me the code on your screen.',
  'Refund ke liye yeh QR code scan kijiye aur UPI PIN daaliye.',
  'Congratulations, you won the KBC lucky draw of 25 lakh, pay the processing fee first.',
  'Your electricity will be disconnected tonight at 9:30, call the officer immediately.',
  'Mummy main musibat mein hoon, jaldi is UPI pe paise bhejo, papa ko mat batana.',
  'This is Mumbai police cyber cell. Your Aadhaar is linked to a money laundering case.',
  'Sorry wrong number, but my son entered your number by mistake, please tell me the code.',
]

const GENUINE = [
  'Hi, I am outside your building with the Amazon delivery, can you share the delivery OTP?',
  'Beta, did you eat lunch? Papa will send the school fees tomorrow.',
  'Hello, this is Dr. Mehta clinic, your appointment is confirmed for Friday at 5 pm.',
  'Bhaiya, the plumber will come at 11, keep the bathroom door open.',
  'Your HDFC credit card bill of 4,200 rupees is due on the 15th. Please pay on time.',
  'Are you coming for the cricket match on Sunday? We need one more player.',
  'Sir, the cab is waiting at gate number 2, I am in a white Swift.',
  'Mummy I reached the hostel safely, will call you at night.',
  'This is a reminder from the school about the parent teacher meeting on Saturday.',
  'Kal office mein meeting dus baje hai, presentation ready rakhna.',
]

type Result = { similarity: { score: number; matched: string }; llm: { fraud_probability: number; scam_type: string; reason: string } | null; semantic_score: number }

const [url, token] = process.argv.slice(2)

async function score(text: string): Promise<Result> {
  if (url) {
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token ?? 'demo'}` },
      body: JSON.stringify({ text, lang_hint: null }),
    })
    if (!res.ok) throw new Error(`${res.status} ${await res.text()}`)
    return (await res.json()) as Result
  }
  const { analyze } = await import('../src/lib/semantic/analyze')
  return analyze(text)
}

const pad = (s: string, n: number) => (s.length > n ? `${s.slice(0, n - 1)}…` : s.padEnd(n))

async function run(label: string, lines: string[]) {
  console.log(`\n${label}`)
  const scores: number[] = []
  const times: number[] = []
  for (const line of lines) {
    const t0 = performance.now()
    const r = await score(line)
    const ms = performance.now() - t0
    times.push(ms)
    scores.push(r.semantic_score)
    const llm = r.llm ? ` llm ${r.llm.fraud_probability.toFixed(2)} ${r.llm.scam_type}` : ' llm —'
    console.log(`  ${r.semantic_score.toFixed(2)}  sim ${r.similarity.score.toFixed(2)}${llm}  ${Math.round(ms)}ms  ${pad(line, 60)}  ≈ "${pad(r.similarity.matched, 40)}"`)
  }
  return { scores, times }
}

console.log(url ? `Endpoint: ${url}` : 'In-process (first call loads the model; can take a while on first download)')
if (!url) {
  const t0 = performance.now()
  const { loadModel } = await import('../src/lib/semantic/similarity')
  await loadModel()
  console.log(`Model ready in ${Math.round(performance.now() - t0)} ms`)
}

const scam = await run('SCAM (should be high)', SCAM)
const genuine = await run('GENUINE (should mostly be < 0.5)', GENUINE)

const avg = (a: number[]) => a.reduce((s, x) => s + x, 0) / a.length
const scamHigh = scam.scores.filter((s) => s >= 0.5).length
const genuineLow = genuine.scores.filter((s) => s < 0.5).length
const slowest = Math.max(...scam.times, ...genuine.times)
console.log(`\nScam ≥ 0.5: ${scamHigh}/${SCAM.length} (avg ${avg(scam.scores).toFixed(2)})`)
console.log(`Genuine < 0.5: ${genuineLow}/${GENUINE.length} (avg ${avg(genuine.scores).toFixed(2)})`)
console.log(`Slowest response: ${Math.round(slowest)} ms (budget 2500 ms)`)

const pass = scamHigh >= 12 && genuineLow >= 8 && slowest < 2500
console.log(pass ? '\nPASS' : '\nFAIL (targets: scam ≥ 12/15, genuine ≥ 8/10, every call < 2.5 s)')
process.exit(pass ? 0 : 1)
