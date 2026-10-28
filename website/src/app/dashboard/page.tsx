'use client'

import { ArrowRight, EyeOff, Eye, TrendingUp } from 'lucide-react'
import Link from 'next/link'
import { useEffect, useMemo, useState } from 'react'
import { AnimatedNumber } from '@/components/AnimatedNumber'
import { DetectedViaChart, OverTimeChart, ScamTypeChart } from '@/components/Charts'
import { ReportsTable } from '@/components/ReportsTable'
import { Segmented } from '@/components/Segmented'
import { Sparkline } from '@/components/Sparkline'
import { spikeHref, spikeText } from '@/components/SpikeBanner'
import { StatCard } from '@/components/StatCard'
import { Button } from '@/components/ui/button'
import { useRealtime, useWindowReports } from '@/hooks/realtime'
import { useClusters, useNumbers, useSpikes } from '@/hooks/useData'
import { useNow } from '@/hooks/useNow'
import { useTriage } from '@/hooks/useTriage'
import { computeStats } from '@/lib/analytics'
import { AVG_LOSS_PREVENTED_INR } from '@/lib/estimate'
import { inr, timeAgo } from '@/lib/format'
import type { StatsRange } from '@/lib/types'

const FEED_SIZE = 25

export default function OverviewPage() {
  const [range, setRange] = useState<StatsRange>('24h')
  const [mask, setMask] = useState(false)
  const now = useNow(30_000)
  const { data: reports } = useWindowReports()
  const { data: numbers } = useNumbers()
  const clusters = useClusters()
  const spikes = useSpikes()
  const { freshIds, lastInsertAt, status } = useRealtime()
  const triage = useTriage()

  const stats = useMemo(() => (reports ? computeStats(reports, range, now) : undefined), [reports, range, now])
  const flagged = numbers?.filter((n) => n.is_flagged).length
  // Reviews lag behind reports, so the rate is always taken over 7 days (24 h is usually empty).
  const fpr = useMemo(() => (reports ? computeStats(reports, '7d', now).falsePositive : undefined), [reports, now])

  useEffect(() => {
    const n = stats?.awaitingReview ?? 0
    document.title = n ? `(${n}) Overview · RakshaCall` : 'Overview · RakshaCall'
  }, [stats?.awaitingReview])

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-display text-[32px] leading-tight font-semibold tracking-tight text-navy-deep">Overview</h1>
          <p className="text-[15px] text-muted-foreground">Fraud-call reports from citizens&apos; phones, as they arrive.</p>
        </div>
        <Segmented
          label="Time range"
          value={range}
          onChange={setRange}
          options={[
            { value: '24h', label: 'Last 24 h' },
            { value: '7d', label: 'Last 7 days' },
          ]}
        />
      </div>

      <section aria-label="Summary" className="grid grid-cols-6 gap-4 max-xl:grid-cols-3">
        <StatCard
          label="Total reports"
          loading={!stats}
          note={stats && `${stats.byBand.scam} scam · ${stats.byBand.high} high · ${stats.byBand.warn} warning`}
          aside={stats && <Sparkline values={stats.overTime.map((b) => b.count)} className="mb-1 h-8 w-16 shrink-0" />}
        >
          {stats && <AnimatedNumber value={stats.total} />}
        </StatCard>
        <StatCard label="Scam calls disconnected" accent="#b42318" loading={!stats} note="Phone ended the call automatically">
          {stats && <AnimatedNumber value={stats.disconnected} />}
        </StatCard>
        <StatCard label="Numbers flagged" accent="#b42318" loading={flagged === undefined} note="Reported by 3+ independent devices">
          {flagged !== undefined && <AnimatedNumber value={flagged} />}
        </StatCard>
        <StatCard label="Active clusters" loading={!clusters} note="Number series used by likely gangs (7 d)">
          {clusters && <AnimatedNumber value={clusters.length} />}
        </StatCard>
        <StatCard
          label="False-positive rate · 7 d"
          loading={!fpr}
          note={fpr && (fpr.reviewed ? `${fpr.falsePositives} of ${fpr.reviewed} officer-reviewed or citizen-rated` : 'Nothing reviewed yet')}
        >
          {fpr && (fpr.rate === null ? '—' : <AnimatedNumber value={Math.round(fpr.rate * 1000)} format={(n) => `${(n / 10).toFixed(1)}%`} />)}
        </StatCard>
        <StatCard
          label="Est. money protected"
          loading={!stats}
          note={<>Estimate: disconnected × {inr.format(AVG_LOSS_PREVENTED_INR)} (placeholder)</>}
        >
          {stats && <AnimatedNumber value={stats.moneyProtectedEstimate} format={(n) => inr.format(n)} />}
        </StatCard>
      </section>

      {spikes.length > 0 && (
        <section className="surface border-l-4 border-red-600 p-5" aria-label="Active spikes">
          <h2 className="flex items-center gap-2 font-display text-lg font-semibold text-red-800">
            <TrendingUp className="size-5" aria-hidden /> Active spikes ({spikes.length})
          </h2>
          <p className="mb-2 text-[13px] text-muted-foreground">Last-hour reports at least 3× the hourly average of the previous 24 h, and at least 3 reports.</p>
          <ul className="divide-y divide-border">
            {spikes.map((s) => (
              <li key={s.id} className="flex items-center justify-between gap-4 py-2 text-sm">
                <span>{spikeText(s)}</span>
                <Link href={spikeHref(s)} className="inline-flex shrink-0 items-center gap-1 font-medium text-navy underline underline-offset-2">
                  Reports <ArrowRight className="size-3.5" aria-hidden />
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}

      <div className="grid grid-cols-3 gap-4 max-xl:grid-cols-1">
        <OverTimeChart stats={stats} />
        <ScamTypeChart stats={stats} />
        <DetectedViaChart stats={stats} />
      </div>

      <section className="surface overflow-hidden" aria-label="Live feed">
        <div className="flex flex-wrap items-center gap-3 border-b border-border px-4 py-3">
          <span className="live-dot" data-status={status} aria-hidden />
          <h2 className="font-display text-lg font-semibold text-navy-deep">Live feed</h2>
          <span className="text-xs text-muted-foreground">
            {lastInsertAt ? `Last report ${timeAgo(lastInsertAt, now)}` : status === 'live' ? 'Waiting for new reports' : 'Connecting to realtime…'}
          </span>
          <div className="ml-auto flex items-center gap-2">
            <Button variant="outline" size="sm" onClick={() => setMask((m) => !m)} aria-pressed={mask}>
              {mask ? <Eye className="size-3.5" aria-hidden /> : <EyeOff className="size-3.5" aria-hidden />}
              {mask ? 'Show numbers' : 'Mask numbers'}
            </Button>
            <Link href="/dashboard/reports" className="inline-flex h-7 items-center gap-1 rounded-lg px-2.5 text-[0.8rem] font-medium text-navy hover:bg-muted">
              All reports <ArrowRight className="size-3.5" aria-hidden />
            </Link>
          </div>
        </div>
        <ReportsTable
          reports={reports?.slice(0, FEED_SIZE)}
          freshIds={freshIds}
          mask={mask}
          onTriage={triage}
          emptyText="No reports yet. New ones appear here the moment a phone files them."
        />
        <p className="border-t border-border px-4 py-2 text-xs text-muted-foreground">
          Newest {FEED_SIZE} of the last 7 days. Keyboard: <kbd className="font-mono">J</kbd>/<kbd className="font-mono">K</kbd> move,{' '}
          <kbd className="font-mono">Enter</kbd> opens, <kbd className="font-mono">R</kbd> reviewed, <kbd className="font-mono">F</kbd> false positive.
        </p>
      </section>
    </div>
  )
}
