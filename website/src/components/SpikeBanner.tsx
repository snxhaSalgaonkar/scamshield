'use client'

import { TrendingUp, X } from 'lucide-react'
import Link from 'next/link'
import { useCallback, useEffect, useState } from 'react'
import { useSpikes } from '@/hooks/useData'
import type { Spike } from '@/lib/analytics'
import { filtersToParams } from '@/lib/filters'
import { scamTypeLabel } from '@/lib/labels'

const KEY = 'rakshacall.dismissed-spikes'

/** "Digital Arrest reports in Maharashtra up 4× in the last hour (12 reports)". */
export function spikeText(s: Spike): string {
  const what = s.scamType ? `${scamTypeLabel[s.scamType]} reports` : 'Reports'
  const where = s.circle ? ` in ${s.circle}` : ''
  const change = Number.isFinite(s.ratio) ? `up ${s.ratio >= 10 ? Math.round(s.ratio) : s.ratio.toFixed(1)}× ` : ''
  const tail = Number.isFinite(s.ratio) ? '' : ', none in the 24 h before'
  return `${what}${where} ${change}in the last hour (${s.lastHour} report${s.lastHour === 1 ? '' : 's'}${tail})`
}

export function spikeHref(s: Spike): string {
  const p = filtersToParams({ scamType: s.scamType ?? undefined, circle: s.circle ?? undefined, from: new Date(Date.now() - 3_600_000).toISOString() })
  return `/dashboard/reports?${p}`
}

/**
 * Dismissal is remembered per spike *and* size, so the banner comes back if the same spike grows.
 */
function useDismissed() {
  const [dismissed, setDismissed] = useState<Set<string>>(new Set())
  useEffect(() => {
    try {
      setDismissed(new Set(JSON.parse(sessionStorage.getItem(KEY) ?? '[]') as string[]))
    } catch {
      // storage unavailable: dismissals last until reload
    }
  }, [])
  const dismiss = useCallback((key: string) => {
    setDismissed((prev) => {
      const next = new Set(prev).add(key)
      try {
        sessionStorage.setItem(KEY, JSON.stringify([...next]))
      } catch {
        // ignore
      }
      return next
    })
  }, [])
  return { dismissed, dismiss }
}

const dismissKey = (s: Spike) => `${s.id}#${s.lastHour}`

export function SpikeBanner() {
  const spikes = useSpikes()
  const { dismissed, dismiss } = useDismissed()
  const visible = spikes.filter((s) => !dismissed.has(dismissKey(s)))
  if (!visible.length) return null

  return (
    <div role="alert" className="border-b border-red-300 bg-red-50 print:hidden">
      <ul className="mx-auto max-w-[1400px] divide-y divide-red-200 px-6">
        {visible.slice(0, 3).map((s) => (
          <li key={s.id} className="flex items-center gap-3 py-2.5 text-sm text-red-900">
            <TrendingUp className="size-4 shrink-0 text-red-700" aria-hidden />
            <span className="font-semibold">Spike alert:</span>
            <span className="min-w-0 flex-1 truncate">{spikeText(s)}</span>
            <Link href={spikeHref(s)} className="shrink-0 font-medium underline underline-offset-2 hover:text-red-700">
              View reports
            </Link>
            <button
              type="button"
              onClick={() => dismiss(dismissKey(s))}
              className="flex size-7 shrink-0 items-center justify-center rounded-md hover:bg-red-100"
              aria-label="Dismiss this spike alert"
            >
              <X className="size-4" aria-hidden />
            </button>
          </li>
        ))}
        {visible.length > 3 && <li className="py-1.5 text-xs text-red-800">+{visible.length - 3} more spikes on the overview page</li>}
      </ul>
    </div>
  )
}
