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
  { to: '/dashboard/trends', label: 'Hotspots and trends' },
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
    <div className="flex min-h-screen flex-col bg-background text-foreground">
      {/* Classification marking, as printed on restricted government systems */}
      <div className="bg-navy-deep text-xs text-white/65 print:hidden">
        <div className="mx-auto flex h-8 max-w-[1400px] items-center justify-between gap-6 px-6">
          <p>
            <strong className="mr-2 font-semibold tracking-[0.12em] text-brass">RESTRICTED</strong>
            For authorised law-enforcement use only. Every action is logged.
          </p>
          <p className="font-mono tabular-nums">
            {now.toLocaleDateString('en-IN', { weekday: 'short', day: '2-digit', month: 'short', year: 'numeric' })}{' '}
            {now.toLocaleTimeString('en-IN', { hour12: false })} IST
          </p>
        </div>
      </div>

      <header className="border-b-[3px] border-brass bg-navy text-white print:hidden">
        <div className="mx-auto flex h-[68px] max-w-[1400px] items-center gap-6 px-6 xl:gap-10">
          <Link to="/dashboard" className="rounded focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-brass">
            <Wordmark subtitle="Cyber crime monitoring console" />
          </Link>

          <nav className="flex h-full items-stretch gap-1" aria-label="Main">
            {NAV.filter((n) => !n.adminOnly || officer?.role === 'admin').map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) =>
                  cn(
                    'relative flex items-center px-3 text-[15px] font-medium whitespace-nowrap transition-colors',
                    isActive ? 'text-white' : 'text-white/60 hover:text-white',
                  )
                }
              >
                {({ isActive }) => (
                  <>
                    {item.label}
                    {isActive && <span className="absolute inset-x-3 -bottom-[3px] h-[3px] bg-white" aria-hidden />}
                  </>
                )}
              </NavLink>
            ))}
          </nav>

          <div className="ml-auto flex items-center gap-3">
            <button
              type="button"
              onClick={() => setFinderOpen(true)}
              aria-label="Find a case (Ctrl K)"
              className="flex h-9 items-center gap-2 rounded-md bg-navy-deep/60 px-3 text-sm whitespace-nowrap text-white/55 shadow-[inset_0_0_0_1px_rgb(255_255_255/0.12)] hover:text-white/80 xl:w-56"
            >
              <Search className="size-4" aria-hidden />
              <span className="hidden xl:inline">Find a case</span>
              <kbd className="ml-auto hidden rounded bg-white/10 px-1.5 py-0.5 font-mono text-[11px] text-white/60 xl:inline">Ctrl K</kbd>
            </button>
            {isMock && (
              <span className="rounded border border-brass/50 px-2 py-0.5 text-xs font-medium whitespace-nowrap text-brass" title="Showing generated demo data, not real reports">
                Demo data
              </span>
            )}
            <Link
              to="/command"
              className="flex h-9 items-center gap-2 rounded-md bg-white/10 px-3 text-sm font-medium whitespace-nowrap hover:bg-white/15"
              title="Command wall"
            >
              <MonitorPlay className="size-4" aria-hidden />
              <span className="hidden lg:inline">Command wall</span>
              <span className="sr-only lg:hidden">Command wall</span>
            </Link>
            {officer && (
              <div className="ml-2 flex items-center gap-3 border-l border-white/15 pl-4">
                <div className="text-right leading-tight">
                  <div className="text-sm font-medium">{officer.name}</div>
                  <div className="text-xs text-white/55">{officer.role === 'admin' ? 'Administrator' : 'Investigating officer'}</div>
                </div>
                <button
                  type="button"
                  onClick={() => {
                    signOut()
                    navigate('/login')
                  }}
                  className="flex size-10 items-center justify-center rounded-md text-white/70 hover:bg-white/10 hover:text-white"
                  aria-label="Sign out"
                  title="Sign out"
                >
                  <LogOut className="size-4" />
                </button>
              </div>
            )}
          </div>
        </div>
      </header>

      <main className="mx-auto w-full max-w-[1400px] flex-1 px-6 py-7">
        <Outlet />
      </main>

      <CaseFinder open={finderOpen} onOpenChange={setFinderOpen} />

      <footer className="border-t border-border">
        <p className="mx-auto max-w-[1400px] px-6 py-4 text-xs text-muted-foreground">
          Victims can report cyber fraud on the National Cyber Crime Helpline <strong className="text-foreground">1930</strong>. This
          console shows device events and risk scores only. SMS text, OTP values and call audio are never collected.
        </p>
      </footer>
    </div>
  )
}
