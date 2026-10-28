'use client'

import { ArrowDownRight, ArrowRight, ArrowUpRight, Check, ClipboardCopy, Network, Rows3 } from 'lucide-react'
import Link from 'next/link'
import { useRouter, useSearchParams } from 'next/navigation'
import { Suspense, useMemo, useState } from 'react'
import { toast } from 'sonner'
import { RiskBadge } from '@/components/RiskBadge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { useWindowReports } from '@/hooks/realtime'
import { useClusters } from '@/hooks/useData'
import type { Cluster } from '@/lib/analytics'
import { caseRef, dateTime, formatPhone, shortDate, timeAgo } from '@/lib/format'
import { scamTypeLabel } from '@/lib/labels'
import { cn } from '@/lib/utils'

/** Plain text an officer can send to the telecom operator / DoT asking for a series block. */
function blockRecommendation(c: Cluster): string {
  return [
    `Subject: Request to review number series ${c.prefix}-XXXXX for organised fraud`,
    ``,
    `RakshaCall has received ${c.reportCount} fraud-call reports in the last 7 days from ${c.deviceCount} independent citizen devices,`,
    `involving ${c.numbers.length} different numbers in the series ${c.prefix}-XXXXX${c.operator ? ` (${c.operator}` : ''}${c.circle ? `${c.operator ? ', ' : ' ('}${c.circle} circle, approx.)` : c.operator ? ')' : ''}.`,
    `Dominant pattern: ${scamTypeLabel[c.topScamType]}. Highest risk score: ${c.maxRisk}/100.`,
    `Active ${dateTime(c.firstSeen)} to ${dateTime(c.lastSeen)}.`,
    ``,
    `Numbers reported:`,
    ...c.numbers.map((n) => `  ${formatPhone(n)}`),
    ``,
    `Rotation through consecutive numbers in one series suggests an organised group avoiding single-number blocking.`,
    `Recommended: verify KYC of the subscribers in this series and suspend the reported numbers pending review.`,
  ].join('\n')
}

const TrendIcon = ({ t }: { t: Cluster['trend'] }) =>
  t === 'up' ? (
    <span className="inline-flex items-center gap-0.5 text-xs font-semibold text-red-700" title="More reports in the last 24 h than its daily average">
      <ArrowUpRight className="size-4" aria-hidden /> Rising
    </span>
  ) : t === 'down' ? (
    <span className="inline-flex items-center gap-0.5 text-xs font-semibold text-emerald-700" title="Fewer reports in the last 24 h than its daily average">
      <ArrowDownRight className="size-4" aria-hidden /> Falling
    </span>
  ) : (
    <span className="inline-flex items-center gap-0.5 text-xs font-semibold text-muted-foreground">
      <ArrowRight className="size-4" aria-hidden /> Steady
    </span>
  )

function ClusterDetail({ c }: { c: Cluster }) {
  const { data: reports } = useWindowReports()
  const [copied, setCopied] = useState(false)
  const rows = useMemo(() => (reports ?? []).filter((r) => r.number_prefix === c.prefix), [reports, c.prefix])
  const perNumber = useMemo(() => {
    const m = new Map<string, number>()
    rows.forEach((r) => m.set(r.caller_number, (m.get(r.caller_number) ?? 0) + 1))
    return [...m.entries()].sort((a, b) => b[1] - a[1])
  }, [rows])

  return (
    <section className="surface reveal space-y-5 p-6" aria-label={`Cluster ${c.prefix}`}>
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h2 className="font-display text-2xl font-semibold text-navy-deep">
            <span className="font-mono">{c.prefix}-XXXXX</span>
          </h2>
          <p className="text-sm text-muted-foreground">
            {[c.operator, c.circle && `${c.circle} (approx.)`].filter(Boolean).join(' · ') || 'Operator unknown'} · active since {dateTime(c.firstSeen)}
          </p>
        </div>
        <div className="flex gap-2">
          <Link href={`/dashboard/reports?prefix=${c.prefix}`} className="inline-flex h-8 items-center gap-1.5 rounded-lg border border-border px-2.5 text-sm font-medium hover:bg-muted">
            <Rows3 className="size-4" aria-hidden /> Reports
          </Link>
          <Button
            className="bg-navy text-white hover:bg-navy/90"
            onClick={async () => {
              try {
                await navigator.clipboard.writeText(blockRecommendation(c))
                setCopied(true)
                toast.success('Block recommendation copied. Paste it into your email to the operator / DoT.')
                setTimeout(() => setCopied(false), 2500)
              } catch {
                toast.error('Clipboard blocked by the browser')
              }
            }}
          >
            {copied ? <Check className="size-4" aria-hidden /> : <ClipboardCopy className="size-4" aria-hidden />}
            Recommend series block
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-[1fr_2fr] gap-6 max-lg:grid-cols-1">
        <div>
          <h3 className="mb-2 text-sm font-semibold text-muted-foreground">Numbers in this cluster ({perNumber.length})</h3>
          <ul className="divide-y divide-border/70">
            {perNumber.map(([n, count]) => (
              <li key={n} className="flex items-center justify-between py-1.5 text-sm">
                <Link href={`/dashboard/numbers?number=${encodeURIComponent(n)}`} className="font-mono hover:text-navy hover:underline">
                  {formatPhone(n)}
                </Link>
                <span className="text-muted-foreground tabular-nums">
                  {count} report{count > 1 ? 's' : ''}
                </span>
              </li>
            ))}
          </ul>
        </div>
        <div>
          <h3 className="mb-2 text-sm font-semibold text-muted-foreground">Timeline (newest first)</h3>
          <ol className="relative space-y-1 border-l-2 border-navy-soft pl-4">
            {rows.map((r) => (
              <li key={r.id} className="relative">
                <span className="absolute top-3 -left-[21px] size-2.5 rounded-full border-2 border-[var(--neu-base)] bg-navy" aria-hidden />
                <Link href={`/dashboard/reports/${r.id}`} className="flex items-center gap-3 rounded-md px-2 py-1.5 text-sm hover:bg-navy-soft">
                  <span className="w-32 shrink-0 text-muted-foreground tabular-nums">{dateTime(r.created_at)}</span>
                  <RiskBadge score={r.risk_score} />
                  <span className="min-w-0 flex-1 truncate">{scamTypeLabel[r.scam_type]}</span>
                  <span className="font-mono text-xs text-muted-foreground">{formatPhone(r.caller_number)}</span>
                  <span className="hidden font-mono text-xs text-muted-foreground xl:inline">{caseRef(r)}</span>
                </Link>
              </li>
            ))}
          </ol>
        </div>
      </div>
    </section>
  )
}

function ClustersView() {
  const clusters = useClusters()
  const router = useRouter()
  const params = useSearchParams()
  const selectedPrefix = params.get('prefix')
  const selected = clusters?.find((c) => c.prefix === selectedPrefix)

  return (
    <div className="space-y-5">
      <div>
        <h1 className="font-display text-[32px] leading-tight font-semibold tracking-tight text-navy-deep">Number-series clusters</h1>
        <p className="max-w-3xl text-[15px] text-muted-foreground">
          Scammers rotate through blocks of numbers to avoid single-number blocking. Clusters reveal likely organised groups. A series (first 5
          digits) becomes a cluster with 3+ distinct numbers or 5+ reports in the last 7 days.
        </p>
      </div>

      {!clusters ? (
        <div className="grid grid-cols-3 gap-4">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-48 rounded-2xl" />
          ))}
        </div>
      ) : clusters.length === 0 ? (
        <div className="surface p-8 text-center text-sm text-muted-foreground">
          <Network className="mx-auto mb-2 size-8 opacity-40" aria-hidden />
          No number series meets the cluster threshold in the last 7 days.
        </div>
      ) : (
        <div className="grid grid-cols-3 gap-4 max-xl:grid-cols-2">
          {clusters.map((c) => {
            const active = c.prefix === selectedPrefix
            return (
              <button
                key={c.prefix}
                type="button"
                onClick={() => router.replace(active ? '/dashboard/clusters' : `/dashboard/clusters?prefix=${c.prefix}`, { scroll: false })}
                aria-pressed={active}
                className={cn(
                  'surface flex flex-col gap-3 p-5 text-left transition-shadow focus-visible:outline-2 focus-visible:outline-navy',
                  active && 'ring-2 ring-navy',
                )}
              >
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <p className="font-mono text-xl font-semibold text-navy-deep">{c.prefix}-XXXXX</p>
                    <p className="text-[13px] text-muted-foreground">{[c.operator, c.circle].filter(Boolean).join(' · ') || 'Unknown series'}</p>
                  </div>
                  <TrendIcon t={c.trend} />
                </div>
                <dl className="grid grid-cols-3 gap-2 text-center">
                  {[
                    ['Numbers', c.numbers.length],
                    ['Reports', c.reportCount],
                    ['Devices', c.deviceCount],
                  ].map(([k, v]) => (
                    <div key={k} className="rounded-lg bg-navy-soft/60 py-2">
                      <dd className="font-display text-2xl leading-none font-semibold text-navy tabular-nums">{v}</dd>
                      <dt className="mt-1 text-[11px] font-semibold tracking-wide text-muted-foreground uppercase">{k}</dt>
                    </div>
                  ))}
                </dl>
                <div className="flex items-center justify-between text-[13px]">
                  <span className="font-semibold">{scamTypeLabel[c.topScamType]}</span>
                  <span className="text-muted-foreground">
                    since {shortDate(c.firstSeen)} · last {timeAgo(c.lastSeen)}
                  </span>
                </div>
              </button>
            )
          })}
        </div>
      )}

      {selectedPrefix && clusters && !selected && (
        <p className="surface p-5 text-sm text-muted-foreground">
          Series {selectedPrefix}-XXXXX is not a cluster right now (fewer than 3 numbers and 5 reports in 7 days).{' '}
          <Link href={`/dashboard/reports?prefix=${selectedPrefix}`} className="font-medium text-navy underline">
            See its reports
          </Link>
          .
        </p>
      )}
      {selected && <ClusterDetail key={selected.prefix} c={selected} />}
    </div>
  )
}

export default function ClustersPage() {
  return (
    <Suspense fallback={<Skeleton className="h-96 w-full" />}>
      <ClustersView />
    </Suspense>
  )
}
