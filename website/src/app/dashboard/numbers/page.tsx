'use client'

import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowDown, ArrowUp, ChevronDown, ChevronRight, Info } from 'lucide-react'
import Link from 'next/link'
import { useSearchParams } from 'next/navigation'
import { Fragment, Suspense, useEffect, useMemo, useRef, useState } from 'react'
import { RiskBadge } from '@/components/RiskBadge'
import { Segmented } from '@/components/Segmented'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { useNumbers } from '@/hooks/useData'
import { useNow } from '@/hooks/useNow'
import { dataSource } from '@/lib/data'
import { caseRef, dateTime, deviceHash, formatPhone, shortDate, timeAgo } from '@/lib/format'
import { scamTypeLabel } from '@/lib/labels'
import type { NumberRecord } from '@/lib/types'
import { cn } from '@/lib/utils'

type SortKey = 'reporter_count' | 'report_count' | 'safe_count' | 'max_risk_score' | 'first_seen' | 'last_activity'

const COLUMNS: { key: SortKey | null; label: string; right?: boolean }[] = [
  { key: null, label: 'Number' },
  { key: null, label: 'Circle' },
  { key: 'report_count', label: 'Reports', right: true },
  { key: 'reporter_count', label: 'Reporters', right: true },
  { key: 'safe_count', label: 'Marked safe by', right: true },
  { key: 'max_risk_score', label: 'Max risk' },
  { key: 'first_seen', label: 'First seen' },
  { key: 'last_activity', label: 'Last activity' },
  { key: null, label: 'Status' },
]

const FLAG_HELP = 'Flagged only when 3+ independent devices report it (prevents false reporting), and more devices reported it than marked it safe.'

function NumberReports({ number }: { number: string }) {
  const { data } = useQuery({ queryKey: ['reports', 'number', number], queryFn: () => dataSource.getReportsByNumber(number) })
  if (!data) return <Skeleton className="h-16 w-full" />
  return (
    <ul className="divide-y divide-border/70">
      {data.map((r) => (
        <li key={r.id}>
          <Link href={`/dashboard/reports/${r.id}`} className="flex items-center gap-4 rounded-md px-2 py-2 text-sm hover:bg-navy-soft">
            <span className="w-40 font-mono text-[12.5px] text-navy">{caseRef(r)}</span>
            <RiskBadge score={r.risk_score} />
            <span className="flex-1">{scamTypeLabel[r.scam_type]}</span>
            <span className="font-mono text-xs text-muted-foreground">{deviceHash(r.device_id)}</span>
            {r.user_feedback === 'safe' && <span className="rounded-sm bg-emerald-50 px-1.5 text-[11px] font-medium text-emerald-800">Marked safe</span>}
            <span className="w-36 text-right text-muted-foreground">{dateTime(r.created_at)}</span>
          </Link>
        </li>
      ))}
    </ul>
  )
}

function NumbersView() {
  const params = useSearchParams()
  const focus = params.get('number')
  const queryClient = useQueryClient()
  const now = useNow()
  const { data } = useNumbers()
  const [flaggedOnly, setFlaggedOnly] = useState<'all' | 'flagged'>('all')
  const [sort, setSort] = useState<{ key: SortKey; desc: boolean }>({ key: 'reporter_count', desc: true })
  const [expanded, setExpanded] = useState<Set<string>>(() => new Set(focus ? [focus] : []))
  const [flash, setFlash] = useState<Set<string>>(new Set())
  const timers = useRef<ReturnType<typeof setTimeout>[]>([])

  // Live: a numbers row changes (new report / safe feedback) → patch it in place and flash it.
  useEffect(() => {
    const pending = timers.current
    const off = dataSource.subscribeToNumbers((n) => {
      queryClient.setQueryData<NumberRecord[]>(['numbers'], (rows) => {
        if (!rows) return rows
        return rows.some((r) => r.number === n.number) ? rows.map((r) => (r.number === n.number ? n : r)) : [n, ...rows]
      })
      setFlash((s) => new Set(s).add(n.number))
      pending.push(
        setTimeout(
          () =>
            setFlash((s) => {
              const next = new Set(s)
              next.delete(n.number)
              return next
            }),
          3000,
        ),
      )
    })
    return () => {
      off()
      pending.forEach(clearTimeout)
    }
  }, [queryClient])

  const rows = useMemo(() => {
    if (!data) return undefined
    const value = (n: NumberRecord) => (sort.key === 'first_seen' || sort.key === 'last_activity' ? new Date(n[sort.key]).getTime() : n[sort.key])
    return data
      .filter((n) => flaggedOnly === 'all' || n.is_flagged)
      .sort((a, b) => (sort.desc ? value(b) - value(a) : value(a) - value(b)) || b.report_count - a.report_count)
  }, [data, sort, flaggedOnly])

  const flaggedCount = data?.filter((n) => n.is_flagged).length ?? 0

  const toggle = (n: string) =>
    setExpanded((s) => {
      const next = new Set(s)
      if (next.has(n)) next.delete(n)
      else next.add(n)
      return next
    })

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="flex items-center gap-2 font-display text-[32px] leading-tight font-semibold tracking-tight text-navy-deep">
            Numbers
            <Tooltip>
              <TooltipTrigger className="text-muted-foreground hover:text-navy" aria-label="How flagging works">
                <Info className="size-5" />
              </TooltipTrigger>
              <TooltipContent className="max-w-xs">{FLAG_HELP}</TooltipContent>
            </Tooltip>
          </h1>
          <p className="text-[15px] text-muted-foreground">
            Every reported caller number. {data ? `${flaggedCount} flagged of ${data.length}.` : ''} Flagged only when 3+ independent devices
            report it.
          </p>
        </div>
        <Segmented
          label="Show"
          value={flaggedOnly}
          onChange={setFlaggedOnly}
          options={[
            { value: 'all', label: 'All numbers' },
            { value: 'flagged', label: 'Flagged only' },
          ]}
        />
      </div>

      <section className="surface overflow-hidden">
        <Table>
          <TableHeader>
            <TableRow style={{ background: 'var(--eth-teal-soft)' }} className="hover:bg-transparent">
              <TableHead className="w-8" />
              {COLUMNS.map((c) => (
                <TableHead key={c.label} className={cn('h-10 text-[13px] font-semibold', c.right && 'text-right')} style={{ color: 'var(--eth-teal-deep)' }}>
                  {c.key ? (
                    <button
                      type="button"
                      className={cn('inline-flex items-center gap-1 hover:text-navy', c.right && 'flex-row-reverse')}
                      onClick={() => setSort((s) => ({ key: c.key!, desc: s.key === c.key ? !s.desc : true }))}
                      aria-sort={sort.key === c.key ? (sort.desc ? 'descending' : 'ascending') : undefined}
                    >
                      {c.label}
                      {sort.key === c.key && (sort.desc ? <ArrowDown className="size-3.5" /> : <ArrowUp className="size-3.5" />)}
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
                  <TableCell colSpan={COLUMNS.length + 1}>
                    <Skeleton className="h-6 w-full" />
                  </TableCell>
                </TableRow>
              ))}
            {rows?.length === 0 && (
              <TableRow>
                <TableCell colSpan={COLUMNS.length + 1} className="py-10 text-center text-sm text-muted-foreground">
                  {flaggedOnly === 'flagged' ? 'No number has been reported by 3+ devices yet.' : 'No numbers reported yet.'}
                </TableCell>
              </TableRow>
            )}
            {rows?.map((n) => {
              const open = expanded.has(n.number)
              return (
                <Fragment key={n.number}>
                  <TableRow
                    onClick={() => toggle(n.number)}
                    className={cn('cursor-pointer', n.is_flagged && 'bg-red-50/60', flash.has(n.number) && 'row-flash', focus === n.number && 'shadow-[inset_3px_0_0_var(--eth-teal)]')}
                    aria-expanded={open}
                  >
                    <TableCell className="pl-3">
                      {open ? <ChevronDown className="size-4 text-muted-foreground" /> : <ChevronRight className="size-4 text-muted-foreground" />}
                    </TableCell>
                    <TableCell className="font-mono text-[13px] font-medium whitespace-nowrap">{formatPhone(n.number)}</TableCell>
                    <TableCell className="text-[13px] text-muted-foreground">{n.telecom_circle ?? '—'}</TableCell>
                    <TableCell className="text-right tabular-nums">{n.report_count}</TableCell>
                    <TableCell className="text-right font-display text-base font-semibold tabular-nums">{n.reporter_count}</TableCell>
                    <TableCell className={cn('text-right tabular-nums', n.safe_count ? 'text-emerald-800' : 'text-muted-foreground')}>{n.safe_count}</TableCell>
                    <TableCell>
                      <RiskBadge score={n.max_risk_score} />
                    </TableCell>
                    <TableCell className="text-[13px] text-muted-foreground" title={dateTime(n.first_seen)}>
                      {shortDate(n.first_seen)}
                    </TableCell>
                    <TableCell className="text-[13px] text-muted-foreground" title={dateTime(n.last_activity)}>
                      {timeAgo(n.last_activity, now)}
                    </TableCell>
                    <TableCell>
                      {n.is_flagged ? (
                        <span className="rounded-sm bg-red-700 px-2 py-0.5 text-xs font-semibold text-white" title={FLAG_HELP}>
                          Flagged
                        </span>
                      ) : (
                        <span className="text-xs text-muted-foreground">{n.reporter_count < 3 ? `${3 - n.reporter_count} more to flag` : 'Outvoted by safe'}</span>
                      )}
                    </TableCell>
                  </TableRow>
                  {open && (
                    <TableRow className="hover:bg-transparent">
                      <TableCell />
                      <TableCell colSpan={COLUMNS.length} className="pb-3">
                        <NumberReports number={n.number} />
                      </TableCell>
                    </TableRow>
                  )}
                </Fragment>
              )
            })}
          </TableBody>
        </Table>
      </section>
    </div>
  )
}

export default function NumbersPage() {
  return (
    <Suspense fallback={<Skeleton className="h-96 w-full" />}>
      <NumbersView />
    </Suspense>
  )
}
