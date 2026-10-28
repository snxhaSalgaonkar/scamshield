import { AnimatedNumber } from '@/components/AnimatedNumber'
import { cn } from '@/lib/utils'

interface Props {
  code: string
  title: string
  description: string
  value: number | null
  weight: number
  unusedText?: string
  /** Stagger for the one-time fill on page open. */
  delay?: number
}

/** One layer of the fusion engine as a labelled 0-100 meter. */
export function LayerMeter({ code, title, description, value, weight, unusedText, delay = 0 }: Props) {
  return (
    <div className="grid grid-cols-[52px_1fr_64px] items-center gap-5 py-4">
      <div
        className={cn(
          'flex size-[52px] items-center justify-center rounded-md font-display text-xl font-bold',
          value === null ? 'border-2 border-dashed border-navy/30 text-navy/40' : 'bg-navy text-white',
        )}
      >
        {code}
      </div>
      <div className="min-w-0">
        <div className="flex items-baseline justify-between gap-2">
          <p className="font-semibold">{title}</p>
          <p className="text-xs text-muted-foreground tabular-nums">Weight {Math.round(weight * 100)}%</p>
        </div>
        <p className="mb-2.5 text-[13px] text-muted-foreground">{description}</p>
        <div
          className={cn(
            'relative h-3 overflow-hidden rounded-full bg-navy-soft',
            value === null && 'bg-[repeating-linear-gradient(45deg,#e6e9f0_0_5px,#f4f6f9_5px_10px)]',
          )}
          role="meter"
          aria-label={`${code} ${title}`}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={value ?? undefined}
        >
          {value !== null && (
            <div
              className="meter-fill h-full rounded-full bg-gradient-to-r from-navy/80 to-navy"
              style={{ width: `${value}%`, ['--delay' as string]: `${delay}ms` }}
            />
          )}
          {/* thresholds at 40 and 75 */}
          <span className="absolute inset-y-0 left-[40%] w-0.5 bg-white/90" aria-hidden />
          <span className="absolute inset-y-0 left-[75%] w-0.5 bg-white/90" aria-hidden />
        </div>
        {value === null && unusedText && <p className="mt-1.5 text-xs text-muted-foreground italic">{unusedText}</p>}
      </div>
      <div className="text-right font-display text-[34px] leading-none font-semibold text-navy">
        {value === null ? <span className="text-navy/30">Off</span> : <AnimatedNumber value={value} fromZero duration={900} />}
      </div>
    </div>
  )
}
