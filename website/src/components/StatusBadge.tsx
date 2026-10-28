import { Badge } from '@/components/ui/badge'
import { statusLabel } from '@/lib/risk'
import type { AlertStatus } from '@/lib/types'
import { cn } from '@/lib/utils'

const cls: Record<AlertStatus, string> = {
  new: 'border-navy bg-navy text-white',
  reviewed: 'border-border bg-white text-foreground/80',
  escalated: 'border-red-300 bg-red-50 text-red-800',
  false_positive: 'border-border bg-muted text-muted-foreground',
}

export function StatusBadge({ status }: { status: AlertStatus }) {
  return (
    <Badge variant="outline" className={cn('rounded-sm font-medium', cls[status])}>
      {statusLabel[status]}
    </Badge>
  )
}
