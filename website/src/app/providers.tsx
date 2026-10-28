'use client'

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useState } from 'react'
import { Toaster } from '@/components/ui/sonner'
import { TooltipProvider } from '@/components/ui/tooltip'
import { AuthProvider } from '@/lib/auth'

export function Providers({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(
    () => new QueryClient({ defaultOptions: { queries: { staleTime: 30_000, refetchOnWindowFocus: false, retry: 1 } } }),
  )
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <TooltipProvider delay={150}>{children}</TooltipProvider>
      </AuthProvider>
      <Toaster position="top-right" richColors closeButton />
    </QueryClientProvider>
  )
}
