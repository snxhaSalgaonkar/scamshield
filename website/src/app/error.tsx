'use client'

export default function RootError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <div className="flex min-h-screen items-center justify-center p-8" role="alert">
      <div className="surface max-w-md space-y-3 p-6">
        <h1 className="font-display text-2xl font-semibold text-navy">Something went wrong</h1>
        <p className="text-sm text-muted-foreground">{error.message || 'Unexpected error.'}</p>
        <button type="button" onClick={reset} className="btn-teal h-10 px-4 text-sm font-semibold">
          Try again
        </button>
      </div>
    </div>
  )
}
