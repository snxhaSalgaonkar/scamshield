import Link from 'next/link'

export default function NotFound() {
  return (
    <div className="flex min-h-screen items-center justify-center p-8">
      <div className="surface max-w-md space-y-3 p-6">
        <h1 className="font-display text-2xl font-semibold text-navy">Page not found</h1>
        <p className="text-sm text-muted-foreground">The link may be mistyped.</p>
        <Link href="/dashboard" className="text-sm font-medium text-navy underline underline-offset-2">
          Go to the overview
        </Link>
      </div>
    </div>
  )
}
