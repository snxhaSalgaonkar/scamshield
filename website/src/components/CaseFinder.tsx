import { useQuery } from '@tanstack/react-query'
import { BarChart3, FileText, LayoutList, MonitorPlay, PhoneOff, Search } from 'lucide-react'
import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { RiskScore } from '@/components/RiskScore'
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog'
import { dataSource } from '@/lib/data'
import { caseRef, timeAgo } from '@/lib/format'
import { numberStatusLabel } from '@/lib/risk'
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
  { id: 'p-feed', icon: <LayoutList className="size-4" />, title: 'Live feed', detail: 'All incoming alerts', to: '/dashboard' },
  { id: 'p-numbers', icon: <PhoneOff className="size-4" />, title: 'Flagged numbers', detail: 'Reputation register', to: '/dashboard/numbers' },
  { id: 'p-trends', icon: <BarChart3 className="size-4" />, title: 'Hotspots and trends', detail: 'Regions, hours, score bands', to: '/dashboard/trends' },
  { id: 'p-wall', icon: <MonitorPlay className="size-4" />, title: 'Command wall', detail: 'Full-screen projector view', to: '/command' },
]

const norm = (s: string) => s.toLowerCase().replace(/[\s/]+/g, '')

/** Ctrl+K finder: jump to a case by reference, number, region or type. */
export function CaseFinder({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const navigate = useNavigate()
  const [query, setQuery] = useState('')
  const [active, setActive] = useState(0)
  const listRef = useRef<HTMLUListElement>(null)

  const alerts = useQuery({ queryKey: ['alerts', null], queryFn: () => dataSource.getAlerts({}), enabled: open })
  const numbers = useQuery({ queryKey: ['numbers'], queryFn: () => dataSource.getFlaggedNumbers(), enabled: open })

  const results = useMemo<Result[]>(() => {
    const q = norm(query)
    if (!q) return PAGES
    const numberHits: Result[] = (numbers.data ?? [])
      .filter((n) => norm(n.callerPrefix).includes(q) || n.callerHash.includes(q))
      .slice(0, 3)
      .map((n) => ({
        id: `n-${n.callerHash}`,
        icon: <PhoneOff className="size-4" />,
        title: <span className="font-mono">{n.callerPrefix}</span>,
        detail: `${n.reportCount} reports, ${numberStatusLabel[n.status].toLowerCase()}`,
        to: `/dashboard?number=${n.callerHash}`,
      }))
    const caseHits: Result[] = (alerts.data ?? [])
      .filter((a) =>
        [caseRef(a), a.callerPrefix, a.callerHash, a.region, a.scamType, a.id].some((field) => norm(field).includes(q)),
      )
      .slice(0, 7)
      .map((a) => ({
        id: a.id,
        icon: <FileText className="size-4" />,
        title: (
          <>
            <span className="font-mono text-[13px]">{caseRef(a)}</span> <span className="text-muted-foreground">·</span> {a.scamType}
          </>
        ),
        detail: `${a.region}, ${a.callerPrefix}, ${timeAgo(a.createdAt)}`,
        aside: <RiskScore score={a.score} />,
        to: `/dashboard/cases/${a.id}`,
      }))
    return [...caseHits, ...numberHits]
  }, [query, alerts.data, numbers.data])

  useEffect(() => {
    listRef.current?.querySelector('[aria-selected=true]')?.scrollIntoView({ block: 'nearest' })
  }, [active])

  const go = (r: Result | undefined) => {
    if (!r) return
    onOpenChange(false)
    setQuery('')
    navigate(r.to)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="top-[18%] translate-y-0 gap-0 overflow-hidden p-0 sm:max-w-xl" showCloseButton={false}>
        <DialogTitle className="sr-only">Find a case</DialogTitle>
        <div className="flex items-center gap-3 border-b border-border px-4">
          <Search className="size-4 text-muted-foreground" aria-hidden />
          <input
            autoFocus
            role="combobox"
            aria-expanded
            aria-controls="finder-results"
            aria-activedescendant={results[active] ? `finder-${results[active].id}` : undefined}
            placeholder="Case reference, number, region or scam type"
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
            <li className="px-3 py-8 text-center text-sm text-muted-foreground">
              Nothing matches “{query}”. Try a case number like 000126, a city, or the last digits of a caller prefix.
            </li>
          )}
          {results.map((r, i) => (
            <li
              key={r.id}
              id={`finder-${r.id}`}
              role="option"
              aria-selected={i === active}
              onMouseMove={() => setActive(i)}
              onClick={() => go(r)}
              className={cn(
                'flex cursor-pointer items-center gap-3 rounded-md px-3 py-2.5',
                i === active ? 'bg-navy text-white' : 'text-foreground',
              )}
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

        <div className="flex gap-4 border-t border-border bg-muted/50 px-4 py-2 text-xs text-muted-foreground">
          <span>
            <kbd className="font-mono">↑ ↓</kbd> to move
          </span>
          <span>
            <kbd className="font-mono">Enter</kbd> to open
          </span>
        </div>
      </DialogContent>
    </Dialog>
  )
}
