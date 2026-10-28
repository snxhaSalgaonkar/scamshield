import { useEffect, useState } from 'react'
import { AnimatedNumber } from '@/components/AnimatedNumber'
import { riskLabel, riskLevel } from '@/lib/risk'

const INK = { silent: '#067647', warning: '#b54708', red: '#b42318' } as const
// 270° arc, open at the bottom like an instrument dial.
const R = 52
const C = 2 * Math.PI * R
const ARC = C * 0.75

/** Fused score as a dial. Sweeps from 0 once when the case opens. */
export function ScoreGauge({ score }: { score: number }) {
  const [shown, setShown] = useState(0)
  useEffect(() => {
    const t = requestAnimationFrame(() => setShown(score))
    return () => cancelAnimationFrame(t)
  }, [score])
  const level = riskLevel(score)

  return (
    <div className="relative size-[132px]" role="img" aria-label={`Risk score ${score} of 100, ${riskLabel[level]}`}>
      <svg viewBox="0 0 132 132" className="size-full -rotate-[225deg]">
        <circle cx="66" cy="66" r={R} fill="none" stroke="var(--police-buff-deep)" strokeOpacity=".55" strokeWidth="10" strokeDasharray={`${ARC} ${C}`} strokeLinecap="round" />
        {/* threshold ticks at 40 and 75 */}
        {[40, 75].map((t) => (
          <circle key={t} cx="66" cy="66" r={R} fill="none" stroke="var(--police-buff)" strokeWidth="12" strokeDasharray={`1.5 ${C}`} strokeDashoffset={-(ARC * t) / 100} />
        ))}
        <circle
          className="gauge-arc"
          cx="66"
          cy="66"
          r={R}
          fill="none"
          stroke={INK[level]}
          strokeWidth="10"
          strokeLinecap="round"
          strokeDasharray={`${ARC} ${C}`}
          strokeDashoffset={ARC - (ARC * shown) / 100}
        />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center pt-1">
        <AnimatedNumber value={score} fromZero duration={1100} className="font-display text-[44px] leading-none font-semibold tabular-nums" />
        <span className="mt-1 text-xs font-semibold" style={{ color: INK[level] }}>
          {riskLabel[level]}
        </span>
      </div>
    </div>
  )
}
