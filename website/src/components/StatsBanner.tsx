import type { ReactNode } from 'react'
import { AnimatedNumber } from '@/components/AnimatedNumber'
import { Sparkline } from '@/components/Sparkline'
import { Skeleton } from '@/components/ui/skeleton'
import { useStats } from '@/hooks/useStats'
import { AVG_LOSS_PREVENTED_INR } from '@/lib/estimate'
import { inr, timeAgo } from '@/lib/format'
import { cn } from '@/lib/utils'

function Cell({ label, children, note, accent, aside }: { label: string; children: ReactNode; note?: ReactNode; accent?: string; aside?: ReactNode }) {
  return (
    <div className="relative flex items-end justify-between gap-4 px-6 py-5">
      {accent && <span className={cn('absolute top-5 bottom-5 left-0 w-[3px] rounded-r', accent)} aria-hidden />}
      <div className="min-w-0">
        <p className="text-sm font-medium text-muted-foreground">{label}</p>
        <div className="mt-1 font-display text-[40px] leading-none font-semibold tracking-tight text-navy">{children}</div>
        {note && <p className="mt-2 text-xs text-muted-foreground">{note}</p>}
      </div>
      {aside}
    </div>
  )
}

/** Duty board: the shift's four numbers as one readout strip. */
export function StatsBanner() {
  const { data } = useStats()
  if (!data) return <Skeleton className="h-[124px] w-full rounded-lg" />

  return (
    <section aria-label="Shift summary, last 12 hours" className="surface grid grid-cols-4 divide-x divide-border">
      <Cell
        label="Alerts in the last 12 hours"
        note={`${data.warnings} warnings, ${data.low} low risk`}
        aside={<Sparkline values={data.overTime.map((b) => b.count)} className="mb-1 h-8 w-24 shrink-0" />}
      >
        <AnimatedNumber value={data.totalAlerts} />
      </Cell>
      <Cell label="Red flags" accent="bg-red-600" note="Score 75 or more, or a combo rule fired">
        <span className="text-red-700">
          <AnimatedNumber value={data.redFlags} />
        </span>
      </Cell>
      <Cell
        label="Awaiting review"
        accent="bg-brass"
        note={data.oldestAwaitingReview ? `Oldest came in ${timeAgo(data.oldestAwaitingReview)}` : 'Queue is clear'}
      >
        <AnimatedNumber value={data.awaitingReview} />
      </Cell>
      <Cell label="Loss possibly averted" note={`Estimate: red flags × ${inr.format(AVG_LOSS_PREVENTED_INR)} placeholder`}>
        <AnimatedNumber value={data.redFlags * AVG_LOSS_PREVENTED_INR} format={(n) => inr.format(n)} />
      </Cell>
    </section>
  )
}
