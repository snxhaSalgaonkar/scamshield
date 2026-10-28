import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Eye, FileCheck2, FileX2, Printer, Siren, Zap } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'
import { toast } from 'sonner'
import { EscalateDialog } from '@/components/EscalateDialog'
import { EventTimeline } from '@/components/EventTimeline'
import { HighlightedText } from '@/components/HighlightedText'
import { LayerMeter } from '@/components/LayerMeter'
import { NumberStatusBadge } from '@/components/NumberStatusBadge'
import { RiskScore } from '@/components/RiskScore'
import { ScoreGauge } from '@/components/ScoreGauge'
import { StatusStamp } from '@/components/StatusStamp'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { useAuth } from '@/lib/auth'
import { dataSource } from '@/lib/data'
import { caseRef, dateTime, duration, timeAgo } from '@/lib/format'
import { numberTypeLabel, statusLabel } from '@/lib/risk'
import { DEFAULT_CONFIG, firedCombos } from '@/lib/rules'
import type { Alert, AlertStatus } from '@/lib/types'
import { cn } from '@/lib/utils'

const STATUS_ITEMS = (Object.keys(statusLabel) as AlertStatus[]).map((s) => ({ value: s, label: statusLabel[s] }))

/** "OTP Theft" -> "OTP theft": lower-case words, keep acronyms. */
const sentenceCase = (s: string) =>
  s
    .split(' ')
    .map((w) => (w.length > 1 && w === w.toUpperCase() ? w : w.toLowerCase()))
    .join(' ')

function caseTitle(a: Alert) {
  if (a.scamType === 'False Positive') return <>Likely false alarm from <span className="font-mono font-medium">{a.callerPrefix}</span></>
  return (
    <>
      Suspected {sentenceCase(a.scamType)} call from <span className="font-mono font-medium">{a.callerPrefix}</span>
    </>
  )
}

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

export function CaseDetailPage() {
  const { id = '' } = useParams()
  const { session } = useAuth()
  const queryClient = useQueryClient()
  const [escalateOpen, setEscalateOpen] = useState(false)
  const [viewedAt] = useState(() => new Date())
  const [stampedNow, setStampedNow] = useState(false)

  const { data: alert, isLoading } = useQuery({ queryKey: ['alert', id], queryFn: () => dataSource.getAlert(id), staleTime: Infinity })
  const { data: number } = useQuery({
    queryKey: ['number', alert?.callerHash],
    queryFn: () => dataSource.getNumber(alert!.callerHash),
    enabled: !!alert,
  })
  const { data: sameCaller } = useQuery({
    queryKey: ['alerts', alert?.callerHash ?? null],
    queryFn: () => dataSource.getAlerts({ callerHash: alert!.callerHash }),
    enabled: !!alert,
  })
  const related = sameCaller?.filter((a) => a.id !== id) ?? []

  const mutation = useMutation({
    mutationFn: ({ status, note }: { status: AlertStatus; note?: string }) => dataSource.updateAlertStatus(id, status, note),
    onSuccess: (_, { status }) => {
      queryClient.setQueryData(['alert', id], (prev: Alert | null | undefined) => (prev ? { ...prev, status } : prev))
      queryClient.invalidateQueries({ queryKey: ['alerts'] })
      queryClient.invalidateQueries({ queryKey: ['stats'] })
      queryClient.invalidateQueries({ queryKey: ['audit'] })
      setStampedNow(true)
      toast.success(`Case marked ${statusLabel[status].toLowerCase()}`)
      setEscalateOpen(false)
    },
    onError: (e) => toast.error(e instanceof Error ? e.message : 'Could not update the case status. Try again.'),
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

  if (!alert) {
    return (
      <div className="surface max-w-lg space-y-2 p-6">
        <h1 className="font-display text-2xl font-semibold text-navy">No case with this reference</h1>
        <p className="text-sm text-muted-foreground">It may have been removed, or the link is mistyped. Open it again from the live feed.</p>
        <Link to="/dashboard" className="inline-block text-sm font-medium text-navy underline underline-offset-2">
          Go to the live feed
        </Link>
      </div>
    )
  }

  const combos = firedCombos(alert.events)
  const stampDate = viewedAt.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' })

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between print:hidden">
        <Link to="/dashboard" className="inline-flex h-8 items-center gap-1.5 text-sm text-muted-foreground hover:text-navy">
          <ArrowLeft className="size-4" aria-hidden /> Live feed
        </Link>
        <Button variant="outline" size="sm" onClick={() => window.print()}>
          <Printer className="size-3.5" aria-hidden /> Print case file
        </Button>
      </div>

      {/* Case file cover: buff folder with a tab, like a station's case diary */}
      <div className="relative pt-9">
        <div className="absolute top-0 left-0 flex h-10 items-center gap-3 rounded-t-lg bg-buff-deep px-5 font-mono text-[13px] font-medium text-navy-deep">
          File {caseRef(alert)}
        </div>
        <div className="relative grid grid-cols-[auto_1fr_auto] items-center gap-8 rounded-tr-lg rounded-b-lg bg-buff px-7 py-6 shadow-[inset_0_0_0_1px_rgb(120_95_40/0.18),0_1px_2px_rgb(60_45_10/0.08)]">
          <ScoreGauge score={alert.score} />

          <div className="min-w-0 space-y-2">
            <h1 className="font-display text-[30px] leading-tight font-semibold tracking-tight text-navy-deep">{caseTitle(alert)}</h1>
            <p className="text-[15px] text-navy-deep/70">
              Reported from {alert.region}, {dateTime(alert.createdAt)} ({timeAgo(alert.createdAt)})
            </p>
            <p className={cn('inline-flex items-center gap-1.5 text-sm', alert.complaintSubmitted ? 'text-emerald-800' : 'text-navy-deep/60')}>
              {alert.complaintSubmitted ? <FileCheck2 className="size-4" aria-hidden /> : <FileX2 className="size-4" aria-hidden />}
              {alert.complaintSubmitted ? 'Citizen filed a complaint and agreed to share identity' : 'No complaint filed, so identity is withheld'}
            </p>
          </div>

          <div className="flex flex-col items-end gap-4">
            <div className="flex h-16 items-center pr-2">
              <StatusStamp
                status={alert.status}
                animate={stampedNow}
                stampedBy={stampedNow ? `${session?.officer.name}, ${stampDate}` : undefined}
              />
            </div>
            <div className="flex items-center gap-2 print:hidden">
              <Select
                items={STATUS_ITEMS}
                value={alert.status}
                onValueChange={(v) => v && v !== alert.status && mutation.mutate({ status: v as AlertStatus })}
              >
                <SelectTrigger aria-label="Case status" className="h-10 w-40 bg-white">
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
              <Button
                className="h-10 bg-red-700 px-4 hover:bg-red-800"
                onClick={() => setEscalateOpen(true)}
                disabled={alert.status === 'escalated'}
              >
                <Siren className="size-4" aria-hidden />
                {alert.status === 'escalated' ? 'Escalated' : 'Escalate case'}
              </Button>
            </div>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-3 gap-5">
        <div className="col-span-2 space-y-5">
          <Section
            title="How the score was built"
            aside={<p className="text-[13px] text-muted-foreground">Three independent layers, fused by weight</p>}
          >
            <div className="divide-y divide-border">
              <LayerMeter
                code="L1"
                title="Voice and keyword AI"
                description="Scam phrases picked up by opt-in speakerphone speech-to-text"
                value={alert.l1}
                weight={DEFAULT_CONFIG.weights.l1}
                unusedText="Not used on this call. The voice layer is opt-in on the citizen's phone."
              />
              <LayerMeter
                code="L2"
                title="Caller reputation"
                description="Independent reports, calls per hour and number type"
                value={alert.l2}
                weight={DEFAULT_CONFIG.weights.l2}
                delay={150}
              />
              <LayerMeter
                code="L3"
                title="Event correlation"
                description="OTP, debit, forwarding, merge and app signals inside the call window"
                value={alert.l3}
                weight={DEFAULT_CONFIG.weights.l3}
                delay={300}
              />
            </div>
            <p className="mt-2 rounded-md bg-navy-soft/70 px-4 py-2.5 text-[13px] text-navy-deep/80">
              Below {DEFAULT_CONFIG.thresholds.warning} the phone stays silent. From {DEFAULT_CONFIG.thresholds.warning} it shows a
              warning banner, and from {DEFAULT_CONFIG.thresholds.red} a full-screen alert with an End call button.
            </p>
          </Section>

          <Section title="What happened on the phone" delay={120} aside={<p className="text-[13px] text-muted-foreground">Time from call start</p>}>
            <EventTimeline alert={alert} />
            {combos.length > 0 && (
              <div className="mt-5 flex gap-3 rounded-md bg-red-50 p-4 shadow-[inset_0_0_0_1px_rgb(220_38_38/0.2)]">
                <Zap className="mt-0.5 size-5 shrink-0 text-red-700" aria-hidden />
                <div>
                  <p className="font-semibold text-red-800">
                    {combos.length > 1 ? 'Combo rules fired, so this was an automatic red flag' : 'A combo rule fired, so this was an automatic red flag'}
                  </p>
                  <ul className="mt-1 space-y-0.5 text-sm text-red-900">
                    {combos.map((c) => (
                      <li key={c}>{c}</li>
                    ))}
                  </ul>
                </div>
              </div>
            )}
          </Section>

          <Section title="Why it was flagged" delay={240}>
            <ul className="grid grid-cols-2 gap-x-6 gap-y-2">
              {alert.reasons.map((r) => (
                <li key={r} className="flex gap-2.5 text-[15px]">
                  <span className="mt-2 size-1.5 shrink-0 rounded-full bg-navy" aria-hidden />
                  <span>
                    <HighlightedText text={r} />
                  </span>
                </li>
              ))}
            </ul>
          </Section>
        </div>

        <div className="space-y-5">
          <Section title="Caller reputation" delay={80}>
            <div className="mb-2">
              <p className="font-mono text-lg font-medium">{alert.callerPrefix}</p>
              <p className="font-mono text-xs text-muted-foreground">Number ref #{alert.callerHash}</p>
            </div>
            {number ? (
              <dl>
                <Field label="Status">
                  <NumberStatusBadge status={number.status} />
                </Field>
                <Field label="Number type">{numberTypeLabel[number.numberType]}</Field>
                <Field label="Independent reports">
                  <span className="font-display text-lg font-semibold tabular-nums">{number.reportCount}</span>
                </Field>
                <Field label="Calls per hour">
                  <span className="tabular-nums">{number.callsPerHour}</span>
                </Field>
                <Field label="First seen">{dateTime(number.firstSeen)}</Field>
              </dl>
            ) : (
              <Skeleton className="h-32" />
            )}
            <Link
              to={`/dashboard?number=${alert.callerHash}`}
              className="mt-3 inline-flex h-9 items-center text-sm font-medium text-navy underline underline-offset-2"
            >
              Show every alert from this number
            </Link>
          </Section>

          <Section
            title="Other cases from this number"
            delay={120}
            aside={<span className="text-[13px] text-muted-foreground tabular-nums">{sameCaller ? related.length : ''}</span>}
          >
            {!sameCaller ? (
              <Skeleton className="h-24" />
            ) : related.length === 0 ? (
              <p className="text-sm text-muted-foreground">First report of this number. Watch for repeats.</p>
            ) : (
              <ul className="-mx-2">
                {related.slice(0, 5).map((r) => (
                  <li key={r.id}>
                    <Link
                      to={`/dashboard/cases/${r.id}`}
                      className="flex items-center justify-between gap-3 rounded-md px-2 py-2 hover:bg-navy-soft"
                    >
                      <span className="min-w-0">
                        <span className="block font-mono text-[12.5px] font-medium text-navy">{caseRef(r)}</span>
                        <span className="block truncate text-xs text-muted-foreground">
                          {r.scamType} in {r.region}, {timeAgo(r.createdAt)}
                        </span>
                      </span>
                      <RiskScore score={r.score} />
                    </Link>
                  </li>
                ))}
              </ul>
            )}
            {related.length > 5 && (
              <Link to={`/dashboard?number=${alert.callerHash}`} className="mt-1 inline-block text-sm font-medium text-navy underline underline-offset-2">
                All {related.length + 1} cases
              </Link>
            )}
          </Section>

          <Section title="Call record" delay={160}>
            <dl>
              <Field label="Call event" mono>
                {alert.callEventId}
              </Field>
              <Field label="Call started">{dateTime(alert.callStart)}</Field>
              <Field label="Duration">{duration(alert.callStart, alert.callEnd)}</Field>
              <Field label="Region">{alert.region}</Field>
              <Field label="Device" mono>
                {alert.deviceId}
              </Field>
            </dl>
            <p className="mt-3 text-[13px] leading-relaxed text-muted-foreground">
              The device ID is an opaque reference. SMS text, OTP values, contacts and call audio never leave the citizen&apos;s phone.
            </p>
          </Section>

          <div className="reveal flex gap-3 rounded-lg bg-brass-soft p-4 text-[13px] leading-relaxed text-amber-950 shadow-[inset_0_0_0_1px_rgb(180_140_40/0.3)]" style={{ ['--delay' as string]: '240ms' }}>
            <Eye className="mt-0.5 size-4 shrink-0" aria-hidden />
            <p>
              This view was written to the audit log under <strong>{session?.officer.name}</strong> at{' '}
              {viewedAt.toLocaleTimeString('en-IN', { hour12: false })} IST.
            </p>
          </div>
        </div>
      </div>

      <EscalateDialog
        key={alert.id}
        alert={alert}
        open={escalateOpen}
        onOpenChange={setEscalateOpen}
        pending={mutation.isPending}
        onConfirm={(note) => mutation.mutate({ status: 'escalated', note: note || undefined })}
      />
    </div>
  )
}
