import { Lock, ShieldCheck, ShieldAlert } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Navigate, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { Wordmark } from '@/components/Wordmark'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useAuth } from '@/lib/auth'
import { isMock } from '@/lib/data'

const LAYERS = [
  {
    code: 'L1',
    title: 'Voice & keyword AI',
    text: 'Scam phrases detected when the citizen opts in',
    icon: '🎙️',
  },
  {
    code: 'L2',
    title: 'Caller reputation',
    text: 'Reports and call velocity per number',
    icon: '📋',
  },
  {
    code: 'L3',
    title: 'Event correlation',
    text: 'OTP, debit, forwarding and merge during the call',
    icon: '🔗',
  },
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
    <div
      className="grid min-h-screen grid-cols-[1.2fr_1fr]"
      style={{ background: 'var(--neu-base)' }}
    >
      {/* ── Left identity panel ─────────────────────────────────────────── */}
      <div
        className="guilloche relative flex flex-col justify-between overflow-hidden px-14 py-12 text-white"
        style={{
          background: 'linear-gradient(145deg, #02000D 0%, #07203F 65%, #0f305c 100%)',
          boxShadow: '6px 0 24px rgb(2 0 13 / 0.35)',
        }}
      >
        {/* Top badge */}
        <div className="flex items-center gap-3">
          <ShieldCheck className="size-5 opacity-70" />
          <p className="text-sm text-white/70">
            <strong className="mr-2 font-semibold tracking-[0.12em]" style={{ color: 'var(--police-brass)' }}>
              RESTRICTED
            </strong>
            Authorised personnel only
          </p>
        </div>

        {/* Centre content */}
        <div className="max-w-lg">
          <Wordmark size="lg" />
          <p
            className="mt-8 text-[28px] leading-snug font-medium text-white/90"
            style={{ fontFamily: 'var(--font-display)' }}
          >
            See fraud calls as they happen,<br />not after the money is gone.
          </p>
          <p className="mt-4 max-w-md text-[15px] leading-relaxed text-white/65">
            Citizens&apos; phones raise an alert during digital-arrest, OTP-theft and fake-KYC
            calls. Each alert reaches this console within a second, scored by three independent
            layers.
          </p>
        </div>

        {/* Detection layers */}
        <ol className="grid grid-cols-3 gap-5">
          {LAYERS.map(({ code, title, text, icon }) => (
            <li
              key={code}
              className="rounded-xl p-4"
              style={{
                background: 'rgb(255 255 255 / 0.08)',
                boxShadow: 'inset 0 1px 1px rgb(255 255 255 / 0.12), inset 0 -1px 1px rgb(0 0 0 / 0.08)',
                borderTop: '1px solid rgb(255 255 255 / 0.14)',
              }}
            >
              <div className="mb-2 text-2xl">{icon}</div>
              <span
                className="text-2xl font-bold"
                style={{ fontFamily: 'var(--font-display)', color: 'var(--police-brass)' }}
              >
                {code}
              </span>
              <p className="mt-1 text-sm font-semibold text-white/90">{title}</p>
              <p className="mt-0.5 text-[12px] leading-snug text-white/55">{text}</p>
            </li>
          ))}
        </ol>
      </div>

      {/* ── Right sign-in panel ──────────────────────────────────────────── */}
      <div
        className="flex items-center justify-center p-12"
        style={{ background: 'var(--neu-base)' }}
      >
        <div className="w-full max-w-sm">

          {/* Neumorphic card */}
          <div
            className="rounded-2xl p-8"
            style={{
              background: 'var(--neu-base)',
              boxShadow: '8px 8px 20px var(--neu-shadow-dark), -8px -8px 20px var(--neu-shadow-light)',
            }}
          >
            {/* Icon + title */}
            <div className="mb-7">
              <div
                className="mb-4 flex size-12 items-center justify-center rounded-xl"
                style={{
                  background: 'var(--neu-base)',
                  boxShadow: '4px 4px 10px var(--neu-shadow-dark), -4px -4px 10px var(--neu-shadow-light)',
                }}
              >
                <Lock className="size-5" style={{ color: 'var(--eth-teal)' }} aria-hidden />
              </div>
              <h1
                className="text-[28px] leading-tight font-semibold tracking-tight"
                style={{ fontFamily: 'var(--font-display)', color: 'var(--foreground)' }}
              >
                Officer sign-in
              </h1>
              <p className="mt-1 text-[14px] text-muted-foreground">
                Use the credentials issued by your unit administrator.
              </p>
            </div>

            <form onSubmit={onSubmit} className="space-y-5">
              {/* Email field */}
              <div className="space-y-1.5">
                <Label htmlFor="email" className="text-[13px] font-semibold" style={{ color: 'var(--foreground)' }}>
                  Official email
                </Label>
                <div
                  className="rounded-xl px-1 py-1"
                  style={{
                    background: 'var(--neu-base)',
                    boxShadow: 'inset 3px 3px 7px var(--neu-inset-dark), inset -3px -3px 7px var(--neu-inset-light)',
                  }}
                >
                  <Input
                    id="email"
                    type="email"
                    autoComplete="username"
                    placeholder="name@unit.police.example"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    required
                    className="h-10 border-none bg-transparent shadow-none ring-0 focus-visible:ring-0 focus-visible:ring-offset-0"
                    style={{ boxShadow: 'none', background: 'transparent' }}
                  />
                </div>
              </div>

              {/* Password field */}
              <div className="space-y-1.5">
                <Label htmlFor="password" className="text-[13px] font-semibold" style={{ color: 'var(--foreground)' }}>
                  Password
                </Label>
                <div
                  className="rounded-xl px-1 py-1"
                  style={{
                    background: 'var(--neu-base)',
                    boxShadow: 'inset 3px 3px 7px var(--neu-inset-dark), inset -3px -3px 7px var(--neu-inset-light)',
                  }}
                >
                  <Input
                    id="password"
                    type="password"
                    autoComplete="current-password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    required
                    className="h-10 border-none bg-transparent shadow-none ring-0 focus-visible:ring-0 focus-visible:ring-offset-0"
                    style={{ boxShadow: 'none', background: 'transparent' }}
                  />
                </div>
              </div>

              {/* Error alert */}
              {error && (
                <p
                  role="alert"
                  className="flex items-start gap-2 rounded-xl px-3 py-2.5 text-sm text-red-800"
                  style={{
                    background: '#fef2f2',
                    boxShadow: 'inset 2px 2px 5px rgb(220 38 38 / 0.10), inset -2px -2px 5px rgb(255 255 255 / 0.70)',
                  }}
                >
                  <ShieldAlert className="mt-0.5 size-4 shrink-0" aria-hidden />
                  {error}
                </p>
              )}

              {/* Submit */}
              <button
                type="submit"
                disabled={pending}
                className="btn-teal h-11 w-full text-[15px] font-semibold disabled:opacity-60"
              >
                {pending ? 'Checking credentials…' : 'Sign in'}
              </button>
            </form>

            {/* Demo notice */}
            {isMock && (
              <div
                className="mt-5 rounded-xl px-4 py-3 text-[13px] leading-relaxed"
                style={{
                  background: 'var(--police-brass-soft)',
                  boxShadow: 'inset 2px 2px 5px rgb(176 143 63 / 0.15), inset -2px -2px 5px rgb(255 255 255 / 0.70)',
                  color: '#6b4c0a',
                }}
              >
                <strong>Demo mode.</strong> Any email and password will work. Include{' '}
                <code className="font-mono">admin</code> in the email (e.g.{' '}
                <code className="font-mono">admin@cyber.demo</code>) for admin pages.
              </div>
            )}
          </div>

          {/* Legal note */}
          <p
            className="mt-5 text-center text-xs leading-relaxed text-muted-foreground"
            style={{ borderTop: '1px solid var(--border)', paddingTop: '1rem', marginTop: '1.25rem' }}
          >
            Unauthorised access is prohibited. Every sign-in and every case you open is recorded in the audit log.
          </p>
        </div>
      </div>
    </div>
  )
}
