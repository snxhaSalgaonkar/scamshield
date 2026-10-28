import { useQuery } from '@tanstack/react-query'
import { ArrowDown, ArrowUp } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { NumberStatusBadge } from '@/components/NumberStatusBadge'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useNow } from '@/hooks/useNow'
import { dataSource } from '@/lib/data'
import { dateTime, shortDate, timeAgo } from '@/lib/format'
import { numberTypeLabel } from '@/lib/risk'
import type { NumberRecord, NumberStatus } from '@/lib/types'
import { cn } from '@/lib/utils'

type SortKey = 'reportCount' | 'callsPerHour' | 'lastActivity' | 'firstSeen'

const COLUMNS: { key: SortKey | null; label: string; align?: 'right' }[] = [
  { key: null, label: 'Number' },
  { key: null, label: 'Type' },
  { key: 'reportCount', label: 'Reports', align: 'right' },
  { key: 'callsPerHour', label: 'Calls / hour', align: 'right' },
  { key: 'firstSeen', label: 'First seen' },
  { key: 'lastActivity', label: 'Last activity' },
  { key: null, label: 'Status' },
]

export function NumbersPage() {
  const navigate = useNavigate()
  const now = useNow()
  const [sort, setSort] = useState<{ key: SortKey; desc: boolean }>({ key: 'reportCount', desc: true })
  const { data } = useQuery({ queryKey: ['numbers'], queryFn: () => dataSource.getFlaggedNumbers() })

  const rows = useMemo(() => {
    if (!data) return undefined
    const value = (n: NumberRecord) =>
      sort.key === 'lastActivity' || sort.key === 'firstSeen' ? new Date(n[sort.key]).getTime() : n[sort.key]
    return [...data].sort((a, b) => (sort.desc ? value(b) - value(a) : value(a) - value(b)))
  }, [data, sort])

  const counts = useMemo(() => {
    const c: Record<NumberStatus, number> = { flagged: 0, watch: 0, expired: 0 }
    data?.forEach((n) => (c[n.status] += 1))
    return c
  }, [data])

  return (
    <div className="space-y-6">
      <div className="max-w-3xl">
        <h1 className="font-display text-[32px] leading-tight font-semibold tracking-tight text-navy">Flagged numbers</h1>
        <p className="text-[15px] text-muted-foreground">
          Once a number has enough independent reports it is flagged on every citizen&apos;s phone. Flags expire when reports stop,
          and can be appealed. Open a number to see its alerts.
        </p>
      </div>

      <section aria-label="Register summary" className="surface grid grid-cols-3 divide-x divide-border">
        {(
          [
            ['flagged', 'Flagged for all users', 'bg-red-600', 'text-red-700'],
            ['watch', 'On watch, below threshold', 'bg-amber-500', 'text-navy'],
            ['expired', 'Expired, no recent reports', 'bg-slate-400', 'text-navy/60'],
          ] as const
        ).map(([status, label, bar, ink]) => (
          <div key={status} className="relative px-6 py-5">
            <span className={cn('absolute top-5 bottom-5 left-0 w-[3px] rounded-r', bar)} aria-hidden />
            <p className="text-sm font-medium text-muted-foreground">{label}</p>
            <p className={cn('mt-1 font-display text-[40px] leading-none font-semibold tabular-nums', ink)}>{data ? counts[status] : '–'}</p>
          </div>
        ))}
      </section>

      <section className="surface overflow-hidden">
        <Table>
          <TableHeader>
            <TableRow className="bg-navy-soft/60 hover:bg-navy-soft/60">
              {COLUMNS.map((c, i) => (
                <TableHead
                  key={c.label}
                  className={cn('h-10 text-[13px] font-semibold text-navy/75', i === 0 && 'pl-4', c.align === 'right' && 'text-right')}
                  aria-sort={c.key && sort.key === c.key ? (sort.desc ? 'descending' : 'ascending') : undefined}
                >
                  {c.key ? (
                    <button
                      className="inline-flex h-8 items-center gap-1 hover:text-navy"
                      onClick={() => setSort((s) => ({ key: c.key!, desc: s.key === c.key ? !s.desc : true }))}
                    >
                      {c.label}
                      {sort.key === c.key && (sort.desc ? <ArrowDown className="size-3" /> : <ArrowUp className="size-3" />)}
                    </button>
                  ) : (
                    c.label
                  )}
                </TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {!rows &&
              Array.from({ length: 6 }).map((_, i) => (
                <TableRow key={i}>
                  <TableCell colSpan={7} className="px-4">
                    <Skeleton className="h-6 w-full" />
                  </TableCell>
                </TableRow>
              ))}
            {rows?.map((n) => {
              const max = rows.reduce((m, r) => Math.max(m, r.reportCount), 1)
              return (
                <TableRow
                  key={n.callerHash}
                  tabIndex={0}
                  className="cursor-pointer focus-visible:bg-navy-soft focus-visible:outline-none"
                  onClick={() => navigate(`/dashboard?number=${n.callerHash}`)}
                  onKeyDown={(e) => e.key === 'Enter' && navigate(`/dashboard?number=${n.callerHash}`)}
                  title="Show all alerts from this number"
                >
                  <TableCell className="pl-4">
                    <div className="font-mono text-[13px]">{n.callerPrefix}</div>
                    <div className="font-mono text-[11px] text-muted-foreground">#{n.callerHash}</div>
                  </TableCell>
                  <TableCell className="text-muted-foreground">{numberTypeLabel[n.numberType]}</TableCell>
                  <TableCell className="text-right">
                    <div className="flex items-center justify-end gap-2">
                      <div className="h-1.5 w-20 overflow-hidden rounded-sm bg-navy-soft" aria-hidden>
                        <div className="h-full bg-navy" style={{ width: `${(n.reportCount / max) * 100}%` }} />
                      </div>
                      <span className="w-8 font-semibold tabular-nums">{n.reportCount}</span>
                    </div>
                  </TableCell>
                  <TableCell className="text-right tabular-nums">{n.callsPerHour}</TableCell>
                  <TableCell className="text-muted-foreground" title={dateTime(n.firstSeen)}>
                    {shortDate(n.firstSeen)}
                  </TableCell>
                  <TableCell className="text-muted-foreground" title={dateTime(n.lastActivity)}>
                    {timeAgo(n.lastActivity, now)}
                  </TableCell>
                  <TableCell>
                    <NumberStatusBadge status={n.status} />
                  </TableCell>
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      </section>
    </div>
  )
}
