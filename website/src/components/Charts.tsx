'use client'

import type { ReactNode } from 'react'
import { Bar, BarChart, CartesianGrid, Cell, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { Skeleton } from '@/components/ui/skeleton'
import { GRID, INK, TICK, tooltipStyle } from '@/lib/chartTheme'
import { clockTime, shortDate } from '@/lib/format'
import { detectedViaLabel, scamTypeShort } from '@/lib/labels'
import { DETECTED_VIA, SCAM_TYPES, type Stats } from '@/lib/types'

export function ChartCard({ title, description, children, height = 'h-60' }: { title: string; description: string; children: ReactNode; height?: string }) {
  return (
    <section className="surface flex min-w-0 flex-col gap-3 p-5">
      <div>
        <h2 className="font-display text-lg leading-tight font-semibold text-navy">{title}</h2>
        <p className="text-[13px] text-muted-foreground">{description}</p>
      </div>
      <div className={`w-full min-w-0 ${height}`}>{children}</div>
    </section>
  )
}

export function OverTimeChart({ stats }: { stats?: Stats }) {
  const hourly = stats?.range === '24h'
  return (
    <ChartCard title="Reports over time" description={hourly ? 'Per hour, last 24 hours' : 'Per day, last 7 days'}>
      {!stats ? (
        <Skeleton className="h-full w-full" />
      ) : (
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={stats.overTime} margin={{ top: 8, right: 12, bottom: 0, left: -20 }}>
            <CartesianGrid stroke={GRID} vertical={false} />
            <XAxis
              dataKey="ts"
              tickFormatter={(v: string) => (hourly ? clockTime(v) : shortDate(v))}
              tick={TICK}
              tickLine={false}
              axisLine={{ stroke: GRID }}
              interval={hourly ? 3 : 0}
            />
            <YAxis allowDecimals={false} tick={TICK} tickLine={false} axisLine={false} />
            <Tooltip
              contentStyle={tooltipStyle}
              labelFormatter={(v) => (hourly ? clockTime(String(v)) : shortDate(String(v)))}
              formatter={(v) => [v, 'Reports']}
            />
            <Line type="monotone" dataKey="count" stroke={INK} strokeWidth={2} dot={!hourly} isAnimationActive={false} activeDot={{ r: 4, stroke: '#fff', strokeWidth: 2 }} />
          </LineChart>
        </ResponsiveContainer>
      )}
    </ChartCard>
  )
}

function HBar({ rows, label, color = INK }: { rows: { name: string; count: number; fill?: string }[]; label: string; color?: string }) {
  return (
    <ResponsiveContainer width="100%" height="100%">
      <BarChart data={rows} layout="vertical" margin={{ top: 4, right: 24, bottom: 0, left: 8 }} barCategoryGap={8}>
        <CartesianGrid stroke={GRID} horizontal={false} />
        <XAxis type="number" allowDecimals={false} tick={TICK} tickLine={false} axisLine={{ stroke: GRID }} />
        <YAxis type="category" dataKey="name" tick={TICK} tickLine={false} axisLine={false} width={108} interval={0} />
        <Tooltip contentStyle={tooltipStyle} cursor={{ fill: 'rgb(7 32 63 / 0.06)' }} formatter={(v) => [v, label]} />
        <Bar dataKey="count" radius={[0, 4, 4, 0]} barSize={16} isAnimationActive={false}>
          {rows.map((r) => (
            <Cell key={r.name} fill={r.fill ?? color} />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  )
}

export function ScamTypeChart({ stats }: { stats?: Stats }) {
  const rows = stats
    ? SCAM_TYPES.map((t) => ({ name: scamTypeShort[t], count: stats.byScamType[t] ?? 0 }))
        .filter((r) => r.count > 0)
        .sort((a, b) => b.count - a.count)
    : []
  return (
    <ChartCard title="Scam types" description="Reports by suspected scam type">
      {!stats ? <Skeleton className="h-full w-full" /> : <HBar rows={rows} label="Reports" />}
    </ChartCard>
  )
}

/** Shows the system works without audio: SMS, reputation and correlation catch scams on their own. */
export function DetectedViaChart({ stats }: { stats?: Stats }) {
  const rows = stats
    ? DETECTED_VIA.filter((v) => v !== 'manual_report' || stats.byDetectedVia[v]).map((v) => ({
        name: detectedViaLabel[v],
        count: stats.byDetectedVia[v] ?? 0,
        fill: v === 'voice' ? '#A65E46' : INK,
      }))
    : []
  return (
    <ChartCard title="Detected via" description="Reports per detection signal (a report can have several). Only Voice needs audio.">
      {!stats ? <Skeleton className="h-full w-full" /> : <HBar rows={rows} label="Reports" />}
    </ChartCard>
  )
}
