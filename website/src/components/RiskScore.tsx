import { ShieldAlert, ShieldCheck, TriangleAlert } from 'lucide-react'
import { riskLabel, riskLevel } from '@/lib/risk'
import { cn } from '@/lib/utils'

const styles = {
  silent: { icon: ShieldCheck, cls: 'bg-emerald-50 text-emerald-800 ring-emerald-600/25' },
  warning: { icon: TriangleAlert, cls: 'bg-amber-50 text-amber-900 ring-amber-600/30' },
  red: { icon: ShieldAlert, cls: 'bg-red-50 text-red-800 ring-red-600/30' },
} as const

/** Score + label + icon, so risk never relies on colour alone. */
export function RiskScore({ score }: { score: number }) {
  const level = riskLevel(score)
  const { icon: Icon, cls } = styles[level]
  return (
    <span className={cn('inline-flex items-center gap-1.5 rounded-md px-2 py-0.5 text-xs font-medium ring-1 ring-inset', cls)}>
      <Icon className="size-3.5" aria-hidden />
      <span className="text-sm font-semibold tabular-nums">{score}</span>
      <span>{riskLabel[level]}</span>
    </span>
  )
}
