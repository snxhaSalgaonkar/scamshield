import { Badge } from '@/components/ui/badge'
import { numberStatusLabel } from '@/lib/risk'
import type { NumberStatus } from '@/lib/types'
import { cn } from '@/lib/utils'

const cls: Record<NumberStatus, string> = {
  flagged: 'border-red-300 bg-red-50 text-red-800',
  watch: 'border-amber-300 bg-amber-50 text-amber-900',
  expired: 'border-border bg-muted text-muted-foreground',
}

export function NumberStatusBadge({ status }: { status: NumberStatus }) {
  return (
    <Badge variant="outline" className={cn('rounded-sm font-medium', cls[status])}>
      {numberStatusLabel[status]}
    </Badge>
  )
}
