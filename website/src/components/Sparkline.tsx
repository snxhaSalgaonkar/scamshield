/** Tiny trend line for a readout cell. Single series, no axes; the readout it sits beside names it. */
export function Sparkline({ values, className, stroke = 'var(--police-navy)' }: { values: number[]; className?: string; stroke?: string }) {
  if (values.length < 2) return null
  const w = 120
  const h = 32
  const max = Math.max(1, ...values)
  const step = w / (values.length - 1)
  const pts = values.map((v, i) => [i * step, h - 3 - (v / max) * (h - 6)] as const)
  const line = pts.map(([x, y], i) => `${i ? 'L' : 'M'}${x.toFixed(1)},${y.toFixed(1)}`).join(' ')
  const [lx, ly] = pts[pts.length - 1]
  return (
    <svg viewBox={`0 0 ${w} ${h}`} className={className} aria-hidden preserveAspectRatio="none">
      <path d={`${line} L${w},${h} L0,${h} Z`} fill={stroke} fillOpacity=".07" />
      <path d={line} fill="none" stroke={stroke} strokeWidth="1.75" strokeLinejoin="round" strokeLinecap="round" vectorEffect="non-scaling-stroke" />
      <circle cx={lx} cy={ly} r="2.5" fill={stroke} />
    </svg>
  )
}
