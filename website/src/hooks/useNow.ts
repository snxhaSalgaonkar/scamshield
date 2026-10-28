'use client'

import { useEffect, useState } from 'react'

/** Re-renders every `intervalMs` so relative times ("4 min ago") and time windows stay current. */
export function useNow(intervalMs = 30_000) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const t = window.setInterval(() => setNow(Date.now()), intervalMs)
    return () => window.clearInterval(t)
  }, [intervalMs])
  return now
}
