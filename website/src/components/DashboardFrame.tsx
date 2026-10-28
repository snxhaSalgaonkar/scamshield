'use client'

import { usePathname, useRouter } from 'next/navigation'
import { useEffect, type ReactNode } from 'react'
import { AppShell } from '@/components/AppShell'
import { Skeleton } from '@/components/ui/skeleton'
import { RealtimeProvider } from '@/hooks/realtime'
import { useAuth } from '@/lib/auth'

/** Client-side guard. In Supabase mode the proxy already blocked non-authorities; this covers mock mode and sign-out. */
export function RequireOfficer({ children, fallback }: { children: ReactNode; fallback?: ReactNode }) {
  const { officer, ready } = useAuth()
  const router = useRouter()
  const pathname = usePathname()
  useEffect(() => {
    if (ready && !officer) router.replace(`/login?next=${encodeURIComponent(pathname)}`)
  }, [ready, officer, router, pathname])
  if (!ready || !officer) return <>{fallback ?? null}</>
  return <>{children}</>
}

export function DashboardFrame({ children, simulatorEnabled }: { children: ReactNode; simulatorEnabled: boolean }) {
  return (
    <RequireOfficer
      fallback={
        <div className="mx-auto max-w-[1400px] space-y-4 px-6 py-24">
          <Skeleton className="h-10 w-72" />
          <Skeleton className="h-64 w-full" />
        </div>
      }
    >
      <RealtimeProvider>
        <AppShell simulatorEnabled={simulatorEnabled}>{children}</AppShell>
      </RealtimeProvider>
    </RequireOfficer>
  )
}
