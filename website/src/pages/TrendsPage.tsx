import { useQuery } from '@tanstack/react-query'
import { Fragment, useState } from 'react'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { ChartCard } from '@/components/Charts'
import { GRID, INK, TICK, tooltipStyle } from '@/lib/chartTheme'
import { Skeleton } from '@/components/ui/skeleton'
import { dataSource } from '@/lib/data'
import { pct, shortDate } from '@/lib/format'
import { cn } from '@/lib/utils'

const WINDOWS = [1, 7] as const
const SCAM_TYPES = ['Digital Arrest', 'OTP Theft', 'KYC Scam', 'False Positive']
// Risk bands: the one place green/amber/red appear in a chart, because the bars *are* risk levels.
const BAND = (bucket: number) => (bucket >= 7.5 ? '#b91c1c' : bucket >= 4 ? '#d97706' : '#15803d')

function Kpi({ label, value, note }: { label: string; value?: string; note?: string }) {
  return (
    <div className="px-6 py-5">
      <p className="text-sm font-medium text-muted-foreground">{label}</p>
      {value === undefined ? (
        <Skeleton className="mt-1 h-10 w-16" />
      ) : (
        <p className="mt-1 font-display text-[40px] leading-none font-semibold tabular-nums text-navy">{value}</p>
      )}
      {note && <p className="mt-2 text-xs text-muted-foreground">{note}</p>}
    </div>
  )
}

export function TrendsPage() {
  const [days, setDays] = useState<(typeof WINDOWS)[number]>(7)
  const { data } = useQuery({ queryKey: ['trends', days], queryFn: () => dataSource.getTrends(days) })

  const regions = data
    ? Object.entries(data.byRegion)
        .map(([region, count]) => ({ region, count }))
        .sort((a, b) => b.count - a.count)
    : []
  const hours = data?.byHourOfDay.map((count, hour) => ({ hour, count })) ?? []
  const histogram = data?.scoreHistogram.map((count, i) => ({ bucket: i, label: `${i * 10}–${i === 9 ? 100 : i * 10 + 9}`, count })) ?? []
  const matrixMax = data
    ? Math.max(1, ...Object.values(data.typeByRegion).flatMap((r) => Object.values(r)))
    : 1

  return (
    <div className="space-y-6">
      <div className="flex items-end justify-between">
        <div>
          <h1 className="font-display text-[32px] leading-tight font-semibold tracking-tight text-navy">Hotspots and trends</h1>
          <p className="text-[15px] text-muted-foreground">Where and when fraud calls cluster, by the region of the phone that reported them.</p>
        </div>
        <div role="tablist" aria-label="Time window" className="surface flex p-1">
          {WINDOWS.map((d) => (
            <button
              key={d}
              role="tab"
              aria-selected={days === d}
              onClick={() => setDays(d)}
              className={cn(
                'rounded px-3 py-1 text-sm font-medium',
                days === d ? 'bg-navy text-white' : 'text-muted-foreground hover:text-foreground',
              )}
            >
              {d === 1 ? 'Last 24 h' : `Last ${d} days`}
            </button>
          ))}
        </div>
      </div>

      <section aria-label="Summary" className="surface grid grid-cols-4 divide-x divide-border">
        <Kpi label="Alerts" value={data?.totalAlerts.toString()} note={days === 1 ? 'Last 24 hours' : `Last ${days} days`} />
        <Kpi label="Average risk score" value={data?.avgScore.toString()} note="Fused score out of 100" />
        <Kpi label="Share that were red flags" value={data ? pct(data.redFlagShare) : undefined} note="Score 75 or more" />
        <Kpi label="False-positive rate" value={data ? pct(data.falsePositiveRate) : undefined} note="As marked by officers" />
      </section>

      <div className="grid grid-cols-2 gap-4">
        <ChartCard title="Hotspots by region" description="Alerts per reporting region" height="h-64">
          {!data ? (
            <Skeleton className="h-full w-full" />
          ) : (
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={regions} layout="vertical" margin={{ top: 0, right: 24, bottom: 0, left: 8 }} barCategoryGap={6}>
                <CartesianGrid stroke={GRID} horizontal={false} />
                <XAxis type="number" allowDecimals={false} tick={TICK} tickLine={false} axisLine={{ stroke: GRID }} />
                <YAxis type="category" dataKey="region" tick={TICK} tickLine={false} axisLine={false} width={84} />
                <Tooltip contentStyle={tooltipStyle} cursor={{ fill: '#eef1f6' }} formatter={(v) => [v, 'Alerts']} />
                <Bar dataKey="count" fill={INK} radius={[0, 4, 4, 0]} isAnimationActive={false} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </ChartCard>

        <ChartCard title="Time of day" description="Alerts by hour (IST)" height="h-64">
          {!data ? (
            <Skeleton className="h-full w-full" />
          ) : (
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={hours} margin={{ top: 8, right: 8, bottom: 0, left: -20 }} barCategoryGap={2}>
                <CartesianGrid stroke={GRID} vertical={false} />
                <XAxis
                  dataKey="hour"
                  tick={TICK}
                  tickLine={false}
                  axisLine={{ stroke: GRID }}
                  interval={2}
                  tickFormatter={(h) => `${String(h).padStart(2, '0')}h`}
                />
                <YAxis allowDecimals={false} tick={TICK} tickLine={false} axisLine={false} />
                <Tooltip
                  contentStyle={tooltipStyle}
                  cursor={{ fill: '#eef1f6' }}
                  labelFormatter={(h) => `${String(h).padStart(2, '0')}:00–${String(h).padStart(2, '0')}:59`}
                  formatter={(v) => [v, 'Alerts']}
                />
                <Bar dataKey="count" fill={INK} radius={[3, 3, 0, 0]} isAnimationActive={false} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </ChartCard>

        <ChartCard title="Risk score distribution" description="Alerts per 10-point score band" height="h-64">
          {!data ? (
            <Skeleton className="h-full w-full" />
          ) : (
            <div className="flex h-full flex-col">
            <ul className="mb-1 flex justify-end gap-4 text-xs text-muted-foreground" aria-label="Legend">
              {(
                [
                  [0, 'Low, under 40'],
                  [5, 'Warning, 40 to 74'],
                  [9, 'Red flag, 75 and above'],
                ] as const
              ).map(([b, label]) => (
                <li key={label} className="flex items-center gap-1.5">
                  <span className="size-2.5 rounded-[2px]" style={{ background: BAND(b) }} aria-hidden />
                  {label}
                </li>
              ))}
            </ul>
            <div className="min-h-0 flex-1">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={histogram} margin={{ top: 16, right: 8, bottom: 0, left: -20 }} barCategoryGap={2}>
                <CartesianGrid stroke={GRID} vertical={false} />
                <XAxis dataKey="bucket" tick={TICK} tickLine={false} axisLine={{ stroke: GRID }} tickFormatter={(b) => String(b * 10)} />
                <YAxis allowDecimals={false} tick={TICK} tickLine={false} axisLine={false} />
                <Tooltip
                  contentStyle={tooltipStyle}
                  cursor={{ fill: '#eef1f6' }}
                  labelFormatter={(_, p) => `Score ${p?.[0]?.payload?.label ?? ''}`}
                  formatter={(v) => [v, 'Alerts']}
                />
                <Bar dataKey="count" radius={[3, 3, 0, 0]} isAnimationActive={false}>
                  {histogram.map((h) => (
                    <Cell key={h.bucket} fill={BAND(h.bucket)} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
            </div>
            </div>
          )}
        </ChartCard>

        <ChartCard title="Daily volume" description={`Alerts per day, last ${days === 1 ? '24 hours' : `${days} days`}`} height="h-64">
          {!data ? (
            <Skeleton className="h-full w-full" />
          ) : (
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={data.byDay} margin={{ top: 8, right: 8, bottom: 0, left: -20 }} barCategoryGap={10}>
                <CartesianGrid stroke={GRID} vertical={false} />
                <XAxis dataKey="ts" tick={TICK} tickLine={false} axisLine={{ stroke: GRID }} tickFormatter={(t) => shortDate(String(t))} />
                <YAxis allowDecimals={false} tick={TICK} tickLine={false} axisLine={false} />
                <Tooltip contentStyle={tooltipStyle} cursor={{ fill: '#eef1f6' }} labelFormatter={(t) => shortDate(String(t))} formatter={(v) => [v, 'Alerts']} />
                <Bar dataKey="count" fill={INK} radius={[3, 3, 0, 0]} isAnimationActive={false} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </ChartCard>
      </div>

      {/* Scam type × region matrix: single-hue sequential shading, value printed in every cell */}
      <section className="surface space-y-3 p-5">
        <div>
          <h2 className="font-display text-lg font-semibold text-navy">Scam type by region</h2>
          <p className="text-[13px] text-muted-foreground">Darker cells mean more alerts. Use this to brief district units.</p>
        </div>
        {!data ? (
          <Skeleton className="h-48" />
        ) : (
          <div className="grid gap-px overflow-hidden rounded border border-border bg-border text-sm" style={{ gridTemplateColumns: `140px repeat(${SCAM_TYPES.length}, 1fr)` }}>
            <div className="bg-navy-soft px-3 py-2 text-[13px] font-semibold text-navy/75">Region</div>
            {SCAM_TYPES.map((t) => (
              <div key={t} className="bg-navy-soft px-3 py-2 text-center text-[13px] font-semibold text-navy/75">
                {t}
              </div>
            ))}
            {regions.map(({ region }) => (
              <Fragment key={region}>
                <div className="bg-card px-3 py-2 font-medium">{region}</div>
                {SCAM_TYPES.map((t) => {
                  const v = data.typeByRegion[region]?.[t] ?? 0
                  const strength = v / matrixMax
                  return (
                    <div
                      key={t}
                      className={cn('px-3 py-2 text-center tabular-nums', strength > 0.55 ? 'text-white' : 'text-foreground')}
                      style={{ background: v ? `color-mix(in oklch, var(--police-navy) ${Math.round(12 + strength * 80)}%, white)` : 'var(--card)' }}
                    >
                      {v || <span className="text-muted-foreground">·</span>}
                    </div>
                  )
                })}
              </Fragment>
            ))}
          </div>
        )}
      </section>
    </div>
  )
}
