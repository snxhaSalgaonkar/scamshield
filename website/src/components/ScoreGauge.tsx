'use client'

import { useEffect, useState } from 'react'
import { AnimatedNumber } from '@/components/AnimatedNumber'
import { bandColor, bandLabel, bandOf } from '@/lib/risk'

// 270° arc, open at the bottom like an instrument dial.
const R = 52
const C = 2 * Math.PI * R
const ARC = C * 0.75

/** Risk score as a dial. Sweeps from 0 once when the report opens. */
export function ScoreGauge({ score }: { score: number }) {
  const [shown, setShown] = useState(0)
  useEffect(() => {
    const t = requestAnimationFrame(() => setShown(score))
    return () => cancelAnimationFrame(t)
  }, [score])
  const band = bandOf(score)

  return (
    <div className="relative size-[132px]" role="img" aria-label={`Risk score ${score} of 100, ${bandLabel[band]}`}>
      <svg viewBox="0 0 132 132" className="size-full -rotate-[225deg]">
        <circle cx="66" cy="66" r={R} fill="none" stroke="var(--police-buff-deep)" strokeOpacity=".55" strokeWidth="10" strokeDasharray={`${ARC} ${C}`} strokeLinecap="round" />
        {/* band edges at 40, 75 and 90 */}
        {[40, 75, 90].map((t) => (
          <circle key={t} cx="66" cy="66" r={R} fill="none" stroke="var(--police-buff)" strokeWidth="12" strokeDasharray={`1.5 ${C}`} strokeDashoffset={-(ARC * t) / 100} />
        ))}
        <circle
          className="gauge-arc"
          cx="66"
          cy="66"
          r={R}
          fill="none"
          stroke={bandColor[band]}
          strokeWidth="10"
          strokeLinecap="round"
          strokeDasharray={`${ARC} ${C}`}
          strokeDashoffset={ARC - (ARC * shown) / 100}
        />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center pt-1">
        <AnimatedNumber value={score} fromZero duration={1100} className="font-display text-[48px] leading-none font-semibold tabular-nums" />
        <span className="mt-1 text-xs font-bold tracking-wider uppercase" style={{ color: bandColor[band] }}>
          {bandLabel[band]}
        </span>
      </div>
    </div>
  )
}
