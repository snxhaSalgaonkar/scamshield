'use client'

import { useQuery } from '@tanstack/react-query'
import { useMemo } from 'react'
import { detectSpikes, groupClusters } from '@/lib/analytics'
import { dataSource } from '@/lib/data'
import { useWindowReports } from './realtime'
import { useNow } from './useNow'

export function useNumbers() {
  return useQuery({ queryKey: ['numbers'], queryFn: () => dataSource.getNumbers() })
}

export function useNumberSeries() {
  return useQuery({ queryKey: ['number_series'], queryFn: () => dataSource.getNumberSeries(), staleTime: Infinity })
}

/** Number-series clusters over the live 7-day window. */
export function useClusters() {
  const { data: reports } = useWindowReports()
  const { data: series } = useNumberSeries()
  const now = useNow(60_000)
  return useMemo(() => (reports ? groupClusters(reports, series ?? [], now) : undefined), [reports, series, now])
}

/** Active spikes, recomputed on every realtime report and every 30 s (the window allows reports up to 60 s past `now`). */
export function useSpikes() {
  const { data: reports } = useWindowReports()
  const now = useNow(30_000)
  return useMemo(() => (reports ? detectSpikes(reports, now) : []), [reports, now])
}
