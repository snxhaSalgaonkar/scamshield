'use client'

import { Check, X } from 'lucide-react'
import { useRouter } from 'next/navigation'
import type { KeyboardEvent } from 'react'
import { DetectedViaIcons } from '@/components/DetectedVia'
import { RiskBadge } from '@/components/RiskBadge'
import { StatusBadge } from '@/components/StatusBadge'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useNow } from '@/hooks/useNow'
import { clockTime, dateTime, formatPhone, maskPhone, timeAgo } from '@/lib/format'
import { scamTypeLabel } from '@/lib/labels'
import { actionLabel } from '@/lib/risk'
import type { Report, ReportStatus } from '@/lib/types'
import { cn } from '@/lib/utils'

interface Props {
  reports?: Report[]
  freshIds?: Set<string>
  emptyText?: string
  /** Hide the middle digits of caller numbers (for screen sharing). */
  mask?: boolean
  /** Quick triage without opening the report. */
  onTriage?: (report: Report, status: ReportStatus) => void
  /** Show the telecom circle column (reports list). */
  showCircle?: boolean
}

/** Three tiny bars, one per layer, so the table shows *why* a score is high. */
function LayerBars({ r }: { r: Report }) {
  const layers = [
    ['L1', r.l1_score],
    ['L2', r.l2_score],
    ['L3', r.l3_score],
  ] as const
  const label = layers.map(([k, v]) => `${k} ${v}`).join(', ')
  return (
    <span className="flex h-5 items-end gap-[3px]" title={label} aria-label={`Layers: ${label}`}>
      {layers.map(([k, v]) => (
        <span key={k} className="relative h-full w-[5px] overflow-hidden rounded-[1.5px] bg-navy-soft">
          <span className="absolute inset-x-0 bottom-0 bg-navy/70" style={{ height: `${Math.max(6, v)}%` }} />
        </span>
      ))}
    </span>
  )
}

const actionTone: Record<Report['action_taken'], string> = {
  none: 'text-muted-foreground',
  warn: 'text-amber-800',
  countdown_cancelled: 'text-amber-800',
  disconnected: 'font-semibold text-red-800',
}

export function ReportsTable({ reports, freshIds, emptyText = 'No reports match this view.', mask = false, onTriage, showCircle = false }: Props) {
  const router = useRouter()
  const now = useNow()
  const open = (id: string) => router.push(`/dashboard/reports/${id}`)
  const headers = ['Time', 'Caller', 'Risk', 'Scam type', 'Detected via', 'Action', ...(showCircle ? ['Circle'] : []), 'Status', '']

  // J/K move between rows, Enter opens, R marks reviewed, F marks false positive.
  const onRowKey = (e: KeyboardEvent<HTMLTableRowElement>, r: Report) => {
    const row = e.currentTarget
    if (e.target !== row) return
    const move = (el: Element | null) => (el as HTMLElement | null)?.focus()
    switch (e.key) {
      case 'Enter':
        open(r.id)
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
        if (r.status !== 'reviewed') onTriage?.(r, 'reviewed')
        break
      case 'f':
        if (r.status !== 'false_positive') onTriage?.(r, 'false_positive')
        break
    }
  }

  return (
    <Table>
      <TableHeader>
        <TableRow style={{ background: 'var(--eth-teal-soft)' }} className="hover:bg-transparent">
          {headers.map((h, i) => (
            <TableHead
              key={h || 'actions'}
              className={cn('h-10 text-[13px] font-semibold', i === 0 && 'pl-4', i === headers.length - 1 && 'w-24 pr-4')}
              style={{ color: 'var(--eth-teal-deep)' }}
            >
              {h || <span className="sr-only">Quick triage</span>}
            </TableHead>
          ))}
        </TableRow>
      </TableHeader>
      <TableBody>
        {!reports &&
          Array.from({ length: 6 }).map((_, i) => (
            <TableRow key={i}>
              <TableCell colSpan={headers.length} className="px-4">
                <Skeleton className="h-6 w-full" />
              </TableCell>
            </TableRow>
          ))}
        {reports?.length === 0 && (
          <TableRow>
            <TableCell colSpan={headers.length} className="px-4 py-10 text-center text-sm text-muted-foreground">
              {emptyText}
            </TableCell>
          </TableRow>
        )}
        {reports?.map((r) => (
          <TableRow
            key={r.id}
            tabIndex={0}
            onClick={() => open(r.id)}
            onKeyDown={(e) => onRowKey(e, r)}
            className={cn(
              'group h-12 cursor-pointer transition-colors duration-100 focus-visible:bg-[var(--eth-teal-soft)] focus-visible:shadow-[inset_3px_0_0_var(--eth-teal)] focus-visible:outline-none',
              r.status === 'new' && 'font-medium',
              freshIds?.has(r.id) && 'row-fresh',
            )}
          >
            <TableCell className="pl-4 whitespace-nowrap" title={dateTime(r.created_at)}>
              <div className="text-[13px] tabular-nums">{clockTime(r.created_at)}</div>
              <div className="text-[11px] font-normal text-muted-foreground">{timeAgo(r.created_at, now)}</div>
            </TableCell>
            <TableCell className="font-mono text-[13px] whitespace-nowrap">{mask ? maskPhone(r.caller_number) : formatPhone(r.caller_number)}</TableCell>
            <TableCell>
              <div className="flex items-center gap-2.5">
                <RiskBadge score={r.risk_score} />
                <LayerBars r={r} />
              </div>
            </TableCell>
            <TableCell className="whitespace-nowrap">{scamTypeLabel[r.scam_type]}</TableCell>
            <TableCell>
              <DetectedViaIcons via={r.detected_via} />
            </TableCell>
            <TableCell className={cn('text-[13px] whitespace-nowrap', actionTone[r.action_taken])}>{actionLabel[r.action_taken]}</TableCell>
            {showCircle && <TableCell className="text-[13px] font-normal whitespace-nowrap text-muted-foreground">{r.telecom_circle ?? '—'}</TableCell>}
            <TableCell>
              <div className="flex items-center gap-1.5">
                <StatusBadge status={r.status} />
                {r.user_feedback === 'safe' && (
                  <span className="rounded-sm bg-emerald-50 px-1.5 py-0.5 text-[11px] font-medium text-emerald-800" title="Citizen marked this call safe">
                    Safe
                  </span>
                )}
              </div>
            </TableCell>
            <TableCell className="pr-4">
              {onTriage && (
                <div className="flex justify-end gap-1 opacity-0 transition-opacity group-hover:opacity-100 group-focus-visible:opacity-100 focus-within:opacity-100">
                  <button
                    type="button"
                    title="Mark reviewed (R)"
                    aria-label="Mark reviewed"
                    disabled={r.status === 'reviewed'}
                    onClick={(e) => {
                      e.stopPropagation()
                      onTriage(r, 'reviewed')
                    }}
                    className="flex size-8 items-center justify-center rounded-lg text-navy transition-colors duration-100 hover:bg-navy hover:text-white disabled:opacity-30"
                  >
                    <Check className="size-4" />
                  </button>
                  <button
                    type="button"
                    title="Mark false positive (F)"
                    aria-label="Mark false positive"
                    disabled={r.status === 'false_positive'}
                    onClick={(e) => {
                      e.stopPropagation()
                      onTriage(r, 'false_positive')
                    }}
                    className="flex size-8 items-center justify-center rounded-lg text-muted-foreground hover:bg-slate-600 hover:text-white disabled:opacity-30"
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
