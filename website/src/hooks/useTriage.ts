'use client'

import { useQueryClient } from '@tanstack/react-query'
import { useCallback } from 'react'
import { toast } from 'sonner'
import { dataSource } from '@/lib/data'
import { caseRef } from '@/lib/format'
import { statusLabel } from '@/lib/risk'
import type { Report, ReportStatus } from '@/lib/types'
import { WINDOW_KEY } from './realtime'

/** Optimistic status change with undo; rolls back and toasts on failure. */
export function useTriage() {
  const queryClient = useQueryClient()

  const patch = useCallback(
    (id: string, status: ReportStatus) => {
      const set = (r: Report) => (r.id === id ? { ...r, status } : r)
      queryClient.setQueryData<Report[]>(WINDOW_KEY, (rows) => rows?.map(set))
      queryClient.setQueriesData<{ rows: Report[]; total: number }>({ queryKey: ['reports', 'page'] }, (p) => (p ? { ...p, rows: p.rows.map(set) } : p))
      queryClient.setQueryData<Report | null>(['report', id], (r) => (r ? set(r) : r))
    },
    [queryClient],
  )

  const triage = useCallback(
    async (r: Report, next: ReportStatus, opts: { undo?: boolean } = { undo: true }): Promise<boolean> => {
      const prev = r.status
      patch(r.id, next)
      try {
        await dataSource.updateReportStatus(r.id, next)
        toast.success(`${caseRef(r)} marked ${statusLabel[next].toLowerCase()}`, {
          action: opts.undo ? { label: 'Undo', onClick: () => void triage({ ...r, status: next }, prev, { undo: false }) } : undefined,
        })
        return true
      } catch (e) {
        patch(r.id, prev)
        toast.error(e instanceof Error ? e.message : `Could not update ${caseRef(r)}. Try again.`)
        return false
      }
    },
    [patch],
  )

  return triage
}
