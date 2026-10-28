'use client'

import { Minimize2, Siren } from 'lucide-react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect, useMemo, useRef, useState } from 'react'
import { AnimatedNumber } from '@/components/AnimatedNumber'
import { RequireOfficer } from '@/components/DashboardFrame'
import { Wordmark } from '@/components/Wordmark'
import { RealtimeProvider, useRealtime, useWindowReports } from '@/hooks/realtime'
import { useClusters, useNumbers } from '@/hooks/useData'
import { useNow } from '@/hooks/useNow'
import { computeStats, HOUR } from '@/lib/analytics'
import { CIRCLES } from '@/lib/circles'
import { formatPhone, inr } from '@/lib/format'
import { INDIA_PATH, MAP } from '@/lib/indiaOutline'
import { scamTypeShort } from '@/lib/labels'
import { bandLabel, bandOf, THRESHOLDS } from '@/lib/risk'
import type { Report } from '@/lib/types'
import { cn } from '@/lib/utils'

const { W, H, PAD, LON, LAT, KX, SCALE } = MAP
const x = (lon: number) => PAD + (lon - LON[0]) * KX * SCALE
const y = (lat: number) => H - PAD - (lat - LAT[0]) * SCALE

const SIREN_MS = 7000
const LOG_SIZE = 14

function useClock() {
  const [now, setNow] = useState<Date | null>(null)
  useEffect(() => {
    setNow(new Date())
    const t = window.setInterval(() => setNow(new Date()), 1000)
    return () => window.clearInterval(t)
  }, [])
  return now
}

function Readout({ label, children, tone }: { label: string; children: React.ReactNode; tone?: 'red' | 'brass' }) {
  return (
    <div className="min-w-0 px-[clamp(16px,2vw,32px)] py-[clamp(10px,2.2vh,24px)]">
      <p className="truncate text-[clamp(12px,1.6vh,14px)] text-white/55">{label}</p>
      <div className={cn('mt-1 truncate font-display text-[clamp(30px,6.2vh,64px)] leading-none font-semibold tracking-tight tabular-nums', tone === 'red' ? 'text-red-400' : tone === 'brass' ? 'text-brass' : 'text-white')}>
        {children}
      </div>
    </div>
  )
}

/**
 * Full-screen wall display for the control-room projector: live beacons per telecom circle,
 * an incoming log, and the day's numbers. A high-risk report sounds the siren strip.
 */
function Wall() {
  const router = useRouter()
  const clock = useClock()
  const now = useNow(30_000)
  const { data: reports } = useWindowReports()
  const { data: numbers } = useNumbers()
  const clusters = useClusters()
  const { freshIds, status } = useRealtime()
  const [siren, setSiren] = useState<Report | null>(null)
  const seen = useRef<Set<string>>(new Set())

  // Siren for every fresh report with risk ≥ 75.
  useEffect(() => {
    for (const id of freshIds) {
      if (seen.current.has(id)) continue
      seen.current.add(id)
      const r = reports?.find((x) => x.id === id)
      if (r && r.risk_score >= THRESHOLDS.high) setSiren(r)
    }
  }, [freshIds, reports])

  useEffect(() => {
    if (!siren) return
    const t = window.setTimeout(() => setSiren(null), SIREN_MS)
    return () => window.clearTimeout(t)
  }, [siren])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') router.push('/dashboard')
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [router])

  const stats = useMemo(() => (reports ? computeStats(reports, '24h', now) : undefined), [reports, now])
  const activity = useMemo(() => {
    const m = new Map<string, { day: number; hourHigh: boolean }>()
    for (const r of reports ?? []) {
      const age = now - new Date(r.created_at).getTime()
      if (age > 24 * HOUR || !r.telecom_circle) continue
      const e = m.get(r.telecom_circle) ?? { day: 0, hourHigh: false }
      e.day++
      if (age <= HOUR && r.risk_score >= THRESHOLDS.high) e.hourHigh = true
      m.set(r.telecom_circle, e)
    }
    return m
  }, [reports, now])
  const ranking = [...activity.entries()].sort((a, b) => b[1].day - a[1].day)
  const max = ranking[0]?.[1].day ?? 1

  return (
    <div className="wall relative flex h-dvh flex-col overflow-hidden text-white">
      {siren && (
        <div role="alert" className="siren-strip absolute inset-x-0 top-0 z-20 flex h-[clamp(56px,9vh,76px)] items-center gap-5 px-8" data-moving>
          <Siren className="size-8 shrink-0 animate-pulse" aria-hidden />
          <span className="font-display text-[clamp(22px,3.6vh,32px)] font-bold">Risk {siren.risk_score}</span>
          <span className="truncate text-[clamp(15px,2.4vh,21px)] font-medium">
            {scamTypeShort[siren.scam_type]} · {formatPhone(siren.caller_number)} · {siren.telecom_circle ?? 'unknown circle'}
          </span>
          <Link href={`/dashboard/reports/${siren.id}`} className="ml-auto shrink-0 rounded-md bg-white px-4 py-2 text-sm font-semibold text-red-800">
            Open report
          </Link>
        </div>
      )}

      <header className="flex shrink-0 items-center justify-between gap-6 border-b border-white/10 px-8 py-[clamp(10px,2vh,20px)]">
        <Wordmark subtitle="Command wall, cyber crime control room" />
        <div className="flex items-center gap-6">
          <div className="text-right">
            <div className="font-display text-[clamp(24px,4.2vh,36px)] leading-none font-semibold tabular-nums">{clock?.toLocaleTimeString('en-IN', { hour12: false })}</div>
            <div className="mt-1 text-xs text-white/50">{clock?.toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long' })}, IST</div>
          </div>
          <span className="flex items-center gap-2 rounded-full border border-white/15 px-3 py-1.5 text-sm">
            <span className="live-dot" data-status={status} aria-hidden /> {status === 'live' ? 'Live' : 'Connecting'}
          </span>
          <Link href="/dashboard" className="flex size-10 items-center justify-center rounded-md text-white/60 hover:bg-white/10 hover:text-white" aria-label="Leave command wall (Esc)" title="Leave command wall (Esc)">
            <Minimize2 className="size-5" />
          </Link>
        </div>
      </header>

      <div className="grid min-h-0 flex-1 grid-cols-[minmax(0,1.35fr)_minmax(0,1fr)] overflow-hidden">
        <section className="flex min-h-0 flex-col border-r border-white/10 px-8 pt-[clamp(12px,2.4vh,24px)] pb-3">
          <h2 className="font-display text-[clamp(17px,2.6vh,22px)] font-semibold">Reports by telecom circle, last 24 hours</h2>
          <p className="text-[clamp(12px,1.7vh,14px)] text-white/50">Size shows report count. Red means a high-risk report in the last hour. Circle is approximate (number portability).</p>
          <div className="mt-3 grid min-h-0 flex-1 grid-cols-[minmax(0,1.1fr)_minmax(200px,1fr)] gap-8">
            <div className="relative min-h-0">
              <svg viewBox={`0 0 ${W} ${H}`} className="absolute inset-0 h-full w-full" role="img" aria-label="Map of India with report counts per telecom circle">
                <path d={INDIA_PATH} fill="oklch(0.38 0.08 262 / 0.55)" stroke="oklch(0.78 0.12 82 / 0.7)" strokeWidth="1.2" strokeLinejoin="round" />
                {CIRCLES.map((c) => {
                  const e = activity.get(c.name)
                  const r = e ? 6 + Math.sqrt(e.day) * 3.4 : 3
                  const color = e?.hourHigh ? '#f04438' : e ? 'var(--police-brass)' : 'white'
                  return (
                    <g key={c.name}>
                      {e?.hourHigh && <circle className="beacon-ring" cx={x(c.lon)} cy={y(c.lat)} r={r} fill="none" stroke={color} strokeWidth="2" />}
                      <circle cx={x(c.lon)} cy={y(c.lat)} r={r} fill={color} fillOpacity={e ? 0.9 : 0.25} />
                    </g>
                  )
                })}
              </svg>
            </div>
            <ol className="min-h-0 space-y-[clamp(6px,1.3vh,14px)] overflow-hidden py-2">
              {ranking.length === 0 && <li className="text-sm text-white/50">No reports in the last 24 hours.</li>}
              {ranking.map(([circle, e], i) => (
                <li key={circle}>
                  <div className="flex items-baseline justify-between gap-3">
                    <span className="flex items-baseline gap-2.5 truncate">
                      <span className="w-4 text-right font-mono text-xs text-white/35">{i + 1}</span>
                      <span className="truncate text-[clamp(14px,1.9vh,18px)] font-medium">{circle}</span>
                    </span>
                    <span className="font-display text-[clamp(18px,2.6vh,26px)] font-semibold tabular-nums">{e.day}</span>
                  </div>
                  <div className="mt-1 ml-[26px] h-1.5 overflow-hidden rounded-full bg-white/10">
                    <div className={cn('h-full', e.hourHigh ? 'bg-red-500' : 'bg-brass/85')} style={{ width: `${(e.day / max) * 100}%` }} />
                  </div>
                </li>
              ))}
            </ol>
          </div>
        </section>

        <section className="flex min-h-0 flex-col">
          <h2 className="px-6 pt-[clamp(12px,2.4vh,24px)] pb-2 font-display text-[clamp(17px,2.6vh,22px)] font-semibold">Incoming</h2>
          <ol className="min-h-0 flex-1 overflow-hidden px-3">
            {(reports ?? []).slice(0, LOG_SIZE).map((r) => {
              const band = bandOf(r.risk_score)
              return (
                <li key={r.id}>
                  <Link href={`/dashboard/reports/${r.id}`} className={cn('grid grid-cols-[56px_96px_minmax(0,1fr)] items-center gap-3 rounded-md px-3 py-[clamp(5px,1.1vh,10px)] hover:bg-white/5', freshIds.has(r.id) && 'dispatch-in')}>
                    <span className="font-mono text-sm text-white/55 tabular-nums">{new Date(r.created_at).toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', hour12: false })}</span>
                    <span className={cn('inline-flex items-center justify-center gap-1.5 rounded px-2 py-1 text-xs font-semibold', band === 'scam' || band === 'high' ? 'bg-red-500/20 text-red-300' : band === 'warn' ? 'bg-amber-400/15 text-amber-300' : 'bg-emerald-400/10 text-emerald-300')}>
                      <span className="font-display text-base tabular-nums">{r.risk_score}</span> {bandLabel[band]}
                    </span>
                    <span className="min-w-0">
                      <span className="block truncate text-[clamp(14px,1.9vh,16px)] font-medium">
                        {scamTypeShort[r.scam_type]} <span className="text-white/45">in</span> {r.telecom_circle ?? 'unknown'}
                      </span>
                      <span className="block truncate font-mono text-xs text-white/40">{formatPhone(r.caller_number)}</span>
                    </span>
                  </Link>
                </li>
              )
            })}
          </ol>
        </section>
      </div>

      <footer className="grid shrink-0 grid-cols-5 divide-x divide-white/10 border-t border-white/10 bg-black/15">
        <Readout label="Reports, last 24 h">{stats ? <AnimatedNumber value={stats.total} /> : '–'}</Readout>
        <Readout label="Scam calls disconnected" tone="red">
          {stats ? <AnimatedNumber value={stats.disconnected} /> : '–'}
        </Readout>
        <Readout label="Numbers flagged" tone="brass">
          {numbers ? <AnimatedNumber value={numbers.filter((n) => n.is_flagged).length} /> : '–'}
        </Readout>
        <Readout label="Active clusters">{clusters ? <AnimatedNumber value={clusters.length} /> : '–'}</Readout>
        <Readout label="Money protected (estimate)">{stats ? <AnimatedNumber value={stats.moneyProtectedEstimate} format={(n) => inr.format(n)} /> : '–'}</Readout>
      </footer>
    </div>
  )
}

export default function CommandWallPage() {
  return (
    <RequireOfficer fallback={<div className="wall h-dvh" />}>
      <RealtimeProvider>
        <Wall />
      </RealtimeProvider>
    </RequireOfficer>
  )
}
