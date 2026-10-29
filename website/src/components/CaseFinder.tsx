'use client'

import { FileText, LayoutDashboard, PhoneOff, Rows3, Search } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { RiskBadge } from '@/components/RiskBadge'
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog'
import { useWindowReports } from '@/hooks/realtime'
import { useNumbers } from '@/hooks/useData'
import { caseRef, formatPhone, timeAgo } from '@/lib/format'
import { scamTypeLabel } from '@/lib/labels'
import { cn } from '@/lib/utils'

interface Result {
  id: string
  icon: ReactNode
  title: ReactNode
  detail: ReactNode
  aside?: ReactNode
  to: string
}

const PAGES: Result[] = [
  { id: 'p-overview', icon: <LayoutDashboard className="size-4" />, title: 'Overview', detail: 'Live feed and summary', to: '/dashboard' },
  { id: 'p-reports', icon: <Rows3 className="size-4" />, title: 'Reports', detail: 'Filter, page and export', to: '/dashboard/reports' },
  { id: 'p-numbers', icon: <PhoneOff className="size-4" />, title: 'Numbers', detail: 'Reported and flagged numbers', to: '/dashboard/numbers' },
]

const norm = (s: string) => s.toLowerCase().replace(/[\s/+-]+/g, '')

/** Ctrl+K finder: jump to a report by reference, number, circle or scam type. */
export function CaseFinder({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const router = useRouter()
  const [query, setQuery] = useState('')
  const [active, setActive] = useState(0)
  const listRef = useRef<HTMLUListElement>(null)
  const { data: reports } = useWindowReports()
  const { data: numbers } = useNumbers()

  const results = useMemo<Result[]>(() => {
    const q = norm(query)
    if (!q) return PAGES
    const numberHits: Result[] = (numbers ?? [])
      .filter((n) => norm(n.number).includes(q))
      .slice(0, 3)
      .map((n) => ({
        id: `n-${n.number}`,
        icon: <PhoneOff className="size-4" />,
        title: <span className="font-mono">{formatPhone(n.number)}</span>,
        detail: `${n.report_count} reports from ${n.reporter_count} devices${n.is_flagged ? ', flagged' : ''}`,
        to: `/dashboard/numbers?number=${encodeURIComponent(n.number)}`,
      }))
    const reportHits: Result[] = (reports ?? [])
      .filter((r) => [caseRef(r), r.caller_number, r.telecom_circle ?? '', scamTypeLabel[r.scam_type], r.id].some((f) => norm(f).includes(q)))
      .slice(0, 7)
      .map((r) => ({
        id: r.id,
        icon: <FileText className="size-4" />,
        title: (
          <>
            <span className="font-mono text-[13px]">{caseRef(r)}</span> <span className="opacity-60">·</span> {scamTypeLabel[r.scam_type]}
          </>
        ),
        detail: `${formatPhone(r.caller_number)}${r.telecom_circle ? `, ${r.telecom_circle}` : ''}, ${timeAgo(r.created_at)}`,
        aside: <RiskBadge score={r.risk_score} />,
        to: `/dashboard/reports/${r.id}`,
      }))
    return [...reportHits, ...numberHits]
  }, [query, reports, numbers])

  useEffect(() => {
    listRef.current?.querySelector('[aria-selected=true]')?.scrollIntoView({ block: 'nearest' })
  }, [active])

  const go = (r: Result | undefined) => {
    if (!r) return
    onOpenChange(false)
    setQuery('')
    router.push(r.to)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="top-[18%] translate-y-0 gap-0 overflow-hidden p-0 sm:max-w-xl" showCloseButton={false}>
        <DialogTitle className="sr-only">Find a report</DialogTitle>
        <div className="flex items-center gap-3 border-b border-border px-4">
          <Search className="size-4 text-muted-foreground" aria-hidden />
          <input
            autoFocus
            role="combobox"
            aria-expanded
            aria-controls="finder-results"
            aria-activedescendant={results[active] ? `finder-${results[active].id}` : undefined}
            placeholder="Case reference, number, circle or scam type"
            value={query}
            onChange={(e) => {
              setQuery(e.target.value)
              setActive(0)
            }}
            onKeyDown={(e) => {
              if (e.key === 'ArrowDown') {
                e.preventDefault()
                setActive((i) => Math.min(results.length - 1, i + 1))
              } else if (e.key === 'ArrowUp') {
                e.preventDefault()
                setActive((i) => Math.max(0, i - 1))
              } else if (e.key === 'Enter') {
                e.preventDefault()
                go(results[active])
              }
            }}
            className="h-14 flex-1 bg-transparent text-[15px] outline-none placeholder:text-muted-foreground"
          />
          <kbd className="rounded border border-border px-1.5 py-0.5 font-mono text-[11px] text-muted-foreground">Esc</kbd>
        </div>

        <ul id="finder-results" role="listbox" ref={listRef} className="max-h-[50vh] overflow-y-auto p-2">
          {!query && <li className="px-3 pt-1 pb-2 text-xs text-muted-foreground">Go to</li>}
          {results.length === 0 && (
            <li className="px-3 py-8 text-center text-sm text-muted-foreground">Nothing in the last 7 days matches “{query}”.</li>
          )}
          {results.map((r, i) => (
            <li
              key={r.id}
              id={`finder-${r.id}`}
              role="option"
              aria-selected={i === active}
              onMouseMove={() => setActive(i)}
              onClick={() => go(r)}
              className={cn('flex cursor-pointer items-center gap-3 rounded-md px-3 py-2.5', i === active ? 'bg-navy text-white' : 'text-foreground')}
            >
              <span className={cn('shrink-0', i === active ? 'text-brass' : 'text-navy/60')}>{r.icon}</span>
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm font-medium">{r.title}</span>
                <span className={cn('block truncate text-xs', i === active ? 'text-white/65' : 'text-muted-foreground')}>{r.detail}</span>
              </span>
              {r.aside && <span className="shrink-0">{r.aside}</span>}
            </li>
          ))}
        </ul>
      </DialogContent>
    </Dialog>
  )
}
