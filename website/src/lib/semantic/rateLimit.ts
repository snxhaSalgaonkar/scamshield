// Sliding-window limiter, in memory (per server instance — enough for a demo; use Redis/Upstash at scale).
const WINDOW_MS = 60_000

const hits = new Map<string, number[]>()

/** true if this call is allowed; records it. */
export function allow(key: string, limit = 30, now = Date.now()): boolean {
  const recent = (hits.get(key) ?? []).filter((t) => now - t < WINDOW_MS)
  if (recent.length >= limit) {
    hits.set(key, recent)
    return false
  }
  recent.push(now)
  hits.set(key, recent)
  if (hits.size > 10_000) {
    for (const [k, ts] of hits) if (!ts.some((t) => now - t < WINDOW_MS)) hits.delete(k)
  }
  return true
}
