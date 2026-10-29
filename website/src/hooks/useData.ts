'use client'

import { useQuery } from '@tanstack/react-query'
import { useMemo } from 'react'
import { detectSpikes } from '@/lib/analytics'
import { dataSource } from '@/lib/data'
import { useWindowReports } from './realtime'
import { useNow } from './useNow'

export function useNumbers() {
  return useQuery({ queryKey: ['numbers'], queryFn: () => dataSource.getNumbers() })
}

/** Active spikes, recomputed on every realtime report and every 30 s (the window allows reports up to 60 s past `now`). */
export function useSpikes() {
  const { data: reports } = useWindowReports()
  const now = useNow(30_000)
  return useMemo(() => (reports ? detectSpikes(reports, now) : []), [reports, now])
}
