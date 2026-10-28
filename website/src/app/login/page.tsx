'use client'

import { Link2, Lock, MessageSquareText, ShieldAlert, ShieldCheck, Users } from 'lucide-react'
import { useRouter, useSearchParams } from 'next/navigation'
import { Suspense, useEffect, useState, type FormEvent } from 'react'
import { Wordmark } from '@/components/Wordmark'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { RESTRICTED_MESSAGE, useAuth } from '@/lib/auth'
import { isMock } from '@/lib/env'

const LAYERS = [
  { code: 'L1', title: 'Content', text: 'Scam phrases and tactics, detected on the phone', icon: MessageSquareText },
  { code: 'L2', title: 'Reputation', text: 'Independent reporters per number and number series', icon: Users },
  { code: 'L3', title: 'Correlation', text: 'OTP or debit SMS arriving during the call', icon: Link2 },
] as const

const wellStyle = {
  background: 'var(--neu-base)',
  boxShadow: 'inset 3px 3px 7px var(--neu-inset-dark), inset -3px -3px 7px var(--neu-inset-light)',
}

/** Only same-site paths, so ?next= can't be used as an open redirect. */
const safeNext = (n: string | null) => (n && n.startsWith('/') && !n.startsWith('//') ? n : '/dashboard')

function LoginForm() {
  const { officer, ready, signIn, signOut } = useAuth()
  const router = useRouter()
  const params = useSearchParams()
  const next = safeNext(params.get('next'))
  const restricted = params.get('error') === 'restricted'
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(restricted ? RESTRICTED_MESSAGE : null)
  const [pending, setPending] = useState(false)

  // Sent here by the proxy with a non-authority session: drop it.
  useEffect(() => {
    if (restricted) void signOut()
  }, [restricted, signOut])

  useEffect(() => {
    if (ready && officer && !restricted) router.replace(next)
  }, [ready, officer, restricted, next, router])

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setPending(true)
    try {
      await signIn(email.trim(), password)
      router.replace(next)
      router.refresh()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Sign-in failed. Check your email and password.')
    } finally {
      setPending(false)
    }
  }

  return (
    <div
      className="rounded-2xl p-8"
      style={{ background: 'var(--neu-base)', boxShadow: '8px 8px 20px var(--neu-shadow-dark), -8px -8px 20px var(--neu-shadow-light)' }}
    >
      <div className="mb-7">
        <div
          className="mb-4 flex size-12 items-center justify-center rounded-xl"
          style={{ background: 'var(--neu-base)', boxShadow: '4px 4px 10px var(--neu-shadow-dark), -4px -4px 10px var(--neu-shadow-light)' }}
        >
          <Lock className="size-5" style={{ color: 'var(--eth-teal)' }} aria-hidden />
        </div>
        <h1 className="font-display text-[28px] leading-tight font-semibold tracking-tight">Officer sign-in</h1>
        <p className="mt-1 text-[14px] text-muted-foreground">Use the credentials issued by your unit administrator.</p>
      </div>

      <form onSubmit={onSubmit} className="space-y-5">
        <div className="space-y-1.5">
          <Label htmlFor="email" className="text-[13px] font-semibold">
            Official email
          </Label>
          <div className="rounded-xl px-1 py-1" style={wellStyle}>
            <Input
              id="email"
              type="email"
              autoComplete="username"
              placeholder="name@unit.police.example"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              className="h-10 border-none bg-transparent shadow-none focus-visible:ring-2"
            />
          </div>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="password" className="text-[13px] font-semibold">
            Password
          </Label>
          <div className="rounded-xl px-1 py-1" style={wellStyle}>
            <Input
              id="password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              className="h-10 border-none bg-transparent shadow-none focus-visible:ring-2"
            />
          </div>
        </div>

        {error && (
          <p role="alert" className="flex items-start gap-2 rounded-xl bg-red-50 px-3 py-2.5 text-sm text-red-800">
            <ShieldAlert className="mt-0.5 size-4 shrink-0" aria-hidden />
            {error}
          </p>
        )}

        <button type="submit" disabled={pending} className="btn-teal h-11 w-full text-[15px] font-semibold disabled:opacity-60">
          {pending ? 'Checking credentials…' : 'Sign in'}
        </button>
      </form>

      {isMock && (
        <div className="mt-5 rounded-xl px-4 py-3 text-[13px] leading-relaxed" style={{ background: 'var(--police-brass-soft)', color: '#6b3a26' }}>
          <strong>Demo mode.</strong> No backend: any email and password work. Use an email containing{' '}
          <code className="font-mono">citizen</code> to see how a non-authority account is rejected.
        </div>
      )}
    </div>
  )
}

export default function LoginPage() {
  return (
    <div className="grid min-h-screen grid-cols-[1.2fr_1fr]" style={{ background: 'var(--neu-base)' }}>
      <div
        className="guilloche relative flex flex-col justify-between overflow-hidden px-14 py-12 text-white"
        style={{ background: 'linear-gradient(145deg, #02000D 0%, #07203F 65%, #0f305c 100%)', boxShadow: '6px 0 24px rgb(2 0 13 / 0.35)' }}
      >
        <div className="flex items-center gap-3">
          <ShieldCheck className="size-5 opacity-70" aria-hidden />
          <p className="text-sm text-white/70">
            <strong className="mr-2 font-semibold tracking-[0.12em]" style={{ color: 'var(--police-brass)' }}>
              RESTRICTED
            </strong>
            Authorised personnel only
          </p>
        </div>

        <div className="max-w-lg">
          <Wordmark size="lg" />
          <p className="mt-8 font-display text-[28px] leading-snug font-medium text-white/90">
            See fraud calls as they happen,
            <br />
            not after the money is gone.
          </p>
          <p className="mt-4 max-w-md text-[15px] leading-relaxed text-white/65">
            Citizens&apos; phones score every unknown call with three independent layers and report digital-arrest, OTP-theft and
            fake-KYC calls here within seconds. The conversation itself never leaves the phone.
          </p>
        </div>

        <ol className="grid grid-cols-3 gap-5">
          {LAYERS.map(({ code, title, text, icon: Icon }) => (
            <li key={code} className="rounded-xl p-4" style={{ background: 'rgb(255 255 255 / 0.08)', borderTop: '1px solid rgb(255 255 255 / 0.14)' }}>
              <Icon className="mb-2 size-6 text-white/70" aria-hidden />
              <span className="font-display text-2xl font-bold" style={{ color: 'var(--police-brass)' }}>
                {code}
              </span>
              <p className="mt-1 text-sm font-semibold text-white/90">{title}</p>
              <p className="mt-0.5 text-[12px] leading-snug text-white/55">{text}</p>
            </li>
          ))}
        </ol>
      </div>

      <div className="flex items-center justify-center p-12">
        <div className="w-full max-w-sm">
          <Suspense fallback={<div className="h-96 rounded-2xl" />}>
            <LoginForm />
          </Suspense>
          <p className="mt-5 border-t border-border pt-4 text-center text-xs leading-relaxed text-muted-foreground">
            Unauthorised access is prohibited. Sign-ins and case updates are recorded.
          </p>
        </div>
      </div>
    </div>
  )
}
