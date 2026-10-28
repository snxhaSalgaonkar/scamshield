import type { ReactNode } from 'react'
import { Skeleton } from '@/components/ui/skeleton'

interface Props {
  label: string
  children?: ReactNode
  note?: ReactNode
  accent?: string
  aside?: ReactNode
  loading?: boolean
}

/** One readout on the overview: big number, short label, one-line note. */
export function StatCard({ label, children, note, accent, aside, loading }: Props) {
  return (
    <div
      className="relative flex min-w-0 items-end justify-between gap-3 rounded-2xl p-5"
      style={{ background: 'var(--neu-base)', boxShadow: '6px 6px 14px var(--neu-shadow-dark), -6px -6px 14px var(--neu-shadow-light)' }}
    >
      {accent && <span className="absolute top-4 right-4 size-2.5 rounded-full" style={{ background: accent }} aria-hidden />}
      <div className="min-w-0">
        <p className="text-[12px] font-semibold tracking-wider text-muted-foreground uppercase">{label}</p>
        <div className="mt-1 truncate font-display text-[36px] leading-none font-semibold tracking-tight" style={{ color: accent ?? 'var(--eth-teal)' }}>
          {loading ? <Skeleton className="mt-1 h-9 w-20" /> : children}
        </div>
        {note && <p className="mt-2 text-[11.5px] leading-snug text-muted-foreground">{note}</p>}
      </div>
      {aside}
    </div>
  )
}
