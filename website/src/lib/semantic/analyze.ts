import 'server-only'
import { z } from 'zod'
import { llmVerdict, type LlmVerdict } from './llm'
import { similarity } from './similarity'

export const AnalyzeBody = z.object({
  text: z.string().trim().min(1).max(2000),
  lang_hint: z.enum(['en', 'hi', 'hinglish']).nullable().optional(),
})

/** Response shape, exactly SHARED_CONTRACT.md §6. */
export interface AnalyzeResponse {
  similarity: { score: number; matched: string }
  llm: LlmVerdict | null
  semantic_score: number
}

/** Similarity model and LLM in parallel; semantic_score = max of the two. */
export async function analyze(text: string): Promise<AnalyzeResponse> {
  const [sim, llm] = await Promise.all([similarity(text), llmVerdict(text)])
  return {
    similarity: { score: sim.score, matched: sim.matched },
    llm,
    semantic_score: Math.max(sim.score, llm?.fraud_probability ?? 0),
  }
}
