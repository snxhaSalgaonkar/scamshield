import { useCallback, useEffect, useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { dataSource } from '@/lib/data'
import type { Alert } from '@/lib/types'

const FRESH_MS = 6000

interface Options {
  callerHash?: string
  /** Side effects for every incoming alert (toasts, sounds), paused or not. */
  onAlert?: (a: Alert) => void
}

/**
 * Alerts list kept live by subscribeToAlerts.
 * - `freshIds` marks rows that arrived after first load (for the slide-in highlight).
 * - While `paused`, new alerts queue in `pending` so the table doesn't shift under an officer reading it.
 */
export function useAlertFeed({ callerHash, onAlert }: Options = {}) {
  const queryClient = useQueryClient()
  const queryKey = ['alerts', callerHash ?? null] as const
  const [freshIds, setFreshIds] = useState<Set<string>>(new Set())
  const [paused, setPaused] = useState(false)
  const [pending, setPending] = useState<Alert[]>([])
  const [lastAlertAt, setLastAlertAt] = useState<string | null>(null)
  const pausedRef = useRef(paused)
  const onAlertRef = useRef(onAlert)
  const timers = useRef<number[]>([])

  useEffect(() => {
    pausedRef.current = paused
    onAlertRef.current = onAlert
  })

  const query = useQuery({
    queryKey,
    queryFn: () => dataSource.getAlerts({ callerHash }),
  })

  const reveal = useCallback(
    (incoming: Alert[]) => {
      if (!incoming.length) return
      queryClient.setQueryData<Alert[]>(['alerts', callerHash ?? null], (prev) => [...incoming, ...(prev ?? [])])
      setFreshIds((prev) => {
        const next = new Set(prev)
        incoming.forEach((a) => next.add(a.id))
        return next
      })
      timers.current.push(
        window.setTimeout(() => {
          setFreshIds((prev) => {
            const next = new Set(prev)
            incoming.forEach((a) => next.delete(a.id))
            return next
          })
        }, FRESH_MS),
      )
    },
    [callerHash, queryClient],
  )

  useEffect(() => {
    const unsubscribe = dataSource.subscribeToAlerts((alert) => {
      if (callerHash && alert.callerHash !== callerHash) return
      queryClient.invalidateQueries({ queryKey: ['stats'] })
      queryClient.invalidateQueries({ queryKey: ['numbers'] })
      setLastAlertAt(alert.createdAt)
      onAlertRef.current?.(alert)
      if (pausedRef.current) setPending((p) => [alert, ...p])
      else reveal([alert])
    })
    return () => {
      unsubscribe()
      timers.current.forEach(window.clearTimeout)
      timers.current = []
    }
  }, [callerHash, queryClient, reveal])

  const resume = useCallback(() => {
    setPaused(false)
    reveal(pending)
    setPending([])
  }, [pending, reveal])

  return {
    alerts: query.data,
    isLoading: query.isLoading,
    freshIds,
    paused,
    pause: () => setPaused(true),
    resume,
    pending,
    lastAlertAt,
  }
}
