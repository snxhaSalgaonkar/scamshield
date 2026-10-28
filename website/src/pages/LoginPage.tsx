import { Lock, ShieldAlert } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Navigate, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { Wordmark } from '@/components/Wordmark'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useAuth } from '@/lib/auth'
import { isMock } from '@/lib/data'

const LAYERS = [
  ['L1', 'Voice and keyword AI', 'Scam phrases, when the citizen opts in'],
  ['L2', 'Caller reputation', 'Reports and call velocity per number'],
  ['L3', 'Event correlation', 'OTP, debit, forwarding and merge during the call'],
] as const

export function LoginPage() {
  const { session, signIn } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [params] = useSearchParams()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(
    params.get('error') === 'expired' ? 'Your session expired. Sign in again to continue.' : null,
  )
  const [pending, setPending] = useState(false)
  const from = (location.state as { from?: string } | null)?.from ?? '/dashboard'

  if (session) return <Navigate to={from} replace />

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setPending(true)
    try {
      await signIn(email.trim(), password)
      navigate(from, { replace: true })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Sign-in failed. Check your email and password.')
    } finally {
      setPending(false)
    }
  }

  return (
    <div className="grid min-h-screen grid-cols-[1.15fr_1fr] bg-background">
      {/* Identity panel */}
      <div className="guilloche relative flex flex-col justify-between overflow-hidden border-r-4 border-brass bg-navy px-14 py-12 text-white">
        <p className="text-sm text-white/60">
          <strong className="mr-2 font-semibold tracking-[0.12em] text-brass">RESTRICTED</strong>
          Authorised personnel only
        </p>

        <div className="max-w-lg">
          <Wordmark size="lg" />
          <p className="mt-8 font-display text-[28px] leading-snug font-medium text-white/90">
            See fraud calls as they happen, not after the money is gone.
          </p>
          <p className="mt-4 max-w-md text-[15px] leading-relaxed text-white/65">
            Citizens&apos; phones raise an alert during digital-arrest, OTP-theft and fake-KYC calls. Each alert reaches this console
            within a second, scored by three independent layers.
          </p>
        </div>

        <ol className="grid grid-cols-3 gap-6 border-t border-white/15 pt-6">
          {LAYERS.map(([code, title, text]) => (
            <li key={code}>
              <span className="font-display text-3xl font-bold text-brass">{code}</span>
              <p className="mt-1 text-sm font-medium">{title}</p>
              <p className="text-[13px] leading-snug text-white/55">{text}</p>
            </li>
          ))}
        </ol>
      </div>

      {/* Sign-in */}
      <div className="flex items-center justify-center p-12">
        <div className="w-full max-w-sm">
          <div className="mb-8">
            <Lock className="mb-4 size-6 text-navy" aria-hidden />
            <h1 className="font-display text-[32px] leading-tight font-semibold tracking-tight text-navy">Officer sign-in</h1>
            <p className="mt-1 text-[15px] text-muted-foreground">Use the credentials issued by your unit administrator.</p>
          </div>

          <form onSubmit={onSubmit} className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="email">Official email</Label>
              <Input
                id="email"
                type="email"
                autoComplete="username"
                placeholder="name@unit.police.example"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                className="h-11 bg-white"
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="password">Password</Label>
              <Input
                id="password"
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                className="h-11 bg-white"
              />
            </div>

            {error && (
              <p role="alert" className="flex items-start gap-2 rounded-md bg-red-50 px-3 py-2.5 text-sm text-red-800 shadow-[inset_0_0_0_1px_rgb(220_38_38/0.25)]">
                <ShieldAlert className="mt-0.5 size-4 shrink-0" aria-hidden />
                {error}
              </p>
            )}

            <Button type="submit" disabled={pending} className="h-11 w-full bg-navy text-[15px] hover:bg-navy-deep">
              {pending ? 'Checking credentials…' : 'Sign in'}
            </Button>
          </form>

          {isMock && (
            <div className="mt-6 rounded-md bg-brass-soft px-4 py-3 text-[13px] leading-relaxed text-amber-950 shadow-[inset_0_0_0_1px_rgb(180_140_40/0.35)]">
              <strong>Demo mode.</strong> Any email and password will work. Include <code className="font-mono">admin</code> in the email
              (for example <code className="font-mono">admin@cyber.demo</code>) to open the administration pages.
            </div>
          )}

          <p className="mt-8 border-t border-border pt-4 text-xs leading-relaxed text-muted-foreground">
            Unauthorised access is prohibited. Every sign-in and every case you open is recorded in the audit log.
          </p>
        </div>
      </div>
    </div>
  )
}
