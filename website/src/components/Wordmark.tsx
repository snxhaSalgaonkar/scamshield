import { Crest } from '@/components/Crest'
import { cn } from '@/lib/utils'

/** Bilingual wordmark, as on Indian police signage: Devanagari beside the Latin name. */
export function Wordmark({ size = 'md', subtitle, className }: { size?: 'md' | 'lg'; subtitle?: string; className?: string }) {
  const lg = size === 'lg'
  return (
    <div className={cn('flex items-center whitespace-nowrap', lg ? 'gap-4' : 'gap-3', className)}>
      <Crest className={lg ? 'h-20 w-[68px]' : 'h-10 w-9'} />
      <div className="leading-none">
        <div className="flex items-baseline gap-2">
          <span className={cn('font-display font-semibold tracking-tight text-white', lg ? 'text-4xl' : 'text-[19px]')}>RakshaCall</span>
          <span lang="hi" className={cn('font-deva font-semibold text-brass', lg ? 'text-2xl' : 'text-[15px]')}>
            रक्षा कॉल
          </span>
        </div>
        {subtitle && <div className={cn('mt-1 text-white/60', lg ? 'text-base' : 'text-xs')}>{subtitle}</div>}
      </div>
    </div>
  )
}
