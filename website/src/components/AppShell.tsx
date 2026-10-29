'use client'

import { LogOut, Search } from 'lucide-react'
import Link from 'next/link'
import { usePathname, useRouter } from 'next/navigation'
import { useEffect, useState, type ReactNode } from 'react'
import { CaseFinder } from '@/components/CaseFinder'
import { SpikeBanner } from '@/components/SpikeBanner'
import { Wordmark } from '@/components/Wordmark'
import { useRealtime } from '@/hooks/realtime'
import { useAuth } from '@/lib/auth'
import { cn } from '@/lib/utils'

const NAV = [
  { href: '/dashboard', label: 'Overview', exact: true },
  { href: '/dashboard/reports', label: 'Reports' },
  { href: '/dashboard/numbers', label: 'Numbers' },
]

const STATUS_TEXT = { live: 'Live', connecting: 'Connecting…', offline: 'Offline' } as const

function useClock() {
  const [now, setNow] = useState<Date | null>(null)
  useEffect(() => {
    setNow(new Date())
    const t = window.setInterval(() => setNow(new Date()), 1000)
    return () => window.clearInterval(t)
  }, [])
  return now
}

export function AppShell({ children }: { children: ReactNode }) {
  const { officer, signOut } = useAuth()
  const { status } = useRealtime()
  const router = useRouter()
  const pathname = usePathname()
  const now = useClock()
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
            Authorised law-enforcement use only.
          </p>
          <p className="font-mono text-xs text-white/60 tabular-nums" suppressHydrationWarning>
            {now &&
              `${now.toLocaleDateString('en-IN', { weekday: 'short', day: '2-digit', month: 'short', year: 'numeric' })} ${now.toLocaleTimeString('en-IN', { hour12: false })} IST`}
          </p>
        </div>
      </div>

      <header className="eth-header print:hidden">
        <div className="mx-auto flex h-[68px] max-w-[1400px] items-center gap-6 px-6 xl:gap-8">
          <Link href="/dashboard" className="rounded-lg focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-white/60">
            <Wordmark subtitle="Cyber crime monitoring console" />
          </Link>

          <nav className="flex h-full items-center gap-1" aria-label="Main">
            {NAV.map((item) => {
              const active = item.exact ? pathname === item.href : pathname.startsWith(item.href)
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  aria-current={active ? 'page' : undefined}
                  className={cn(
                    'relative flex items-center rounded-lg px-3 py-1.5 text-[14px] font-medium whitespace-nowrap transition-all duration-150 focus-visible:outline-2 focus-visible:outline-white/70',
                    active ? 'bg-white/15 text-white' : 'text-white/65 hover:bg-white/10 hover:text-white',
                  )}
                >
                  {item.label}
                </Link>
              )
            })}
          </nav>

          <div className="ml-auto flex items-center gap-2.5">
            <span
              className="flex items-center gap-2 rounded-lg px-2.5 py-1 text-xs font-medium whitespace-nowrap text-white/80"
              style={{ background: 'rgb(0 0 0 / 0.18)' }}
              title={status === 'live' ? 'Realtime connected: new reports appear instantly' : 'Realtime not connected'}
              role="status"
            >
              <span className="live-dot" data-status={status} aria-hidden />
              {STATUS_TEXT[status]}
            </span>

            <button
              type="button"
              onClick={() => setFinderOpen(true)}
              aria-label="Find a report (Ctrl K)"
              className="flex h-9 items-center gap-2 rounded-lg px-3 text-sm whitespace-nowrap text-white/60 transition-all duration-150 hover:text-white focus-visible:outline-2 focus-visible:outline-white/70 xl:w-44"
              style={{ background: 'rgb(0 0 0 / 0.18)', boxShadow: 'inset 0 0 0 1px rgb(255 255 255 / 0.12)' }}
            >
              <Search className="size-4" aria-hidden />
              <span className="hidden xl:inline">Find</span>
              <kbd className="ml-auto hidden rounded bg-white/10 px-1.5 py-0.5 font-mono text-[11px] text-white/55 xl:inline">Ctrl K</kbd>
            </button>

            {officer && (
              <div className="ml-1 flex items-center gap-3 pl-3" style={{ borderLeft: '1px solid rgb(255 255 255 / 0.15)' }}>
                <div className="hidden text-right leading-tight lg:block">
                  <div className="text-sm font-semibold text-white">{officer.name}</div>
                  <div className="max-w-44 truncate text-xs text-white/55">{officer.email}</div>
                </div>
                <button
                  type="button"
                  onClick={async () => {
                    await signOut()
                    router.replace('/login')
                    router.refresh()
                  }}
                  className="flex size-9 items-center justify-center rounded-lg text-white/65 transition-all duration-150 hover:bg-white/10 hover:text-white focus-visible:outline-2 focus-visible:outline-white/70"
                  aria-label="Sign out"
                  title="Sign out"
                >
                  <LogOut className="size-4" />
                </button>
              </div>
            )}
          </div>
        </div>
        <div className="teal-strip" />
      </header>

      <SpikeBanner />

      <main className="mx-auto w-full max-w-[1400px] flex-1 px-6 py-8">{children}</main>

      <CaseFinder open={finderOpen} onOpenChange={setFinderOpen} />

      <footer className="mt-2 border-t border-border">
        <p className="mx-auto max-w-[1400px] px-6 py-4 text-xs text-muted-foreground">
          Victims can report cyber fraud on the National Cyber Crime Helpline <strong className="text-foreground">1930</strong>. Reports
          carry detected tactics and scores only: conversations, SMS text and OTP values never leave the citizen&apos;s phone.
        </p>
      </footer>
    </div>
  )
}
