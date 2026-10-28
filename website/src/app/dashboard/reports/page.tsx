'use client'

import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight, Download, Search, X } from 'lucide-react'
import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { Suspense, useEffect, useMemo, useState } from 'react'
import { toast } from 'sonner'
import { FilterSelect } from '@/components/FilterSelect'
import { ReportsTable } from '@/components/ReportsTable'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { useRealtime } from '@/hooks/realtime'
import { useTriage } from '@/hooks/useTriage'
import { CIRCLE_NAMES } from '@/lib/circles'
import { csvFilename } from '@/lib/csv'
import { dataSource } from '@/lib/data'
import { filtersFromParams, filtersToParams } from '@/lib/filters'
import { digitsOf } from '@/lib/format'
import { detectedViaLabel, scamTypeLabel } from '@/lib/labels'
import { bandLabel, statusLabel } from '@/lib/risk'
import { BANDS, DETECTED_VIA, REPORT_STATUSES, SCAM_TYPES, type ReportFilters } from '@/lib/types'

const PAGE_SIZE = 50
const ALL = 'all'

const opts = <T extends string>(values: readonly T[], label: (v: T) => string) => [
  { value: ALL, label: 'All' },
  ...values.map((v) => ({ value: v, label: label(v) })),
]
const TYPE_ITEMS = opts(SCAM_TYPES, (t) => scamTypeLabel[t])
const BAND_ITEMS = opts(BANDS, (b) => bandLabel[b])
const STATUS_ITEMS = opts(REPORT_STATUSES, (s) => statusLabel[s])
const VIA_ITEMS = opts(DETECTED_VIA, (v) => detectedViaLabel[v])
const CIRCLE_ITEMS = opts(CIRCLE_NAMES, (c) => c)

/** yyyy-mm-dd (local) ↔ ISO bounds: `from` = start of that day, `to` = start of the next day. */
const dayToIso = (day: string, next = false) => {
  const d = new Date(`${day}T00:00:00`)
  if (next) d.setDate(d.getDate() + 1)
  return d.toISOString()
}
const isoToDay = (iso: string | undefined, prev = false) => {
  if (!iso) return ''
  const d = new Date(iso)
  if (prev) d.setDate(d.getDate() - 1)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function ReportsView() {
  const router = useRouter()
  const pathname = usePathname()
  const params = useSearchParams()
  const filters = useMemo(() => filtersFromParams(new URLSearchParams(params.toString())), [params])
  const page = Math.max(0, Number(params.get('page') ?? '0') || 0)
  const [numberInput, setNumberInput] = useState(filters.number ?? '')
  const [exporting, setExporting] = useState(false)
  const { freshIds } = useRealtime()
  const triage = useTriage()

  useEffect(() => setNumberInput(filters.number ?? ''), [filters.number])

  const { data, isFetching, error } = useQuery({
    queryKey: ['reports', 'page', filters, page],
    queryFn: () => dataSource.getReportsPage(filters, page, PAGE_SIZE),
    placeholderData: keepPreviousData,
  })

  const setFilters = (next: ReportFilters, nextPage = 0) => {
    const p = filtersToParams(next)
    if (nextPage) p.set('page', String(nextPage))
    router.replace(`${pathname}${p.size ? `?${p}` : ''}`, { scroll: false })
  }
  const set = <K extends keyof ReportFilters>(key: K, value: ReportFilters[K] | typeof ALL | '') =>
    setFilters({ ...filters, [key]: value === ALL || value === '' ? undefined : value })

  const active = Object.values(filters).some(Boolean)
  const pages = data ? Math.max(1, Math.ceil(data.total / PAGE_SIZE)) : 1

  async function exportCsv() {
    setExporting(true)
    try {
      const blob = await dataSource.exportCsv(filters)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = csvFilename()
      a.click()
      URL.revokeObjectURL(url)
      toast.success('CSV exported')
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Export failed')
    } finally {
      setExporting(false)
    }
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-display text-[32px] leading-tight font-semibold tracking-tight text-navy-deep">Reports</h1>
          <p className="text-[15px] text-muted-foreground">Every report filed by citizens&apos; phones. Filters stay in the URL, so a view can be shared.</p>
        </div>
        <Button variant="outline" onClick={exportCsv} disabled={exporting || !data?.total}>
          <Download className="size-4" aria-hidden />
          {exporting ? 'Exporting…' : `Export CSV${data ? ` (${data.total})` : ''}`}
        </Button>
      </div>

      <section className="surface space-y-3 p-4" aria-label="Filters">
        <div className="flex flex-wrap items-center gap-2">
          <label className="flex items-center gap-1.5 text-sm">
            <span className="text-muted-foreground">From</span>
            <input
              type="date"
              value={isoToDay(filters.from)}
              onChange={(e) => set('from', e.target.value ? dayToIso(e.target.value) : undefined)}
              className="h-7 rounded-lg border border-input bg-transparent px-2 text-sm"
            />
          </label>
          <label className="flex items-center gap-1.5 text-sm">
            <span className="text-muted-foreground">To</span>
            <input
              type="date"
              value={isoToDay(filters.to, true)}
              onChange={(e) => set('to', e.target.value ? dayToIso(e.target.value, true) : undefined)}
              className="h-7 rounded-lg border border-input bg-transparent px-2 text-sm"
            />
          </label>
          <FilterSelect label="Scam type" value={filters.scamType ?? ALL} items={TYPE_ITEMS} onChange={(v) => set('scamType', v as ReportFilters['scamType'])} />
          <FilterSelect label="Band" value={filters.band ?? ALL} items={BAND_ITEMS} onChange={(v) => set('band', v as ReportFilters['band'])} />
          <FilterSelect label="Status" value={filters.status ?? ALL} items={STATUS_ITEMS} onChange={(v) => set('status', v as ReportFilters['status'])} />
          <FilterSelect label="Detected via" value={filters.detectedVia ?? ALL} items={VIA_ITEMS} onChange={(v) => set('detectedVia', v as ReportFilters['detectedVia'])} />
          <FilterSelect label="Circle" value={filters.circle ?? ALL} items={CIRCLE_ITEMS} onChange={(v) => set('circle', v)} />
          <form
            className="relative"
            onSubmit={(e) => {
              e.preventDefault()
              set('number', digitsOf(numberInput))
            }}
          >
            <Search className="pointer-events-none absolute top-1/2 left-2.5 size-3.5 -translate-y-1/2 text-muted-foreground" aria-hidden />
            <input
              aria-label="Search caller number"
              value={numberInput}
              onChange={(e) => setNumberInput(e.target.value)}
              onBlur={() => digitsOf(numberInput) !== (filters.number ?? '') && set('number', digitsOf(numberInput))}
              placeholder="Number, e.g. 98112"
              inputMode="tel"
              className="h-7 w-48 rounded-lg border border-input bg-transparent pr-2 pl-8 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring/50"
            />
          </form>
          {filters.prefix && (
            <Button variant="outline" size="sm" onClick={() => set('prefix', undefined)}>
              Series {filters.prefix}-XXXXX <X className="size-3.5" aria-hidden />
            </Button>
          )}
          {active && (
            <Button variant="ghost" size="sm" onClick={() => setFilters({})}>
              Clear all
            </Button>
          )}
        </div>
      </section>

      <section className="surface overflow-hidden" aria-label="Reports" aria-busy={isFetching}>
        {error ? (
          <p className="p-6 text-sm text-red-800">Could not load reports: {error instanceof Error ? error.message : 'unknown error'}</p>
        ) : (
          <ReportsTable
            reports={data?.rows}
            freshIds={freshIds}
            onTriage={triage}
            showCircle
            emptyText={active ? 'No reports match these filters.' : 'No reports yet.'}
          />
        )}
        <div className="flex items-center justify-between border-t border-border px-4 py-2.5 text-sm text-muted-foreground">
          <span className="tabular-nums">
            {data ? (data.total ? `${page * PAGE_SIZE + 1}–${Math.min(data.total, (page + 1) * PAGE_SIZE)} of ${data.total}` : '0 reports') : <Skeleton className="h-4 w-24" />}
          </span>
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setFilters(filters, page - 1)}>
              <ChevronLeft className="size-3.5" aria-hidden /> Previous
            </Button>
            <span className="tabular-nums">
              Page {page + 1} of {pages}
            </span>
            <Button variant="outline" size="sm" disabled={page + 1 >= pages} onClick={() => setFilters(filters, page + 1)}>
              Next <ChevronRight className="size-3.5" aria-hidden />
            </Button>
          </div>
        </div>
      </section>
    </div>
  )
}

export default function ReportsPage() {
  return (
    <Suspense fallback={<Skeleton className="h-96 w-full" />}>
      <ReportsView />
    </Suspense>
  )
}
