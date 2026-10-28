'use client'

import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useRouter } from 'next/navigation'
import { createContext, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { toast } from 'sonner'
import { DAY } from '@/lib/analytics'
import { dataSource } from '@/lib/data'
import { formatPhone } from '@/lib/format'
import { scamTypeLabel } from '@/lib/labels'
import { THRESHOLDS } from '@/lib/risk'
import type { RealtimeStatus, Report } from '@/lib/types'

/** Reports of the last 7 days: the shared working set for overview, clusters, map and spikes. */
export const WINDOW_KEY = ['reports', 'window'] as const

const FRESH_MS = 3_000
const WINDOW_REFRESH_MS = 5 * 60_000

export function useWindowReports() {
  return useQuery({
    queryKey: WINDOW_KEY,
    queryFn: () => dataSource.getReports({ from: new Date(Date.now() - 7 * DAY).toISOString() }),
    staleTime: WINDOW_REFRESH_MS,
    refetchInterval: WINDOW_REFRESH_MS,
  })
}

interface RealtimeValue {
  status: RealtimeStatus
  /** Report ids that arrived in the last few seconds (row highlight). */
  freshIds: Set<string>
  lastInsertAt: string | null
}

const RealtimeContext = createContext<RealtimeValue>({ status: 'connecting', freshIds: new Set(), lastInsertAt: null })

export const useRealtime = () => useContext(RealtimeContext)

/**
 * One subscription to `reports` for the whole dashboard. New rows are merged into the cached
 * window (so every page updates without refetching), list queries are refreshed, and a toast is
 * raised for risk ≥ 75.
 */
export function RealtimeProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const router = useRouter()
  const [status, setStatus] = useState<RealtimeStatus>('connecting')
  const [freshIds, setFreshIds] = useState<Set<string>>(new Set())
  const [lastInsertAt, setLastInsertAt] = useState<string | null>(null)
  const timers = useRef<ReturnType<typeof setTimeout>[]>([])
  const routerRef = useRef(router)

  useEffect(() => {
    routerRef.current = router
  })

  useEffect(() => {
    const unsubscribe = dataSource.subscribeToReports((report, type) => {
      queryClient.setQueryData<Report[]>(WINDOW_KEY, (prev) => {
        if (!prev) return prev
        if (type === 'UPDATE') return prev.map((r) => (r.id === report.id ? report : r))
        return prev.some((r) => r.id === report.id) ? prev : [report, ...prev]
      })
      queryClient.setQueryData<Report | null>(['report', report.id], (prev) => (prev === undefined ? prev : report))
      void queryClient.invalidateQueries({ queryKey: ['reports', 'page'] })
      void queryClient.invalidateQueries({ queryKey: ['reports', 'number', report.caller_number] })
      void queryClient.invalidateQueries({ queryKey: ['reports', 'prefix', report.number_prefix] })
      if (type === 'UPDATE') return

      void queryClient.invalidateQueries({ queryKey: ['numbers'] })
      void queryClient.invalidateQueries({ queryKey: ['number', report.caller_number] })
      setLastInsertAt(report.created_at)
      setFreshIds((s) => new Set(s).add(report.id))
      timers.current.push(
        setTimeout(() => {
          setFreshIds((s) => {
            const next = new Set(s)
            next.delete(report.id)
            return next
          })
        }, FRESH_MS),
      )
      if (report.risk_score >= THRESHOLDS.high) {
        toast.error(`Risk ${report.risk_score} · ${scamTypeLabel[report.scam_type]}`, {
          description: `${formatPhone(report.caller_number)}${report.telecom_circle ? ` · ${report.telecom_circle}` : ''}`,
          action: { label: 'Open', onClick: () => routerRef.current.push(`/dashboard/reports/${report.id}`) },
          duration: 8000,
        })
      }
    }, setStatus)
    return () => {
      unsubscribe()
      timers.current.forEach(clearTimeout)
      timers.current = []
    }
  }, [queryClient])

  const value = useMemo(() => ({ status, freshIds, lastInsertAt }), [status, freshIds, lastInsertAt])
  return <RealtimeContext.Provider value={value}>{children}</RealtimeContext.Provider>
}
