import { LogOut, MonitorPlay, Search } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { CaseFinder } from '@/components/CaseFinder'
import { Wordmark } from '@/components/Wordmark'
import { useAuth } from '@/lib/auth'
import { isMock } from '@/lib/data'
import { cn } from '@/lib/utils'

const NAV = [
  { to: '/dashboard', label: 'Live feed', end: true },
  { to: '/dashboard/numbers', label: 'Flagged numbers' },
  { to: '/dashboard/trends', label: 'Hotspots & trends' },
  { to: '/dashboard/admin', label: 'Administration', adminOnly: true },
]

function useClock() {
  const [now, setNow] = useState(() => new Date())
  useEffect(() => {
    const t = window.setInterval(() => setNow(new Date()), 1000)
    return () => window.clearInterval(t)
  }, [])
  return now
}

export function AppShell() {
  const { session, signOut } = useAuth()
  const navigate = useNavigate()
  const now = useClock()
  const officer = session?.officer
  const [finderOpen, setFinderOpen] = useState(false)

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        setFinderOpen((o) => !o)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  return (
    <div className="flex min-h-screen flex-col text-foreground" style={{ background: 'var(--neu-base)' }}>

      {/* Classification strip */}
      <div className="print:hidden" style={{ background: 'var(--eth-teal-deep)' }}>
        <div className="mx-auto flex h-8 max-w-[1400px] items-center justify-between gap-6 px-6">
          <p className="text-xs text-white/70">
            <strong className="mr-2 font-semibold tracking-[0.12em]" style={{ color: 'var(--police-brass)' }}>
              RESTRICTED
            </strong>
            Authorised law-enforcement use only. Every action is logged.
          </p>
          <p className="font-mono text-xs tabular-nums text-white/60">
            {now.toLocaleDateString('en-IN', { weekday: 'short', day: '2-digit', month: 'short', year: 'numeric' })}{' '}
            {now.toLocaleTimeString('en-IN', { hour12: false })} IST
          </p>
        </div>
      </div>

      {/* Main header — neumorphic-friendly teal gradient */}
      <header className="eth-header print:hidden">
        <div className="mx-auto flex h-[68px] max-w-[1400px] items-center gap-6 px-6 xl:gap-10">
          <Link
            to="/dashboard"
            className="rounded-lg focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-white/60"
          >
            <Wordmark subtitle="Cyber crime monitoring console" />
          </Link>

          {/* Nav pills */}
          <nav className="flex h-full items-center gap-1" aria-label="Main">
            {NAV.filter((n) => !n.adminOnly || officer?.role === 'admin').map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) =>
                  cn(
                    'relative flex items-center rounded-lg px-3.5 py-1.5 text-[14px] font-medium whitespace-nowrap transition-all duration-150',
                    isActive
                      ? 'bg-white/15 text-white shadow-[inset_0_1px_1px_rgb(255_255_255/0.10),inset_0_-1px_1px_rgb(0_0_0/0.10)]'
                      : 'text-white/65 hover:bg-white/10 hover:text-white',
                  )
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>

          {/* Right side controls */}
          <div className="ml-auto flex items-center gap-3">
            {/* Case search */}
            <button
              type="button"
              onClick={() => setFinderOpen(true)}
              aria-label="Find a case (Ctrl K)"
              className="flex h-9 items-center gap-2 rounded-lg px-3 text-sm whitespace-nowrap text-white/60 transition-all duration-150 hover:text-white xl:w-56"
              style={{
                background: 'rgb(0 0 0 / 0.18)',
                boxShadow: 'inset 0 0 0 1px rgb(255 255 255 / 0.12)',
              }}
            >
              <Search className="size-4" aria-hidden />
              <span className="hidden xl:inline">Find a case</span>
              <kbd className="ml-auto hidden rounded bg-white/10 px-1.5 py-0.5 font-mono text-[11px] text-white/55 xl:inline">
                Ctrl K
              </kbd>
            </button>

            {isMock && (
              <span
                className="rounded-lg border px-2 py-0.5 text-xs font-medium whitespace-nowrap"
                style={{ borderColor: 'rgb(166 94 70 / 0.5)', color: 'var(--police-brass)' }}
                title="Showing generated demo data, not real reports"
              >
                Demo data
              </span>
            )}

            {/* Command wall link */}
            <Link
              to="/command"
              className="flex h-9 items-center gap-2 rounded-lg px-3 text-sm font-medium whitespace-nowrap text-white/80 transition-all duration-150 hover:text-white"
              style={{ background: 'rgb(255 255 255 / 0.10)', boxShadow: '0 1px 2px rgb(0 0 0 / 0.15)' }}
              title="Command wall"
            >
              <MonitorPlay className="size-4" aria-hidden />
              <span className="hidden lg:inline">Command wall</span>
              <span className="sr-only lg:hidden">Command wall</span>
            </Link>

            {/* Officer info + sign out */}
            {officer && (
              <div
                className="ml-2 flex items-center gap-3 pl-4"
                style={{ borderLeft: '1px solid rgb(255 255 255 / 0.15)' }}
              >
                <div className="text-right leading-tight">
                  <div className="text-sm font-semibold text-white">{officer.name}</div>
                  <div className="text-xs text-white/55">
                    {officer.role === 'admin' ? 'Administrator' : 'Investigating officer'}
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => {
                    signOut()
                    navigate('/login')
                  }}
                  className="flex size-9 items-center justify-center rounded-lg text-white/65 transition-all duration-150 hover:bg-white/10 hover:text-white"
                  aria-label="Sign out"
                  title="Sign out"
                >
                  <LogOut className="size-4" />
                </button>
              </div>
            )}
          </div>
        </div>

        {/* Teal accent rim at the bottom of header */}
        <div className="teal-strip" />
      </header>

      <main className="mx-auto w-full max-w-[1400px] flex-1 px-6 py-8">
        <Outlet />
      </main>

      <CaseFinder open={finderOpen} onOpenChange={setFinderOpen} />

      <footer
        className="mt-2"
        style={{ borderTop: '1px solid var(--border)' }}
      >
        <p className="mx-auto max-w-[1400px] px-6 py-4 text-xs text-muted-foreground">
          Victims can report cyber fraud on the National Cyber Crime Helpline{' '}
          <strong className="text-foreground">1930</strong>. This console shows device events and risk scores only.
          SMS text, OTP values and call audio are never collected.
        </p>
      </footer>
    </div>
  )
}
