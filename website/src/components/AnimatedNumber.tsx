'use client'

import { useEffect, useRef, useState } from 'react'

const reducedMotion = () => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches

interface Props {
  value: number
  format?: (n: number) => string
  duration?: number
  /** Count up from 0 on mount (the wall and the case gauge); otherwise only animate changes. */
  fromZero?: boolean
  className?: string
}

/** Rolls to a new value instead of jumping, so a changed readout is noticed. */
export function AnimatedNumber({ value, format = String, duration = 700, fromZero = false, className }: Props) {
  const [display, setDisplay] = useState(fromZero ? 0 : value)
  const current = useRef(fromZero ? 0 : value)

  useEffect(() => {
    if (reducedMotion() || current.current === value) {
      current.current = value
      setDisplay(value)
      return
    }
    const from = current.current
    const start = performance.now()
    let frame = 0
    const tick = (now: number) => {
      const t = Math.min(1, (now - start) / duration)
      const eased = 1 - Math.pow(1 - t, 3)
      current.current = from + (value - from) * eased
      setDisplay(current.current)
      if (t < 1) frame = requestAnimationFrame(tick)
    }
    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [value, duration])

  return <span className={className ?? 'tabular-nums'}>{format(Math.round(display))}</span>
}
