import type { ReactNode } from 'react'
import { AnimatedNumber } from '@/components/AnimatedNumber'
import { Sparkline } from '@/components/Sparkline'
import { Skeleton } from '@/components/ui/skeleton'
import { useStats } from '@/hooks/useStats'
import { AVG_LOSS_PREVENTED_INR } from '@/lib/estimate'
import { inr, timeAgo } from '@/lib/format'

interface CellProps {
  label: string
  children: ReactNode
  note?: ReactNode
  accentColor?: string
  aside?: ReactNode
}

function Cell({ label, children, note, accentColor, aside }: CellProps) {
  return (
    <div
      className="relative flex items-end justify-between gap-4 rounded-2xl p-5"
      style={{
        background: 'var(--neu-base)',
        boxShadow: '6px 6px 14px var(--neu-shadow-dark), -6px -6px 14px var(--neu-shadow-light)',
      }}
    >
      {/* Colour accent dot in top-left */}
      {accentColor && (
        <span
          className="absolute top-4 right-4 size-2.5 rounded-full"
          style={{ background: accentColor, boxShadow: `0 0 6px ${accentColor}` }}
          aria-hidden
        />
      )}
      <div className="min-w-0">
        <p className="text-[12px] font-semibold uppercase tracking-wider text-muted-foreground">{label}</p>
        <div
          className="mt-1 text-[38px] leading-none font-semibold tracking-tight"
          style={{ fontFamily: 'var(--font-display)', color: accentColor ?? 'var(--eth-teal)' }}
        >
          {children}
        </div>
        {note && <p className="mt-2 text-[11px] text-muted-foreground">{note}</p>}
      </div>
      {aside}
    </div>
  )
}

/** Duty board: the shift's four numbers as one neumorphic readout strip. */
export function StatsBanner() {
  const { data } = useStats()
  if (!data)
    return (
      <div className="grid grid-cols-4 gap-4">
        {Array.from({ length: 4 }).map((_, i) => (
          <Skeleton key={i} className="h-[110px] w-full rounded-2xl" />
        ))}
      </div>
    )

  return (
    <section aria-label="Shift summary, last 12 hours" className="grid grid-cols-4 gap-4">
      <Cell
        label="Alerts · last 12 h"
        note={`${data.warnings} warnings · ${data.low} low risk`}
        accentColor="var(--eth-teal)"
        aside={<Sparkline values={data.overTime.map((b) => b.count)} className="mb-1 h-8 w-24 shrink-0" />}
      >
        <AnimatedNumber value={data.totalAlerts} />
      </Cell>

      <Cell label="Red flags" accentColor="#dc2626" note="Score ≥ 75, or a combo rule fired">
        <AnimatedNumber value={data.redFlags} />
      </Cell>

      <Cell
        label="Awaiting review"
        accentColor="var(--police-brass)"
        note={data.oldestAwaitingReview ? `Oldest came in ${timeAgo(data.oldestAwaitingReview)}` : 'Queue is clear'}
      >
        <AnimatedNumber value={data.awaitingReview} />
      </Cell>

      <Cell label="Loss possibly averted" note={`Estimate: red flags × ${inr.format(AVG_LOSS_PREVENTED_INR)}`}>
        <AnimatedNumber value={data.redFlags * AVG_LOSS_PREVENTED_INR} format={(n) => inr.format(n)} />
      </Cell>
    </section>
  )
}
