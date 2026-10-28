import { ShieldAlert, ShieldCheck, ShieldX, TriangleAlert } from 'lucide-react'
import { bandLabel, bandOf } from '@/lib/risk'
import { cn } from '@/lib/utils'

const styles = {
  safe: { icon: ShieldCheck, cls: 'bg-emerald-50 text-emerald-800 ring-emerald-600/25' },
  warn: { icon: TriangleAlert, cls: 'bg-amber-50 text-amber-900 ring-amber-600/30' },
  high: { icon: ShieldAlert, cls: 'bg-red-50 text-red-800 ring-red-600/30' },
  scam: { icon: ShieldX, cls: 'bg-red-700 text-white ring-red-800/40' },
} as const

/** Score + band label + icon, so risk never relies on colour alone. */
export function RiskBadge({ score, className }: { score: number; className?: string }) {
  const band = bandOf(score)
  const { icon: Icon, cls } = styles[band]
  return (
    <span className={cn('inline-flex items-center gap-1.5 rounded-md px-2 py-0.5 text-xs font-medium ring-1 ring-inset', cls, className)}>
      <Icon className="size-3.5" aria-hidden />
      <span className="text-sm font-semibold tabular-nums">{score}</span>
      <span>{bandLabel[band]}</span>
    </span>
  )
}
