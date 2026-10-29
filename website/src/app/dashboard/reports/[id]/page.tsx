'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, BadgeCheck, Lock, Printer, Rows3, Siren, Sparkles } from 'lucide-react'
import Link from 'next/link'
import { useParams } from 'next/navigation'
import { useState, type ReactNode } from 'react'
import { toast } from 'sonner'
import { DetectedViaChips } from '@/components/DetectedVia'
import { EscalateDialog } from '@/components/EscalateDialog'
import { HighlightedText } from '@/components/HighlightedText'
import { LayerMeter } from '@/components/LayerMeter'
import { RiskBadge } from '@/components/RiskBadge'
import { ScoreGauge } from '@/components/ScoreGauge'
import { StatusStamp } from '@/components/StatusStamp'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { WINDOW_KEY } from '@/hooks/realtime'
import { useAuth } from '@/lib/auth'
import { dataSource } from '@/lib/data'
import { explainL1, explainL2, explainL3 } from '@/lib/explain'
import { caseRef, dateTime, deviceHash, formatPhone, timeAgo } from '@/lib/format'
import { LAYERS, scamTypeLabel } from '@/lib/labels'
import { actionLabel, confidenceLabel, layersInAgreement, statusLabel } from '@/lib/risk'
import { groupTactics, tacticLabel } from '@/lib/tactics'
import { REPORT_STATUSES, type Report, type ReportStatus } from '@/lib/types'
import { cn } from '@/lib/utils'

const STATUS_ITEMS = REPORT_STATUSES.map((s) => ({ value: s, label: statusLabel[s] }))
const WEIGHTS = { l1_score: 0.45, l2_score: 0.2, l3_score: 0.35 } as const

function Section({ title, aside, children, className, delay }: { title: string; aside?: ReactNode; children: ReactNode; className?: string; delay?: number }) {
  return (
    <section className={cn('surface reveal p-5', className)} style={delay ? { ['--delay' as string]: `${delay}ms` } : undefined}>
      <div className="mb-3 flex items-baseline justify-between gap-4">
        <h2 className="font-display text-lg font-semibold text-navy">{title}</h2>
        {aside}
      </div>
      {children}
    </section>
  )
}

function Field({ label, children, mono }: { label: string; children: ReactNode; mono?: boolean }) {
  return (
    <div className="flex items-baseline justify-between gap-4 border-b border-border/70 py-2.5 text-sm last:border-0">
      <dt className="shrink-0 text-muted-foreground">{label}</dt>
      <dd className={cn('text-right', mono && 'font-mono text-[13px]')}>{children}</dd>
    </div>
  )
}

export default function ReportDetailPage() {
  const { id } = useParams<{ id: string }>()
  const { officer } = useAuth()
  const queryClient = useQueryClient()
  const [escalateOpen, setEscalateOpen] = useState(false)
  /** Date string once the officer changes the status here (drives the stamp animation). */
  const [stampedOn, setStampedOn] = useState<string | null>(null)

  const { data: report, isLoading, error } = useQuery({ queryKey: ['report', id], queryFn: () => dataSource.getReport(id) })
  const { data: number } = useQuery({
    queryKey: ['number', report?.caller_number],
    queryFn: () => dataSource.getNumber(report!.caller_number),
    enabled: !!report,
  })
  const { data: sameNumber } = useQuery({
    queryKey: ['reports', 'number', report?.caller_number],
    queryFn: () => dataSource.getReportsByNumber(report!.caller_number),
    enabled: !!report,
  })
  const related = sameNumber?.filter((r) => r.id !== id) ?? []

  const mutation = useMutation({
    mutationFn: (status: ReportStatus) => dataSource.updateReportStatus(id, status),
    onSuccess: (_, status) => {
      const set = (r: Report) => (r.id === id ? { ...r, status } : r)
      queryClient.setQueryData<Report | null>(['report', id], (r) => (r ? set(r) : r))
      queryClient.setQueryData<Report[]>(WINDOW_KEY, (rows) => rows?.map(set))
      void queryClient.invalidateQueries({ queryKey: ['reports', 'page'] })
      setStampedOn(new Date().toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' }))
      // Only the escalation itself closes the dialog: an earlier status change can finish after it opened.
      if (status === 'escalated') setEscalateOpen(false)
      toast.success(`Report marked ${statusLabel[status].toLowerCase()}`)
    },
    onError: (e) => toast.error(e instanceof Error ? e.message : 'Could not update the status. Try again.'),
  })

  if (isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-44 w-full rounded-lg" />
        <div className="grid grid-cols-3 gap-5">
          <Skeleton className="col-span-2 h-96 rounded-lg" />
          <Skeleton className="h-96 rounded-lg" />
        </div>
      </div>
    )
  }

  if (!report) {
    return (
      <div className="surface max-w-lg space-y-2 p-6">
        <h1 className="font-display text-2xl font-semibold text-navy">{error ? 'Could not load this report' : 'No report with this reference'}</h1>
        <p className="text-sm text-muted-foreground">{error instanceof Error ? error.message : 'The link may be mistyped. Open it again from the reports list.'}</p>
        <Link href="/dashboard/reports" className="inline-block text-sm font-medium text-navy underline underline-offset-2">
          Go to reports
        </Link>
      </div>
    )
  }

  const agreement = layersInAgreement(report)
  const tactics = groupTactics(report.matched_phrases)
  const explanations = { l1_score: explainL1(report), l2_score: explainL2(report, number), l3_score: explainL3(report) }

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between print:hidden">
        <Link href="/dashboard/reports" className="inline-flex h-8 items-center gap-1.5 text-sm text-muted-foreground hover:text-navy">
          <ArrowLeft className="size-4" aria-hidden /> Reports
        </Link>
        <Button variant="outline" size="sm" onClick={() => window.print()}>
          <Printer className="size-3.5" aria-hidden /> Print
        </Button>
      </div>

      {/* Report file cover */}
      <div className="relative pt-9">
        <div className="absolute top-0 left-0 flex h-10 items-center gap-3 rounded-t-lg bg-buff-deep px-5 font-mono text-[13px] font-medium text-navy-deep">
          File {caseRef(report)}
        </div>
        <div className="relative grid grid-cols-[auto_1fr_auto] items-center gap-8 rounded-tr-lg rounded-b-lg bg-buff px-7 py-6 shadow-[inset_0_0_0_1px_rgb(120_95_40/0.18)]">
          <ScoreGauge score={report.risk_score} />

          <div className="min-w-0 space-y-2">
            <h1 className="font-display text-[30px] leading-tight font-semibold tracking-tight text-navy-deep">
              <span className="font-mono font-medium">{formatPhone(report.caller_number)}</span>
            </h1>
            <div className="flex flex-wrap items-center gap-2 text-[15px] text-navy-deep/80">
              <RiskBadge score={report.risk_score} />
              <span className="font-semibold">{scamTypeLabel[report.scam_type]}</span>
              <span aria-hidden>·</span>
              <span>{actionLabel[report.action_taken]}</span>
            </div>
            <p className="text-[14px] text-navy-deep/70">
              {dateTime(report.created_at)} ({timeAgo(report.created_at)}) · {report.telecom_circle ?? 'Circle unknown'} circle (approx.)
            </p>
            {report.user_feedback === 'safe' && (
              <p className="inline-flex items-center gap-1.5 rounded-md bg-emerald-50 px-2.5 py-1 text-sm font-medium text-emerald-800">
                <BadgeCheck className="size-4" aria-hidden /> Marked safe by user
              </p>
            )}
          </div>

          <div className="flex flex-col items-end gap-4">
            <div className="flex h-16 items-center pr-2">
              <StatusStamp status={report.status} animate={!!stampedOn} stampedBy={stampedOn ? `${officer?.name}, ${stampedOn}` : undefined} />
            </div>
            <div className="flex items-center gap-2 print:hidden">
              <Select items={STATUS_ITEMS} value={report.status} onValueChange={(v) => v && v !== report.status && mutation.mutate(v as ReportStatus)}>
                <SelectTrigger aria-label="Report status" className="h-10 w-40 bg-white">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {STATUS_ITEMS.map((s) => (
                    <SelectItem key={s.value} value={s.value}>
                      {s.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Button className="h-10 bg-red-700 px-4 text-white hover:bg-red-800" onClick={() => setEscalateOpen(true)} disabled={report.status === 'escalated' || mutation.isPending}>
                <Siren className="size-4" aria-hidden />
                {report.status === 'escalated' ? 'Escalated' : 'Escalate'}
              </Button>
            </div>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-3 gap-5 max-lg:grid-cols-1">
        <div className="col-span-2 space-y-5 max-lg:col-span-1">
          <Section title="Detected tactics" aside={<DetectedViaChips via={report.detected_via} />}>
            {tactics.length ? (
              <div className="space-y-3">
                {tactics.map((g) => (
                  <div key={g.tactic} className="flex flex-wrap items-baseline gap-2">
                    <span className="w-48 shrink-0 text-[13px] font-semibold text-muted-foreground">{tacticLabel[g.tactic]}</span>
                    {g.phrases.map((p) => (
                      <mark key={p} className="rounded-md bg-red-50 px-2 py-1 text-[14px] font-medium text-red-900 ring-1 ring-red-200 ring-inset">
                        “{p}”
                      </mark>
                    ))}
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">No scam phrases on this call. It was caught by {report.detected_via.includes('voice') ? 'other signals' : 'SMS, reputation or event correlation, without listening to the call'}.</p>
            )}
            <p className="mt-4 flex items-center gap-2 rounded-md bg-navy-soft/70 px-3 py-2 text-[13px] text-navy-deep/80">
              <Lock className="size-3.5 shrink-0" aria-hidden />
              Conversation never leaves the citizen&apos;s phone — only detected tactics are reported.
            </p>
          </Section>

          <Section
            title="How the score was built"
            delay={80}
            aside={
              <p className="text-[13px] text-muted-foreground">
                Layers in agreement: <strong className="text-foreground">{agreement} of 3</strong> · {confidenceLabel(agreement)} confidence
              </p>
            }
          >
            <div className="divide-y divide-border">
              {LAYERS.map((l, i) => (
                <LayerMeter
                  key={l.key}
                  code={l.code}
                  title={l.name}
                  description={l.help}
                  value={report[l.key]}
                  explanation={explanations[l.key]}
                  weight={WEIGHTS[l.key]}
                  delay={i * 150}
                />
              ))}
            </div>
            <p className="mt-2 text-[12.5px] text-muted-foreground">
              A layer at 40 or more counts as agreeing. The phone fuses the layers (with safety floors for strong single signals) into the
              risk score: 40+ warns, 75+ starts a countdown, 90+ disconnects.
            </p>
          </Section>

          {report.similarity && (
            <Section title="AI pattern match" delay={140}>
              <p className="flex items-start gap-3 text-[15px]">
                <Sparkles className="mt-0.5 size-5 shrink-0 text-brass" aria-hidden />
                <span>
                  Matches known scam pattern: <q className="font-semibold">{report.similarity.matched}</q>{' '}
                  <span className="text-muted-foreground">({Math.round(report.similarity.score * 100)}% similar)</span>
                </span>
              </p>
            </Section>
          )}

          <Section title="Reasons" delay={200}>
            {report.reasons.length ? (
              <ul className="grid grid-cols-2 gap-x-6 gap-y-2 max-md:grid-cols-1">
                {report.reasons.map((r) => (
                  <li key={r} className="flex gap-2.5 text-[15px]">
                    <span className="mt-2 size-1.5 shrink-0 rounded-full bg-navy" aria-hidden />
                    <span>
                      <HighlightedText text={r} />
                    </span>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-sm text-muted-foreground">The phone sent no reasons with this report.</p>
            )}
          </Section>
        </div>

        <div className="space-y-5">
          <Section title="This number" delay={60}>
            {number === undefined ? (
              <Skeleton className="h-32" />
            ) : number ? (
              <dl>
                <Field label="Status">
                  {number.is_flagged ? (
                    <span className="rounded-sm bg-red-700 px-2 py-0.5 text-xs font-semibold text-white">Flagged</span>
                  ) : (
                    <span className="text-muted-foreground">Not flagged</span>
                  )}
                </Field>
                <Field label="Reports">
                  <span className="font-display text-lg font-semibold tabular-nums">{number.report_count}</span>
                </Field>
                <Field label="Reporters (distinct devices)">
                  <span className="font-display text-lg font-semibold tabular-nums">{number.reporter_count}</span>
                </Field>
                <Field label="Marked safe by">{number.safe_count} device{number.safe_count === 1 ? '' : 's'}</Field>
                <Field label="Max risk">{number.max_risk_score}</Field>
                <Field label="First seen">{dateTime(number.first_seen)}</Field>
              </dl>
            ) : (
              <p className="text-sm text-muted-foreground">No reputation record yet.</p>
            )}
            <div className="mt-3 flex flex-col gap-1.5">
              <Link href={`/dashboard/reports?number=${report.caller_number.replace(/\D/g, '')}`} className="inline-flex items-center gap-1.5 text-sm font-medium text-navy underline underline-offset-2">
                <Rows3 className="size-3.5" aria-hidden /> All reports for this number
              </Link>
            </div>
          </Section>

          <Section title="Other reports from this number" delay={120} aside={<span className="text-[13px] text-muted-foreground tabular-nums">{sameNumber ? related.length : ''}</span>}>
            {!sameNumber ? (
              <Skeleton className="h-24" />
            ) : related.length === 0 ? (
              <p className="text-sm text-muted-foreground">First report of this number.</p>
            ) : (
              <ul className="-mx-2">
                {related.slice(0, 6).map((r) => (
                  <li key={r.id}>
                    <Link href={`/dashboard/reports/${r.id}`} className="flex items-center justify-between gap-3 rounded-md px-2 py-2 hover:bg-navy-soft">
                      <span className="min-w-0">
                        <span className="block font-mono text-[12.5px] font-medium text-navy">{caseRef(r)}</span>
                        <span className="block truncate text-xs text-muted-foreground">
                          {scamTypeLabel[r.scam_type]}, {timeAgo(r.created_at)} · {deviceHash(r.device_id)}
                        </span>
                      </span>
                      <RiskBadge score={r.risk_score} />
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </Section>

          <Section title="Call record" delay={180}>
            <dl>
              <Field label="Call started">{report.call_started_at ? dateTime(report.call_started_at) : '—'}</Field>
              <Field label="Reported">{dateTime(report.created_at)}</Field>
              <Field label="Number series">{report.number_prefix}-XXXXX</Field>
              <Field label="Reporting device" mono>
                {deviceHash(report.device_id)}
              </Field>
            </dl>
            <p className="mt-3 text-[13px] leading-relaxed text-muted-foreground">
              The device is shown as a short opaque hash. No citizen name, number, contacts, SMS text or audio is ever sent.
            </p>
          </Section>
        </div>
      </div>

      <EscalateDialog report={report} open={escalateOpen} onOpenChange={setEscalateOpen} pending={mutation.isPending} onConfirm={() => mutation.mutate('escalated')} />
    </div>
  )
}
