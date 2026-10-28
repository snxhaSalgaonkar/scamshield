import type { AlertStatus } from '@/lib/types'

const STAMPS: Partial<Record<AlertStatus, { text: string; color: string }>> = {
  reviewed: { text: 'Reviewed', color: '#1c3159' },
  escalated: { text: 'Escalated', color: '#b42318' },
  false_positive: { text: 'False positive', color: '#5b6476' },
}

/** Rubber stamp on the case file. Only animates when the officer changes the status. */
export function StatusStamp({ status, stampedBy, animate }: { status: AlertStatus; stampedBy?: string; animate: boolean }) {
  const stamp = STAMPS[status]
  if (!stamp) return null
  return (
    <div key={status} className="stamp" data-animate={animate} style={{ color: stamp.color }} aria-label={`Status: ${stamp.text}`}>
      {stamp.text}
      {stampedBy && <small>{stampedBy}</small>}
    </div>
  )
}
