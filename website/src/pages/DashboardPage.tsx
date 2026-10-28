import { useQueryClient } from '@tanstack/react-query'
import { Pause, Play, PhoneIncoming, Search, X } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { AlertsTable } from '@/components/AlertsTable'
import { OverTimeChart, ScamTypeChart } from '@/components/Charts'
import { FilterSelect } from '@/components/FilterSelect'
import { StatsBanner } from '@/components/StatsBanner'
import { Button } from '@/components/ui/button'
import { useAlertFeed } from '@/hooks/useAlertFeed'
import { useNow } from '@/hooks/useNow'
import { useStats } from '@/hooks/useStats'
import { dataSource, isMock } from '@/lib/data'
import { caseRef, timeAgo } from '@/lib/format'
import { riskLabel, riskLevel, statusLabel } from '@/lib/risk'
import type { Alert, AlertStatus, RiskLevel, ScamType } from '@/lib/types'
import { cn } from '@/lib/utils'

const PAGE = 25

type RiskFilter = 'all' | RiskLevel
type StatusFilter = 'all' | AlertStatus
type TypeFilter = 'all' | ScamType

const RISK_TABS: RiskFilter[] = ['all', 'red', 'warning', 'silent']
const STATUS_ITEMS: { value: StatusFilter; label: string }[] = [
  { value: 'all', label: 'All' },
  ...(Object.keys(statusLabel) as AlertStatus[]).map((s) => ({ value: s, label: statusLabel[s] })),
]
const TYPE_ITEMS: { value: TypeFilter; label: string }[] = [
  { value: 'all', label: 'All' },
  ...(['Digital Arrest', 'OTP Theft', 'KYC Scam', 'False Positive'] as ScamType[]).map((t) => ({ value: t, label: t })),
]

export function DashboardPage() {
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const callerHash = params.get('number') ?? undefined
  const [risk, setRisk] = useState<RiskFilter>('all')
  const [status, setStatus] = useState<StatusFilter>('all')
  const [type, setType] = useState<TypeFilter>('all')
  const [limit, setLimit] = useState(PAGE)
  const now = useNow(10_000)
  const { data: stats } = useStats()

  const onAlert = (a: Alert) => {
    if (riskLevel(a.score) !== 'red') return
    toast.error(`Red flag · ${a.scamType}`, {
      description: `${a.callerPrefix} · ${a.region} · score ${a.score}`,
      action: { label: 'Open case', onClick: () => navigate(`/dashboard/cases/${a.id}`) },
      duration: 8000,
    })
  }

  const { alerts, freshIds, paused, pause, resume, pending, lastAlertAt } = useAlertFeed({ callerHash, onAlert })
  const [search, setSearch] = useState('')
  const queryClient = useQueryClient()

  const triage = async (a: Alert, next: AlertStatus) => {
    const prev = a.status
    const patch = (s: AlertStatus) =>
      queryClient.setQueryData<Alert[]>(['alerts', callerHash ?? null], (rows) => rows?.map((r) => (r.id === a.id ? { ...r, status: s } : r)))
    patch(next)
    try {
      await dataSource.updateAlertStatus(a.id, next)
      queryClient.invalidateQueries({ queryKey: ['stats'] })
      queryClient.removeQueries({ queryKey: ['alert', a.id] })
      toast.success(`${caseRef(a)} marked ${statusLabel[next].toLowerCase()}`, {
        action: { label: 'Undo', onClick: () => triage({ ...a, status: next }, prev) },
      })
    } catch {
      patch(prev)
      toast.error(`Could not update ${caseRef(a)}. Try again.`)
    }
  }

  // Unreviewed count in the tab title, so it shows on a background tab or a wall screen.
  useEffect(() => {
    const n = stats?.awaitingReview ?? 0
    document.title = n ? `(${n}) RakshaCall · Live feed` : 'RakshaCall · Live feed'
    return () => {
      document.title = 'RakshaCall Authority Console'
    }
  }, [stats?.awaitingReview])

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase().replace(/\s+/g, '')
    return alerts?.filter(
      (a) =>
        (risk === 'all' || riskLevel(a.score) === risk) &&
        (status === 'all' || a.status === status) &&
        (type === 'all' || a.scamType === type) &&
        (!q ||
          [caseRef(a), a.callerPrefix, a.callerHash, a.region].some((f) => f.toLowerCase().replace(/\s+/g, '').includes(q))),
    )
  }, [alerts, risk, status, type, search])

  const riskCounts = useMemo(() => {
    const c: Record<RiskFilter, number> = { all: 0, red: 0, warning: 0, silent: 0 }
    alerts?.forEach((a) => {
      c.all += 1
      c[riskLevel(a.score)] += 1
    })
    return c
  }, [alerts])

  const filtersActive = risk !== 'all' || status !== 'all' || type !== 'all' || search.trim() !== ''

  return (
    <div className="space-y-6">
      <div className="flex items-end justify-between">
        <div>
          <h1 className="font-display text-[32px] leading-tight font-semibold tracking-tight" style={{ color: 'var(--eth-teal-deep)' }}>Live alert feed</h1>
          <p className="text-[15px] text-muted-foreground">
            Fraud-call alerts raised by citizens&apos; phones, newest first. Only events and scores reach this console.
          </p>
        </div>
        {isMock && dataSource.simulateAlert && (
          <Button
            variant="outline"
            className="border-brass bg-brass-soft text-amber-950 hover:bg-brass/30"
            onClick={() => dataSource.simulateAlert?.()}
          >
            <PhoneIncoming className="size-4" aria-hidden />
            Simulate scam call
          </Button>
        )}
      </div>

      <StatsBanner />

      <div className="grid grid-cols-2 gap-4">
        <OverTimeChart />
        <ScamTypeChart />
      </div>

      <section className="surface overflow-hidden">
        {/* Feed header: live state + filters */}
        <div className="flex flex-wrap items-center gap-3 border-b border-border px-4 py-3">
          <div className="flex items-center gap-2.5">
            <span className="live-dot" data-paused={paused} aria-hidden />
            <h2 className="font-display text-lg font-semibold" style={{ color: 'var(--eth-teal-deep)' }}>{paused ? 'Feed paused' : 'Receiving live'}</h2>
            <span className="text-xs text-muted-foreground">
              {lastAlertAt ? `Last alert ${timeAgo(lastAlertAt, now)}` : 'Waiting for new alerts'}
            </span>
          </div>

          <div className="ml-auto flex flex-wrap items-center gap-2">
            <label className="relative">
              <span className="sr-only">Search the feed</span>
              <Search className="pointer-events-none absolute top-1/2 left-2.5 size-3.5 -translate-y-1/2 text-muted-foreground" aria-hidden />
              <input
                value={search}
                onChange={(e) => {
                  setSearch(e.target.value)
                  setLimit(PAGE)
                }}
                placeholder="Case, number or city"
                className="h-8 w-48 rounded-lg border border-input pr-2 pl-8 text-sm outline-none transition-shadow focus-visible:ring-2"
                style={{ background: 'var(--neu-base)', boxShadow: 'inset 2px 2px 5px var(--neu-inset-dark), inset -2px -2px 5px var(--neu-inset-light)', outline: 'none' }}
              />
            </label>
            <div role="tablist" aria-label="Risk level" className="flex rounded-md border border-border p-0.5">
              {RISK_TABS.map((r) => (
                <button
                  key={r}
                  role="tab"
                  aria-selected={risk === r}
                  onClick={() => {
                    setRisk(r)
                    setLimit(PAGE)
                  }}
                  className={cn(
                    'rounded-lg px-2.5 py-1.5 text-xs font-medium transition-all duration-120',
                    risk === r
                      ? 'text-white'
                      : 'text-muted-foreground hover:text-foreground',
                  )}
                  style={risk === r ? { background: 'var(--eth-teal)', boxShadow: 'inset 1px 1px 3px rgb(0 0 0 / 0.15)' } : {}}
                >
                  {r === 'all' ? 'All' : riskLabel[r]} <span className="tabular-nums opacity-70">{riskCounts[r]}</span>
                </button>
              ))}
            </div>
            <FilterSelect label="Status" value={status} items={STATUS_ITEMS} onChange={setStatus} />
            <FilterSelect label="Type" value={type} items={TYPE_ITEMS} onChange={setType} />
            {callerHash && (
              <Button variant="outline" size="sm" onClick={() => setParams({})}>
                Number #{callerHash}
                <X className="size-3.5" aria-hidden />
                <span className="sr-only">Clear number filter</span>
              </Button>
            )}
            <Button variant="outline" size="sm" onClick={paused ? resume : pause}>
              {paused ? <Play className="size-3.5" aria-hidden /> : <Pause className="size-3.5" aria-hidden />}
              {paused ? 'Resume' : 'Pause'}
            </Button>
          </div>
        </div>

        {pending.length > 0 && (
          <button
            onClick={resume}
            className="w-full border-b border-brass/50 bg-brass-soft py-2 text-sm font-medium text-amber-950 hover:bg-brass/25"
          >
            {pending.length} new alert{pending.length > 1 ? 's' : ''} held while paused · show now
          </button>
        )}

        <AlertsTable
          alerts={filtered?.slice(0, limit)}
          freshIds={freshIds}
          onTriage={triage}
          emptyText={filtersActive ? 'No alerts match these filters. Clear the search or set filters back to All.' : 'No alerts yet. New ones appear here as phones report them.'}
        />
        <p className="border-t border-border px-4 py-2 text-xs text-muted-foreground">
          Keyboard: <kbd className="font-mono">J</kbd>/<kbd className="font-mono">K</kbd> move between rows,{' '}
          <kbd className="font-mono">Enter</kbd> opens, <kbd className="font-mono">R</kbd> marks reviewed,{' '}
          <kbd className="font-mono">F</kbd> marks a false positive. <kbd className="font-mono">Ctrl K</kbd> finds any case.
        </p>

        {filtered && filtered.length > limit && (
          <div className="flex items-center justify-between border-t border-border px-4 py-2.5 text-xs text-muted-foreground">
            <span>
              Showing {limit} of {filtered.length}
            </span>
            <Button variant="ghost" size="sm" onClick={() => setLimit((l) => l + PAGE)}>
              Show {Math.min(PAGE, filtered.length - limit)} more
            </Button>
          </div>
        )}
      </section>

      <p className="sr-only" aria-live="polite">
        {filtered?.[0] && freshIds.has(filtered[0].id) ? `New alert ${caseRef(filtered[0])}, ${filtered[0].scamType}` : ''}
      </p>
    </div>
  )
}
