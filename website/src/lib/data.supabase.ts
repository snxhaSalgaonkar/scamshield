// DataSource backed by the shared Supabase project. Runs in the officer's browser; RLS lets an
// authority read every report and number, and update `status` only.
import { computeStats, rangeMs } from './analytics'
import { applyFilters } from './filters'
import { supabaseBrowser } from './supabase/client'
import type { DataSource, NumberRecord, RealtimeStatus, Report, ReportFilters } from './types'

/** Upper bound for "all reports in a window" queries (overview, spikes). */
const WINDOW_LIMIT = 5000

function check<T>(res: { data: T | null; error: { message: string } | null }): T {
  if (res.error) throw new Error(res.error.message)
  return res.data as T
}

const sb = () => supabaseBrowser()

async function selectReports(filters: ReportFilters = {}): Promise<Report[]> {
  const q = applyFilters(sb().from('reports').select('*'), filters)
    .order('created_at', { ascending: false })
    .limit(filters.limit ?? WINDOW_LIMIT)
  return check(await q) ?? []
}

export const supabaseDataSource: DataSource = {
  getReports: selectReports,

  async getReportsPage(filters, page, pageSize) {
    const from = page * pageSize
    const res = await applyFilters(sb().from('reports').select('*', { count: 'exact' }), filters)
      .order('created_at', { ascending: false })
      .range(from, from + pageSize - 1)
    return { rows: check(res) ?? [], total: res.count ?? 0 }
  },

  async getReport(id) {
    return check(await sb().from('reports').select('*').eq('id', id).maybeSingle())
  },

  getReportsByNumber: (number) => selectReports({ number }),

  getReportsByPrefix: (prefix) => selectReports({ prefix }),

  async updateReportStatus(id, status) {
    const res = await sb().from('reports').update({ status }).eq('id', id).select('id')
    const rows = check(res)
    if (!rows?.length) throw new Error('Not allowed to update this report (authority role required).')
  },

  async getNumbers(opts = {}) {
    let q = sb().from('numbers').select('*')
    if (opts.flaggedOnly) q = q.eq('is_flagged', true)
    return check(await q.order('reporter_count', { ascending: false }).limit(WINDOW_LIMIT)) ?? []
  },

  async getNumber(number) {
    return check(await sb().from('numbers').select('*').eq('number', number).maybeSingle())
  },

  async getStats(range) {
    const from = new Date(Date.now() - rangeMs[range]).toISOString()
    return computeStats(await selectReports({ from }), range)
  },

  async exportCsv(filters) {
    const qs = new URLSearchParams(Object.entries(filters).filter(([, v]) => v != null && v !== '') as [string, string][])
    const res = await fetch(`/api/export?${qs}`, { credentials: 'same-origin' })
    if (!res.ok) throw new Error(res.status === 403 ? 'Export is for authority accounts only.' : `Export failed (${res.status}).`)
    return res.blob()
  },

  subscribeToReports(onChange, onStatus?: (s: RealtimeStatus) => void) {
    onStatus?.('connecting')
    const channel = sb()
      .channel(`reports-feed-${Math.random().toString(36).slice(2)}`)
      .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'reports' }, (p) => onChange(p.new as Report, 'INSERT'))
      .on('postgres_changes', { event: 'UPDATE', schema: 'public', table: 'reports' }, (p) => onChange(p.new as Report, 'UPDATE'))
      .subscribe((status) => {
        if (status === 'SUBSCRIBED') onStatus?.('live')
        else if (status === 'CHANNEL_ERROR' || status === 'TIMED_OUT' || status === 'CLOSED') onStatus?.('offline')
      })
    return () => {
      void sb().removeChannel(channel)
    }
  },

  subscribeToNumbers(onChange) {
    const channel = sb()
      .channel(`numbers-feed-${Math.random().toString(36).slice(2)}`)
      .on('postgres_changes', { event: '*', schema: 'public', table: 'numbers' }, (p) => {
        if (p.new && 'number' in p.new) onChange(p.new as NumberRecord)
      })
      .subscribe()
    return () => {
      void sb().removeChannel(channel)
    }
  },
}
