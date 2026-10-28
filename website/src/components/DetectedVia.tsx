import { Flag, Link2, MessageSquareText, Mic, Users, type LucideIcon } from 'lucide-react'
import { detectedViaHelp, detectedViaLabel } from '@/lib/labels'
import type { DetectedVia } from '@/lib/types'
import { cn } from '@/lib/utils'

export const detectedViaIcon: Record<DetectedVia, LucideIcon> = {
  voice: Mic,
  sms: MessageSquareText,
  reputation: Users,
  correlation: Link2,
  manual_report: Flag,
}

/** Compact icon row for tables; labelled for screen readers and on hover. */
export function DetectedViaIcons({ via, className }: { via: DetectedVia[]; className?: string }) {
  return (
    <span className={cn('inline-flex items-center gap-1', className)} aria-label={`Detected via ${via.map((v) => detectedViaLabel[v]).join(', ')}`}>
      {via.map((v) => {
        const Icon = detectedViaIcon[v]
        return (
          <span key={v} title={`${detectedViaLabel[v]}: ${detectedViaHelp[v]}`} className="flex size-6 items-center justify-center rounded-md bg-navy-soft text-navy">
            <Icon className="size-3.5" aria-hidden />
          </span>
        )
      })}
    </span>
  )
}

/** Labelled chips for the detail page. */
export function DetectedViaChips({ via }: { via: DetectedVia[] }) {
  return (
    <span className="inline-flex flex-wrap gap-1.5">
      {via.map((v) => {
        const Icon = detectedViaIcon[v]
        return (
          <span key={v} title={detectedViaHelp[v]} className="inline-flex items-center gap-1.5 rounded-md bg-navy-soft px-2 py-1 text-xs font-medium text-navy">
            <Icon className="size-3.5" aria-hidden />
            {detectedViaLabel[v]}
          </span>
        )
      })}
    </span>
  )
}
