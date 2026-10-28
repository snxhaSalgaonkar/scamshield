'use client'

import { AnimatedNumber } from '@/components/AnimatedNumber'
import { cn } from '@/lib/utils'

interface Props {
  code: string
  title: string
  description: string
  /** 0–100 */
  value: number
  /** One-line explanation of what drove this layer on this call. */
  explanation: string
  weight: number
  /** Stagger for the one-time fill on page open. */
  delay?: number
}

/** One layer of the fusion engine as a labelled 0–100 meter. */
export function LayerMeter({ code, title, description, value, explanation, weight, delay = 0 }: Props) {
  const agrees = value >= 40
  return (
    <div className="grid grid-cols-[52px_1fr_72px] items-center gap-5 py-4">
      <div
        className={cn(
          'flex size-[52px] items-center justify-center rounded-md font-display text-xl font-bold',
          agrees ? 'bg-navy text-white' : 'border-2 border-dashed border-navy/30 text-navy/50',
        )}
      >
        {code}
      </div>
      <div className="min-w-0">
        <div className="flex items-baseline justify-between gap-2">
          <p className="font-semibold">
            {title} <span className="font-normal text-muted-foreground">· {description}</span>
          </p>
          <p className="shrink-0 text-xs text-muted-foreground tabular-nums">Weight {Math.round(weight * 100)}%</p>
        </div>
        <div
          className="relative mt-2 h-3 overflow-hidden rounded-full bg-navy-soft"
          role="meter"
          aria-label={`${code} ${title}`}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={value}
        >
          <div className="meter-fill h-full rounded-full bg-gradient-to-r from-navy/80 to-navy" style={{ width: `${value}%`, ['--delay' as string]: `${delay}ms` }} />
          {/* the 40 line: a layer at or above it counts towards "layers in agreement" */}
          <span className="absolute inset-y-0 left-[40%] w-0.5 bg-white/90" aria-hidden />
        </div>
        <p className="mt-1.5 text-[13px] text-foreground/80">{explanation}</p>
      </div>
      <div className="text-right font-display text-[34px] leading-none font-semibold text-navy">
        <AnimatedNumber value={value} fromZero duration={900} />
      </div>
    </div>
  )
}
