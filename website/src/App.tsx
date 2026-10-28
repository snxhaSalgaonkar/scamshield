import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { lazy, Suspense } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AppShell } from '@/components/AppShell'
import { Skeleton } from '@/components/ui/skeleton'
import { Toaster } from '@/components/ui/sonner'
import { AuthProvider, RequireAuth } from '@/lib/auth'
import { DashboardPage } from '@/pages/DashboardPage'
import { LoginPage } from '@/pages/LoginPage'

// The live feed and login load eagerly; the rest are split out.
const CaseDetailPage = lazy(() => import('@/pages/CaseDetailPage').then((m) => ({ default: m.CaseDetailPage })))
const NumbersPage = lazy(() => import('@/pages/NumbersPage').then((m) => ({ default: m.NumbersPage })))
const TrendsPage = lazy(() => import('@/pages/TrendsPage').then((m) => ({ default: m.TrendsPage })))
const AdminPage = lazy(() => import('@/pages/AdminPage').then((m) => ({ default: m.AdminPage })))
const CommandWallPage = lazy(() => import('@/pages/CommandWallPage').then((m) => ({ default: m.CommandWallPage })))

const queryClient = new QueryClient({
  defaultOptions: { queries: { staleTime: 30_000, refetchOnWindowFocus: false } },
})

const pageFallback = <Skeleton className="h-96 w-full" />

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route
              path="/command"
              element={
                <RequireAuth>
                  <Suspense fallback={<div className="wall h-screen" />}>
                    <CommandWallPage />
                  </Suspense>
                </RequireAuth>
              }
            />
            <Route
              element={
                <RequireAuth>
                  <AppShell />
                </RequireAuth>
              }
            >
              <Route path="/dashboard" element={<DashboardPage />} />
              <Route path="/dashboard/cases/:id" element={<Suspense fallback={pageFallback}><CaseDetailPage /></Suspense>} />
              <Route path="/dashboard/numbers" element={<Suspense fallback={pageFallback}><NumbersPage /></Suspense>} />
              <Route path="/dashboard/trends" element={<Suspense fallback={pageFallback}><TrendsPage /></Suspense>} />
              <Route
                path="/dashboard/admin"
                element={
                  <RequireAuth role="admin">
                    <Suspense fallback={pageFallback}>
                      <AdminPage />
                    </Suspense>
                  </RequireAuth>
                }
              />
            </Route>
            <Route path="*" element={<Navigate to="/dashboard" replace />} />
          </Routes>
        </BrowserRouter>
        <Toaster position="top-right" richColors closeButton />
      </AuthProvider>
    </QueryClientProvider>
  )
}
