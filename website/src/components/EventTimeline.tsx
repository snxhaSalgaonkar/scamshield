import {
  AppWindow,
  ArrowRightLeft,
  Download,
  IndianRupee,
  KeyRound,
  Merge,
  PhoneIncoming,
  PhoneOff,
  type LucideIcon,
} from 'lucide-react'
import { callOffset, clockTime, inr } from '@/lib/format'
import { DEFAULT_CONFIG, eventLabel } from '@/lib/rules'
import type { Alert, AlertEventType } from '@/lib/types'
import { cn } from '@/lib/utils'

const ICONS: Record<AlertEventType, LucideIcon> = {
  call_start: PhoneIncoming,
  otp_received: KeyRound,
  debit_sms: IndianRupee,
  forwarding_on: ArrowRightLeft,
  merge_seen: Merge,
  remote_app: AppWindow,
  new_apk: Download,
  call_end: PhoneOff,
}

/** Phone events in call order, with the L3 points each one contributed. */
export function EventTimeline({ alert }: { alert: Alert }) {
  const callEnd = new Date(alert.callEnd).getTime()
  const events = [...alert.events].sort((a, b) => a.ts.localeCompare(b.ts))

  return (
    <ol className="relative space-y-0">
      {events.map((e, i) => {
        const Icon = ICONS[e.type]
        const points = e.type === 'call_start' ? DEFAULT_CONFIG.unknownCallerPoints : DEFAULT_CONFIG.eventPoints[e.type]
        const afterCall = new Date(e.ts).getTime() > callEnd
        const signal = !!DEFAULT_CONFIG.eventPoints[e.type]
        return (
          <li key={`${e.type}-${i}`} className="relative grid grid-cols-[64px_32px_1fr_auto] items-start gap-3 pb-4 last:pb-0">
            <span className="pt-1.5 text-right font-mono text-xs tabular-nums text-muted-foreground">
              {callOffset(e.ts, alert.callStart)}
            </span>
            <span className="relative flex justify-center">
              {i < events.length - 1 && <span className="absolute top-8 bottom-[-16px] w-px bg-border" aria-hidden />}
              <span
                className={cn(
                  'z-10 flex size-8 items-center justify-center rounded-full border',
                  signal ? 'border-red-300 bg-red-50 text-red-700' : 'border-border bg-white text-navy',
                )}
              >
                <Icon className="size-4" aria-hidden />
              </span>
            </span>
            <div className="pt-1">
              <p className={cn('text-sm', signal && 'font-semibold')}>
                {eventLabel[e.type]}
                {e.amount != null && <span className="ml-1.5 font-mono text-red-700">{inr.format(e.amount)}</span>}
              </p>
              <p className="text-xs text-muted-foreground">
                {clockTime(e.ts)}
                {afterCall && (
                  <span className="ml-2 rounded-sm bg-brass-soft px-1.5 py-0.5 text-[11px] font-medium text-amber-900">
                    After call ended · inside {DEFAULT_CONFIG.postCallWindowMin}-min window
                  </span>
                )}
                {e.type === 'call_start' && ' · caller not in contacts (gate opened)'}
              </p>
            </div>
            {points ? (
              <span className="mt-1 rounded-sm bg-navy-soft px-1.5 py-0.5 font-mono text-xs font-semibold text-navy">+{points}</span>
            ) : (
              <span />
            )}
          </li>
        )
      })}
    </ol>
  )
}
