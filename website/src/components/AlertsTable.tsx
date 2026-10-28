import { Check, FileCheck2, X } from 'lucide-react'
import type { KeyboardEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { RiskScore } from '@/components/RiskScore'
import { StatusBadge } from '@/components/StatusBadge'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useNow } from '@/hooks/useNow'
import { caseRef, dateTime, timeAgo } from '@/lib/format'
import type { Alert, AlertStatus } from '@/lib/types'
import { cn } from '@/lib/utils'

interface Props {
  alerts?: Alert[]
  freshIds: Set<string>
  emptyText?: string
  /** Quick triage without opening the case. */
  onTriage?: (alert: Alert, status: AlertStatus) => void
}

/** Three tiny bars, one per engine layer, so the table shows *why* a score is high. */
function LayerBars({ alert }: { alert: Alert }) {
  const layers = [
    ['L1', alert.l1],
    ['L2', alert.l2],
    ['L3', alert.l3],
  ] as const
  return (
    <span
      className="flex h-5 items-end gap-[3px]"
      title={layers.map(([k, v]) => `${k} ${v ?? 'off'}`).join(', ')}
      aria-label={`Layers: ${layers.map(([k, v]) => `${k} ${v ?? 'off'}`).join(', ')}`}
    >
      {layers.map(([k, v]) => (
        <span key={k} className="relative w-[5px] overflow-hidden rounded-[1.5px] bg-navy-soft" style={{ height: '100%' }}>
          {v !== null && <span className="absolute inset-x-0 bottom-0 bg-navy/70" style={{ height: `${Math.max(8, v)}%` }} />}
        </span>
      ))}
    </span>
  )
}

const HEADERS = ['Case', 'Caller', 'Risk', 'Suspected type', 'Region', 'Received', 'Status', '']

export function AlertsTable({ alerts, freshIds, emptyText = 'No alerts match this view.', onTriage }: Props) {
  const navigate = useNavigate()
  const now = useNow()
  const open = (id: string) => navigate(`/dashboard/cases/${id}`)

  // J/K move between rows, Enter opens, R marks reviewed, F marks false positive.
  const onRowKey = (e: KeyboardEvent<HTMLTableRowElement>, a: Alert) => {
    const row = e.currentTarget
    const move = (el: Element | null) => (el as HTMLElement | null)?.focus()
    if (e.target !== row) return
    switch (e.key) {
      case 'Enter':
        open(a.id)
        break
      case 'j':
      case 'ArrowDown':
        e.preventDefault()
        move(row.nextElementSibling)
        break
      case 'k':
      case 'ArrowUp':
        e.preventDefault()
        move(row.previousElementSibling)
        break
      case 'r':
        if (a.status !== 'reviewed') onTriage?.(a, 'reviewed')
        break
      case 'f':
        if (a.status !== 'false_positive') onTriage?.(a, 'false_positive')
        break
    }
  }

  return (
    <Table>
      <TableHeader>
        <TableRow style={{ background: 'var(--eth-teal-soft)' }} className="hover:bg-transparent">
          {HEADERS.map((h, i) => (
            <TableHead
              key={h || 'actions'}
              className={cn('h-10 text-[13px] font-semibold', i === 0 && 'pl-4', i === HEADERS.length - 1 && 'w-24 pr-4')}
              style={{ color: 'var(--eth-teal-deep)' }}
            >
              {h || <span className="sr-only">Quick triage</span>}
            </TableHead>
          ))}
        </TableRow>
      </TableHeader>
      <TableBody>
        {!alerts &&
          Array.from({ length: 6 }).map((_, i) => (
            <TableRow key={i}>
              <TableCell colSpan={HEADERS.length} className="px-4">
                <Skeleton className="h-6 w-full" />
              </TableCell>
            </TableRow>
          ))}
        {alerts?.length === 0 && (
          <TableRow>
            <TableCell colSpan={HEADERS.length} className="px-4 py-10 text-center text-sm text-muted-foreground">
              {emptyText}
            </TableCell>
          </TableRow>
        )}
        {alerts?.map((a) => (
          <TableRow
            key={a.id}
            tabIndex={0}
            onClick={() => open(a.id)}
            onKeyDown={(e) => onRowKey(e, a)}
            className={cn(
              'group cursor-pointer transition-colors duration-100 focus-visible:outline-none',
              'focus-visible:shadow-[inset_3px_0_0_var(--eth-teal)]',
              { 'focus-visible:bg-[var(--eth-teal-soft)]': true },
              a.status === 'new' && 'font-medium',
              freshIds.has(a.id) && 'row-fresh',
            )}
          >
            <TableCell className="pl-4">
              <div className="flex items-center gap-1.5 font-mono text-[12.5px] font-medium" style={{ color: 'var(--eth-teal-deep)' }}>
                {caseRef(a)}
                {a.complaintSubmitted && <FileCheck2 className="size-3.5 text-emerald-700" aria-label="Citizen filed a complaint" />}
              </div>
            </TableCell>
            <TableCell>
              <div className="font-mono text-[13px]">{a.callerPrefix}</div>
              <div className="font-mono text-[11px] font-normal text-muted-foreground">#{a.callerHash}</div>
            </TableCell>
            <TableCell>
              <div className="flex items-center gap-2.5">
                <RiskScore score={a.score} />
                <LayerBars alert={a} />
              </div>
            </TableCell>
            <TableCell>{a.scamType}</TableCell>
            <TableCell className="font-normal text-muted-foreground">{a.region}</TableCell>
            <TableCell className="font-normal text-muted-foreground" title={dateTime(a.createdAt)}>
              {timeAgo(a.createdAt, now)}
            </TableCell>
            <TableCell>
              <StatusBadge status={a.status} />
            </TableCell>
            <TableCell className="pr-4">
              {onTriage && (
                <div className="flex justify-end gap-1 opacity-0 transition-opacity group-hover:opacity-100 group-focus-visible:opacity-100 focus-within:opacity-100">
                  <button
                    type="button"
                    title="Mark reviewed (R)"
                    aria-label={`Mark ${caseRef(a)} reviewed`}
                    disabled={a.status === 'reviewed'}
                    onClick={(e) => {
                      e.stopPropagation()
                      onTriage(a, 'reviewed')
                    }}
                    className="flex size-8 items-center justify-center rounded-lg transition-colors duration-100 hover:text-white disabled:opacity-30"
                    style={{ color: 'var(--eth-teal)' }}
                    onMouseEnter={(e) => (e.currentTarget.style.background = 'var(--eth-teal)')}
                    onMouseLeave={(e) => (e.currentTarget.style.background = '')}
                  >
                    <Check className="size-4" />
                  </button>
                  <button
                    type="button"
                    title="Mark false positive (F)"
                    aria-label={`Mark ${caseRef(a)} as a false positive`}
                    disabled={a.status === 'false_positive'}
                    onClick={(e) => {
                      e.stopPropagation()
                      onTriage(a, 'false_positive')
                    }}
                    className="flex size-8 items-center justify-center rounded-md text-muted-foreground hover:bg-slate-600 hover:text-white disabled:opacity-30"
                  >
                    <X className="size-4" />
                  </button>
                </div>
              )}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}
