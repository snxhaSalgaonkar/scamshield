import { Minimize2, PhoneIncoming, Siren } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { AnimatedNumber } from '@/components/AnimatedNumber'
import { Wordmark } from '@/components/Wordmark'
import { useAlertFeed } from '@/hooks/useAlertFeed'
import { useNow } from '@/hooks/useNow'
import { useStats } from '@/hooks/useStats'
import { dataSource, isMock } from '@/lib/data'
import { AVG_LOSS_PREVENTED_INR } from '@/lib/estimate'
import { inr } from '@/lib/format'
import { INDIA_PATH, MAP } from '@/lib/indiaOutline'
import { riskLabel, riskLevel } from '@/lib/risk'
import type { Alert } from '@/lib/types'
import { cn } from '@/lib/utils'

// --- map geometry -----------------------------------------------------------
// India outline per the Survey of India boundary (see lib/indiaOutline.ts), with
// cities placed using the same equirectangular projection.

const { W, H, PAD, LON, LAT, KX, SCALE } = MAP
const x = (lon: number) => PAD + (lon - LON[0]) * KX * SCALE
const y = (lat: number) => H - PAD - (lat - LAT[0]) * SCALE

const CITIES: { name: string; lon: number; lat: number; label: 'l' | 'r' }[] = [
  { name: 'Srinagar', lon: 74.8, lat: 34.08, label: 'r' },
  { name: 'Delhi NCR', lon: 77.1, lat: 28.6, label: 'r' },
  { name: 'Chandigarh', lon: 76.78, lat: 30.73, label: 'l' },
  { name: 'Jaipur', lon: 75.79, lat: 26.91, label: 'l' },
  { name: 'Lucknow', lon: 80.95, lat: 26.85, label: 'r' },
  { name: 'Patna', lon: 85.14, lat: 25.59, label: 'r' },
  { name: 'Guwahati', lon: 91.74, lat: 26.14, label: 'l' },
  { name: 'Kolkata', lon: 88.36, lat: 22.57, label: 'r' },
  { name: 'Ahmedabad', lon: 72.57, lat: 23.02, label: 'l' },
  { name: 'Bhopal', lon: 77.41, lat: 23.26, label: 'r' },
  { name: 'Nagpur', lon: 79.09, lat: 21.15, label: 'r' },
  { name: 'Mumbai', lon: 72.88, lat: 19.08, label: 'l' },
  { name: 'Pune', lon: 73.86, lat: 18.52, label: 'r' },
  { name: 'Hyderabad', lon: 78.49, lat: 17.39, label: 'r' },
  { name: 'Bengaluru', lon: 77.59, lat: 12.97, label: 'l' },
  { name: 'Chennai', lon: 80.27, lat: 13.08, label: 'r' },
  { name: 'Kochi', lon: 76.27, lat: 9.93, label: 'l' },
]

const HOUR = 3_600_000
const SIREN_MS = 7000
const LOG_SIZE = 14

interface CityActivity {
  day: number
  hour: number
  red: number
  hourRed: boolean
}

function useClock() {
  const [now, setNow] = useState(() => new Date())
  useEffect(() => {
    const t = window.setInterval(() => setNow(new Date()), 1000)
    return () => window.clearInterval(t)
  }, [])
  return now
}

function useCityActivity(alerts: Alert[], now: number) {
  return useMemo(() => {
    const m = new Map<string, CityActivity>()
    for (const a of alerts) {
      const age = now - new Date(a.createdAt).getTime()
      if (age > 24 * HOUR) continue
      const e = m.get(a.region) ?? { day: 0, hour: 0, red: 0, hourRed: false }
      const red = riskLevel(a.score) === 'red'
      e.day += 1
      if (red) e.red += 1
      if (age <= HOUR) {
        e.hour += 1
        if (red) e.hourRed = true
      }
      m.set(a.region, e)
    }
    return m
  }, [alerts, now])
}

function RegionPlot({ activity, flare }: { activity: Map<string, CityActivity>; flare: { region: string; key: string } | null }) {
  return (
    <svg viewBox={`0 0 ${W} ${H}`} className="h-full w-full" role="img" aria-label="Map of India showing alerts by city in the last 24 hours">
      {[70, 75, 80, 85, 90, 95].map((lon) => (
        <line key={lon} x1={x(lon)} x2={x(lon)} y1={PAD} y2={H - PAD} stroke="white" strokeOpacity=".05" />
      ))}
      {[10, 15, 20, 25, 30, 35].map((lat) => (
        <line key={lat} x1={PAD} x2={W - PAD} y1={y(lat)} y2={y(lat)} stroke="white" strokeOpacity=".05" />
      ))}
      <path
        d={INDIA_PATH}
        fill="oklch(0.38 0.08 262 / 0.55)"
        stroke="oklch(0.78 0.12 82 / 0.7)"
        strokeWidth="1.2"
        strokeLinejoin="round"
      />

      {CITIES.map((c) => {
        const e = activity.get(c.name)
        const cx = x(c.lon)
        const cy = y(c.lat)
        const r = e ? 6 + Math.sqrt(e.day) * 3.4 : 3
        const color = e?.hourRed ? '#f04438' : e ? 'var(--police-brass)' : 'white'
        const tx = c.label === 'r' ? cx + r + 7 : cx - r - 7
        return (
          <g key={c.name}>
            {e && e.hour > 0 && <circle className="beacon-ring" cx={cx} cy={cy} r={r} fill="none" stroke={color} strokeWidth="2" />}
            {flare?.region === c.name && (
              <circle key={flare.key} className="beacon-flare" cx={cx} cy={cy} r={r} fill="none" stroke="#f04438" strokeWidth="3" />
            )}
            <circle cx={cx} cy={cy} r={r} fill={color} fillOpacity={e ? 0.9 : 0.28} />
            {e && <circle cx={cx} cy={cy} r={r} fill="none" stroke="var(--police-navy-deep)" strokeWidth="2" strokeOpacity=".7" />}
            <text
              x={tx}
              y={cy}
              textAnchor={c.label === 'r' ? 'start' : 'end'}
              dominantBaseline="central"
              fill="white"
              fillOpacity={e ? 1 : 0}
              stroke="var(--police-navy-deep)"
              strokeWidth="4"
              strokeOpacity={e ? 0.85 : 0}
              paintOrder="stroke"
              fontSize={19}
              fontWeight={600}
              className="font-display"
            >
              {c.name}
            </text>
          </g>
        )
      })}
    </svg>
  )
}

/** Ranked list beside the plot: the same numbers, readable from the back of the room. */
function CityRanking({ activity }: { activity: Map<string, CityActivity> }) {
  const rows = [...activity.entries()].sort((a, b) => b[1].day - a[1].day)
  const max = rows[0]?.[1].day ?? 1
  if (!rows.length) return <p className="text-sm text-white/50">No alerts in the last 24 hours.</p>
  return (
    <ol className="space-y-[clamp(6px,1.3vh,14px)]">
      {rows.map(([city, e], i) => (
        <li key={city}>
          <div className="flex items-baseline justify-between gap-3">
            <span className="flex items-baseline gap-2.5 truncate">
              <span className="w-4 text-right font-mono text-xs text-white/35 tabular-nums">{i + 1}</span>
              <span className="truncate text-[clamp(14px,1.9vh,18px)] font-medium">{city}</span>
              {e.hourRed && (
                <span className="rounded-sm bg-red-500/20 px-1.5 py-px text-[11px] font-semibold text-red-300">Red flag this hour</span>
              )}
            </span>
            <span className="font-display text-[clamp(18px,2.6vh,26px)] font-semibold tabular-nums">{e.day}</span>
          </div>
          <div className="mt-1 ml-[26px] flex h-1.5 overflow-hidden rounded-full bg-white/8">
            <div className="h-full bg-red-500/85" style={{ width: `${(e.red / max) * 100}%` }} />
            <div className="h-full bg-brass/85" style={{ width: `${((e.day - e.red) / max) * 100}%` }} />
          </div>
        </li>
      ))}
    </ol>
  )
}

function Readout({ label, children, tone }: { label: string; children: React.ReactNode; tone?: 'red' | 'brass' }) {
  return (
    <div className="min-w-0 px-[clamp(16px,2vw,32px)] py-[clamp(10px,2.2vh,24px)]">
      <p className="truncate text-[clamp(12px,1.6vh,14px)] text-white/55">{label}</p>
      <div
        className={cn(
          'mt-1 truncate font-display text-[clamp(30px,6.2vh,64px)] leading-none font-semibold tracking-tight tabular-nums',
          tone === 'red' ? 'text-red-400' : tone === 'brass' ? 'text-brass' : 'text-white',
        )}
      >
        {children}
      </div>
    </div>
  )
}

/**
 * Full-screen wall display for the control-room projector: live incident beacons by city,
 * a dispatch log, and the shift's numbers. A red flag sounds the siren strip.
 * Sized entirely in viewport units so it fits one screen at any resolution or zoom.
 */
export function CommandWallPage() {
  const navigate = useNavigate()
  const clock = useClock()
  const now = useNow(30_000)
  const { data: stats } = useStats()
  const [siren, setSiren] = useState<Alert | null>(null)
  const [flare, setFlare] = useState<{ region: string; key: string } | null>(null)

  const onAlert = useCallback((a: Alert) => {
    setFlare({ region: a.region, key: a.id })
    if (riskLevel(a.score) === 'red') setSiren(a)
  }, [])
  const { alerts, freshIds } = useAlertFeed({ onAlert })
  const activity = useCityActivity(alerts ?? [], now)

  useEffect(() => {
    if (!siren) return
    const t = window.setTimeout(() => setSiren(null), SIREN_MS)
    return () => window.clearTimeout(t)
  }, [siren])

  // Esc leaves the wall; S simulates a call in demo mode (no visible chrome needed on the projector).
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') navigate('/dashboard')
      if ((e.key === 's' || e.key === 'S') && isMock) dataSource.simulateAlert?.()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [navigate])

  const escalated12h = useMemo(
    () => alerts?.filter((a) => a.status === 'escalated' && now - new Date(a.createdAt).getTime() <= 12 * HOUR).length ?? 0,
    [alerts, now],
  )

  const log = alerts?.slice(0, LOG_SIZE) ?? []

  return (
    <div className="wall relative flex h-dvh flex-col overflow-hidden text-white">
      {siren && (
        <div
          role="alert"
          className="siren-strip absolute inset-x-0 top-0 z-20 flex h-[clamp(56px,9vh,76px)] items-center gap-5 px-8 shadow-[0_8px_30px_rgb(180_35_24/0.45)]"
          data-moving
        >
          <Siren className="size-8 shrink-0 animate-pulse" aria-hidden />
          <div className="flex min-w-0 items-baseline gap-4">
            <span className="font-display text-[clamp(22px,3.6vh,32px)] font-bold">Red flag</span>
            <span className="truncate text-[clamp(15px,2.4vh,21px)] font-medium">
              {siren.scamType} in {siren.region}, caller {siren.callerPrefix}, score {siren.score}
            </span>
          </div>
          <Link
            to={`/dashboard/cases/${siren.id}`}
            className="ml-auto shrink-0 rounded-md bg-white px-4 py-2 text-sm font-semibold text-red-800 hover:bg-red-50"
          >
            Open case
          </Link>
        </div>
      )}

      <header className="flex shrink-0 items-center justify-between gap-6 border-b border-white/10 px-8 py-[clamp(10px,2vh,20px)]">
        <Wordmark subtitle="Command wall, cyber crime control room" />
        <div className="flex items-center gap-6">
          <div className="text-right">
            <div className="font-display text-[clamp(24px,4.2vh,36px)] leading-none font-semibold tabular-nums">
              {clock.toLocaleTimeString('en-IN', { hour12: false })}
            </div>
            <div className="mt-1 text-xs text-white/50">
              {clock.toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long' })}, IST
            </div>
          </div>
          <span className="flex items-center gap-2 rounded-full border border-white/15 px-3 py-1.5 text-sm">
            <span className="live-dot" aria-hidden /> Live
          </span>
          <Link
            to="/dashboard"
            className="flex size-10 items-center justify-center rounded-md text-white/60 hover:bg-white/10 hover:text-white"
            aria-label="Leave command wall (Esc)"
            title="Leave command wall (Esc)"
          >
            <Minimize2 className="size-5" />
          </Link>
        </div>
      </header>

      <div className="grid min-h-0 flex-1 grid-cols-[minmax(0,1.35fr)_minmax(0,1fr)] grid-rows-[minmax(0,1fr)] overflow-hidden">
        {/* Hotspots */}
        <section className="flex min-h-0 flex-col border-r border-white/10 px-8 pt-[clamp(12px,2.4vh,24px)] pb-3">
          <h2 className="font-display text-[clamp(17px,2.6vh,22px)] font-semibold">Incidents by city, last 24 hours</h2>
          <p className="text-[clamp(12px,1.7vh,14px)] text-white/50">
            Size shows alert count. Red means a red flag in the last hour. Rings pulse while a city is active. Boundary as per Survey of India.
          </p>
          <div className="mt-3 grid min-h-0 flex-1 grid-cols-[minmax(0,1.1fr)_minmax(200px,1fr)] grid-rows-[minmax(0,1fr)] gap-8">
            {/* Absolutely placed so the SVG never sizes the row from its own aspect ratio */}
            <div className="relative min-h-0">
              <div className="absolute inset-0">
                <RegionPlot activity={activity} flare={flare} />
              </div>
            </div>
            <div className="min-h-0 overflow-hidden py-2 [mask-image:linear-gradient(to_bottom,black_85%,transparent)]">
              <p className="mb-3 text-[clamp(12px,1.6vh,13px)] text-white/45">Most active, with red flags in red</p>
              <CityRanking activity={activity} />
            </div>
          </div>
        </section>

        {/* Dispatch log */}
        <section className="flex min-h-0 flex-col">
          <div className="flex items-baseline justify-between px-6 pt-[clamp(12px,2.4vh,24px)] pb-2">
            <h2 className="font-display text-[clamp(17px,2.6vh,22px)] font-semibold">Incoming</h2>
            {isMock && (
              <button
                onClick={() => dataSource.simulateAlert?.()}
                className="flex items-center gap-1.5 rounded px-2 py-1 text-xs text-white/40 hover:bg-white/10 hover:text-white/80"
                title="Simulate a scam call (S)"
              >
                <PhoneIncoming className="size-3.5" aria-hidden /> Simulate
              </button>
            )}
          </div>
          <ol className="min-h-0 flex-1 overflow-hidden px-3 [mask-image:linear-gradient(to_bottom,black_80%,transparent)]">
            {log.map((a) => {
              const level = riskLevel(a.score)
              return (
                <li key={a.id}>
                  <Link
                    to={`/dashboard/cases/${a.id}`}
                    className={cn(
                      'grid grid-cols-[56px_88px_minmax(0,1fr)_auto] items-center gap-3 rounded-md px-3 py-[clamp(5px,1.1vh,10px)] hover:bg-white/5',
                      freshIds.has(a.id) && 'dispatch-in',
                    )}
                  >
                    <span className="font-mono text-sm text-white/55 tabular-nums">
                      {new Date(a.createdAt).toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', hour12: false })}
                    </span>
                    <span
                      className={cn(
                        'inline-flex items-center justify-center gap-1.5 rounded px-2 py-1 text-xs font-semibold',
                        level === 'red' && 'bg-red-500/20 text-red-300',
                        level === 'warning' && 'bg-amber-400/15 text-amber-300',
                        level === 'silent' && 'bg-emerald-400/10 text-emerald-300',
                      )}
                    >
                      <span className="font-display text-base tabular-nums">{a.score}</span> {riskLabel[level]}
                    </span>
                    <span className="min-w-0">
                      <span className="block truncate text-[clamp(14px,1.9vh,16px)] font-medium">
                        {a.scamType} <span className="text-white/45">in</span> {a.region}
                      </span>
                      <span className="block truncate font-mono text-xs text-white/40">{a.callerPrefix}</span>
                    </span>
                    <span className="text-xs text-white/40">{a.status === 'new' ? 'Unreviewed' : ''}</span>
                  </Link>
                </li>
              )
            })}
          </ol>
        </section>
      </div>

      <footer className="grid shrink-0 grid-cols-5 divide-x divide-white/10 border-t border-white/10 bg-black/15">
        {stats ? (
          <>
            <Readout label="Alerts, last 12 hours">
              <AnimatedNumber value={stats.totalAlerts} fromZero duration={1400} />
            </Readout>
            <Readout label="Red flags" tone="red">
              <AnimatedNumber value={stats.redFlags} fromZero duration={1400} />
            </Readout>
            <Readout label="Awaiting review" tone="brass">
              <AnimatedNumber value={stats.awaitingReview} fromZero duration={1400} />
            </Readout>
            <Readout label="Escalated, last 12 hours">
              <AnimatedNumber value={escalated12h} fromZero duration={1400} />
            </Readout>
            <Readout label="Loss possibly averted (estimate)">
              <AnimatedNumber value={stats.redFlags * AVG_LOSS_PREVENTED_INR} format={(n) => inr.format(n)} fromZero duration={1400} />
            </Readout>
          </>
        ) : (
          <div className="col-span-5 h-[clamp(70px,13vh,136px)]" />
        )}
      </footer>
    </div>
  )
}
