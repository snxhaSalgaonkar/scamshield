'use client'

import { useMemo, useState } from 'react'
import { FilterSelect } from '@/components/FilterSelect'
import { Skeleton } from '@/components/ui/skeleton'
import { useWindowReports } from '@/hooks/realtime'
import { circleActivity, type CircleActivity } from '@/lib/analytics'
import { CIRCLES } from '@/lib/circles'
import { INDIA_PATH, MAP } from '@/lib/indiaOutline'
import { scamTypeLabel } from '@/lib/labels'
import { SCAM_TYPES, type ScamType } from '@/lib/types'

const { W, H, PAD, LON, LAT, KX, SCALE } = MAP
const px = (lon: number) => PAD + (lon - LON[0]) * KX * SCALE
const py = (lat: number) => H - PAD - (lat - LAT[0]) * SCALE

const TYPE_ITEMS = [{ value: 'all', label: 'All' }, ...SCAM_TYPES.map((t) => ({ value: t, label: scamTypeLabel[t] }))]

/** White → deep red by share of the busiest circle. */
function heat(share: number): string {
  const t = Math.max(0, Math.min(1, share))
  const l = 0.93 - t * 0.48
  const c = 0.03 + t * 0.17
  return `oklch(${l.toFixed(3)} ${c.toFixed(3)} 27)`
}

export default function MapPage() {
  const { data: reports } = useWindowReports()
  const [type, setType] = useState<'all' | ScamType>('all')
  const [hover, setHover] = useState<string | null>(null)

  const activity = useMemo(() => {
    if (!reports) return undefined
    return circleActivity(type === 'all' ? reports : reports.filter((r) => r.scam_type === type))
  }, [reports, type])

  const byName = useMemo(() => new Map((activity ?? []).map((a) => [a.circle, a])), [activity])
  const max = activity?.[0]?.count ?? 1
  const unknown = reports ? (type === 'all' ? reports : reports.filter((r) => r.scam_type === type)).filter((r) => !r.telecom_circle).length : 0
  const hovered: CircleActivity | undefined = hover ? byName.get(hover) : undefined

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-display text-[32px] leading-tight font-semibold tracking-tight text-navy-deep">Telecom-circle map</h1>
          <p className="text-[15px] text-muted-foreground">Reports in the last 7 days by the caller number&apos;s telecom circle.</p>
        </div>
        <FilterSelect label="Scam type" value={type} items={TYPE_ITEMS} onChange={(v) => setType(v as 'all' | ScamType)} />
      </div>

      <div className="grid grid-cols-[minmax(0,1.1fr)_minmax(0,1fr)] gap-5 max-lg:grid-cols-1">
        <section className="surface relative p-5" aria-label="Map">
          {!activity ? (
            <Skeleton className="aspect-[520/574] w-full" />
          ) : (
            <svg viewBox={`0 0 ${W} ${H}`} className="h-auto w-full" role="img" aria-label="Map of India with a circle per telecom circle, coloured and sized by report count">
              <path d={INDIA_PATH} fill="#f4ece5" stroke="#b9a596" strokeWidth="1.2" strokeLinejoin="round" />
              {CIRCLES.map((c) => {
                const a = byName.get(c.name)
                const count = a?.count ?? 0
                const r = count ? 9 + Math.sqrt(count / max) * 24 : 5
                const cx = px(c.lon)
                const cy = py(c.lat)
                return (
                  <g
                    key={c.name}
                    onMouseEnter={() => setHover(c.name)}
                    onMouseLeave={() => setHover((h) => (h === c.name ? null : h))}
                    onFocus={() => setHover(c.name)}
                    onBlur={() => setHover(null)}
                    tabIndex={0}
                    role="button"
                    aria-label={`${c.name}: ${count} reports${a ? `, mostly ${scamTypeLabel[a.topScamType]}` : ''}`}
                    className="cursor-default outline-none"
                  >
                    <circle cx={cx} cy={cy} r={r} fill={count ? heat(count / max) : '#ffffff'} fillOpacity={count ? 0.92 : 0.6} stroke={hover === c.name ? '#07203F' : count ? '#8f1610' : '#b9a596'} strokeWidth={hover === c.name ? 2.5 : 1} />
                    {count > 0 && (
                      <text x={cx} y={cy} textAnchor="middle" dominantBaseline="central" fontSize={count / max > 0.5 ? 13 : 11} fontWeight={700} fill={count / max > 0.5 ? '#fff' : '#5a1410'} className="pointer-events-none font-display">
                        {count}
                      </text>
                    )}
                  </g>
                )
              })}
            </svg>
          )}
          {hovered && (
            <div className="pointer-events-none absolute top-5 right-5 rounded-lg bg-navy px-3 py-2 text-sm text-white shadow-lg" role="status">
              <p className="font-semibold">{hovered.circle}</p>
              <p className="text-white/80">
                {hovered.count} report{hovered.count === 1 ? '' : 's'} · {hovered.highRisk} high-risk
              </p>
              <p className="text-white/80">Top: {scamTypeLabel[hovered.topScamType]}</p>
            </div>
          )}
          {hover && !hovered && (
            <div className="pointer-events-none absolute top-5 right-5 rounded-lg bg-navy px-3 py-2 text-sm text-white shadow-lg" role="status">
              <p className="font-semibold">{hover}</p>
              <p className="text-white/80">No reports</p>
            </div>
          )}
          <p className="mt-3 text-xs text-muted-foreground">
            Circle derived from number series — approximate due to number portability. Bubble size and colour show report count. Outline: Survey of
            India boundary, simplified.
          </p>
        </section>

        <section className="surface p-5" aria-label="Reports by circle">
          <h2 className="font-display text-lg font-semibold text-navy">Reports by circle</h2>
          <p className="mb-3 text-[13px] text-muted-foreground">Red part = high-risk (score ≥ 75)</p>
          {!activity ? (
            <Skeleton className="h-80 w-full" />
          ) : activity.length === 0 ? (
            <p className="py-8 text-center text-sm text-muted-foreground">No reports for this scam type in the last 7 days.</p>
          ) : (
            <ol className="space-y-2.5">
              {activity.map((a) => (
                <li key={a.circle} onMouseEnter={() => setHover(a.circle)} onMouseLeave={() => setHover(null)}>
                  <div className="flex items-baseline justify-between gap-3 text-sm">
                    <span className="font-medium">{a.circle}</span>
                    <span className="text-muted-foreground">
                      <span className="text-[12px]">{scamTypeLabel[a.topScamType]} · </span>
                      <span className="font-display text-base font-semibold text-navy tabular-nums">{a.count}</span>
                    </span>
                  </div>
                  <div className="mt-1 flex h-2 overflow-hidden rounded-full bg-navy-soft">
                    <div className="h-full bg-red-700" style={{ width: `${(a.highRisk / max) * 100}%` }} />
                    <div className="h-full bg-navy/60" style={{ width: `${((a.count - a.highRisk) / max) * 100}%` }} />
                  </div>
                </li>
              ))}
            </ol>
          )}
          {unknown > 0 && <p className="mt-3 text-xs text-muted-foreground">{unknown} report(s) from number series not in the lookup table are not shown.</p>}
        </section>
      </div>
    </div>
  )
}
