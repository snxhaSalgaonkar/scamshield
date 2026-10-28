import type { ReactNode } from 'react'
import { Bar, BarChart, CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { Skeleton } from '@/components/ui/skeleton'
import { useStats } from '@/hooks/useStats'
import { GRID, INK, TICK, tooltipStyle } from '@/lib/chartTheme'
import { clockTime } from '@/lib/format'


export function ChartCard({
  title,
  description,
  children,
  height = 'h-56',
}: {
  title: string
  description: string
  children: ReactNode
  height?: string
}) {
  return (
    <section className="surface flex flex-col gap-3 p-5">
      <div>
        <h2 className="font-display text-lg leading-tight font-semibold text-navy">{title}</h2>
        <p className="text-[13px] text-muted-foreground">{description}</p>
      </div>
      <div className={height}>{children}</div>
    </section>
  )
}

export function OverTimeChart() {
  const { data } = useStats()
  return (
    <ChartCard title="Alerts over time" description="Per hour, last 12 hours">
      {!data ? (
        <Skeleton className="h-full w-full" />
      ) : (
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={data.overTime} margin={{ top: 8, right: 12, bottom: 0, left: -20 }}>
            <CartesianGrid stroke={GRID} vertical={false} />
            <XAxis dataKey="ts" tickFormatter={clockTime} tick={TICK} tickLine={false} axisLine={{ stroke: GRID }} interval={2} />
            <YAxis allowDecimals={false} tick={TICK} tickLine={false} axisLine={false} />
            <Tooltip
              contentStyle={tooltipStyle}
              labelFormatter={(v) => clockTime(String(v))}
              formatter={(v) => [v, 'Alerts']}
            />
            <Line
              type="monotone"
              dataKey="count"
              stroke={INK}
              strokeWidth={2}
              dot={false}
              isAnimationActive={false}
              activeDot={{ r: 4, stroke: '#fff', strokeWidth: 2 }}
            />
          </LineChart>
        </ResponsiveContainer>
      )}
    </ChartCard>
  )
}

export function ScamTypeChart() {
  const { data } = useStats()
  const rows = data
    ? Object.entries(data.byType)
        .map(([type, count]) => ({ type, count }))
        .sort((a, b) => b.count - a.count)
    : []
  return (
    <ChartCard title="Scam type breakdown" description="Alerts by suspected type, last 12 hours">
      {!data ? (
        <Skeleton className="h-full w-full" />
      ) : (
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={rows} layout="vertical" margin={{ top: 4, right: 24, bottom: 0, left: 8 }} barCategoryGap={12}>
            <CartesianGrid stroke={GRID} horizontal={false} />
            <XAxis type="number" allowDecimals={false} tick={TICK} tickLine={false} axisLine={{ stroke: GRID }} />
            <YAxis type="category" dataKey="type" tick={TICK} tickLine={false} axisLine={false} width={104} />
            <Tooltip contentStyle={tooltipStyle} cursor={{ fill: '#f1f5f9' }} formatter={(v) => [v, 'Alerts']} />
            <Bar dataKey="count" fill={INK} radius={[0, 4, 4, 0]} barSize={18} isAnimationActive={false} />
          </BarChart>
        </ResponsiveContainer>
      )}
    </ChartCard>
  )
}
