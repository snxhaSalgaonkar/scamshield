import 'server-only'
import { env, pipeline, type FeatureExtractionPipeline } from '@huggingface/transformers'
import type { ScamType } from '@/lib/types'
import { SCAM_PHRASES } from './scam_phrases'

// Multilingual sentence embeddings (handles English, Hindi and Hinglish), int8-quantised (~120 MB).
const MODEL = 'Xenova/paraphrase-multilingual-MiniLM-L12-v2'

// Vercel's filesystem is read-only except /tmp; locally keep the download next to the project.
env.cacheDir = process.env.VERCEL ? '/tmp/transformers-cache' : './.cache/transformers'

export interface SimilarityResult {
  /** Best match, calibrated to 0–1 (see calibrate) */
  score: number
  matched: string
  scam_type: ScamType
}

interface Model {
  extract: FeatureExtractionPipeline
  /** Normalised phrase embeddings, row-major */
  phrases: Float32Array[]
}

let loading: Promise<Model> | null = null

async function embed(extract: FeatureExtractionPipeline, texts: string[]): Promise<Float32Array[]> {
  const out = await extract(texts, { pooling: 'mean', normalize: true })
  const [n, dim] = out.dims as [number, number]
  const data = out.data as Float32Array
  return Array.from({ length: n }, (_, i) => data.subarray(i * dim, (i + 1) * dim))
}

/** Loads the model and embeds the phrase bank once per server process (module-level singleton). */
export function loadModel(): Promise<Model> {
  loading ??= (async () => {
    const extract = (await pipeline('feature-extraction', MODEL, { dtype: 'q8' })) as FeatureExtractionPipeline
    const phrases = await embed(extract, SCAM_PHRASES.map((p) => p.text))
    // The first single-sentence inference is ~10 s slower (lazy ONNX graph init): pay it here, at startup.
    await embed(extract, ['warm up'])
    return { extract, phrases }
  })().catch((e) => {
    loading = null // allow a retry on the next request
    throw e
  })
  return loading
}

/** Sentences plus overlapping pairs, so a scam line split across two sentences still matches. */
export function chunks(text: string): string[] {
  const sentences = text
    .split(/(?<=[.!?।])\s+|\n+/)
    .map((s) => s.trim())
    .filter((s) => s.length >= 3)
  const pairs = sentences.slice(1).map((s, i) => `${sentences[i]} ${s}`)
  const all = [...sentences, ...pairs]
  return (all.length ? all : [text.trim()]).slice(0, 48)
}

const dot = (a: Float32Array, b: Float32Array) => {
  let s = 0
  for (let i = 0; i < a.length; i++) s += a[i] * b[i]
  return s
}

/**
 * Raw cosine between *unrelated* sentences is already 0.35–0.6 with this model, so it is rescaled:
 * ≤ 0.40 → 0, ≥ 0.85 → 1, linear between. Measured with scripts/test-analyze.ts: scam lines land
 * 0.58–1.0, everyday calls 0–0.49. Re-run that script if you change the model or the phrase bank.
 */
export const COSINE_FLOOR = 0.4
export const COSINE_CEIL = 0.85
export const calibrate = (cos: number) => Math.max(0, Math.min(1, (cos - COSINE_FLOOR) / (COSINE_CEIL - COSINE_FLOOR)))

/** Closest known scam line to any sentence of `text`. */
export async function similarity(text: string): Promise<SimilarityResult> {
  const { extract, phrases } = await loadModel()
  const vecs = await embed(extract, chunks(text))
  let best = { score: -1, index: 0 }
  for (const v of vecs) {
    for (let i = 0; i < phrases.length; i++) {
      const s = dot(v, phrases[i])
      if (s > best.score) best = { score: s, index: i }
    }
  }
  const p = SCAM_PHRASES[best.index]
  return { score: Math.round(calibrate(best.score) * 100) / 100, matched: p.text, scam_type: p.scam_type }
}
