// Fusion exactly as SHARED_CONTRACT.md §4. The phone owns the real computation; the dashboard
// uses this only to generate consistent mock / simulator reports.

/** Layer scores 0–1. Returns risk_score 0–100. */
export function fuse(l1: number, l2: number, l3: number, trusted = false): number {
  const base = 0.45 * l1 + 0.2 * l2 + 0.35 * l3
  let floor = 0
  if (!trusted) {
    if (l3 >= 0.9) floor = Math.max(floor, 0.8) // F1
    if (l3 >= 0.9 && (l1 >= 0.5 || l2 >= 0.6)) floor = Math.max(floor, 0.9) // F2
    if (l3 >= 0.95) floor = Math.max(floor, 0.9) // F2b
    if (l2 >= 0.9 && (l1 >= 0.4 || l3 >= 0.5)) floor = Math.max(floor, 0.8) // F3
    if (l1 >= 0.8) floor = Math.max(floor, 0.75) // F4
    if (l1 >= 0.8 && (l2 >= 0.4 || l3 >= 0.4)) floor = Math.max(floor, 0.9) // F5
  }
  if (l1 >= 0.5 || l3 >= 0.5 || l2 >= 0.4) floor = Math.max(floor, 0.4) // F6 (all callers)
  let score = Math.max(base, floor)
  if (trusted) score *= 0.5
  return Math.round(score * 100)
}
