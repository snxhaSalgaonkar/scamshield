'use client'

import { RotateCcw } from 'lucide-react'
import { Button } from '@/components/ui/button'

/** Error boundary for every dashboard page: keeps the shell, offers a retry. */
export default function DashboardError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <div className="surface max-w-xl space-y-3 p-6" role="alert">
      <h1 className="font-display text-2xl font-semibold text-navy">This page hit an error</h1>
      <p className="text-sm text-muted-foreground">
        {error.message || 'Something went wrong while loading this page.'} The live feed and other pages still work.
      </p>
      <Button onClick={reset} className="bg-navy text-white hover:bg-navy/90">
        <RotateCcw className="size-4" aria-hidden /> Try again
      </Button>
    </div>
  )
}
