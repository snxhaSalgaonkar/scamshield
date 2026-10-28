'use client'

import { FlaskConical, Loader2, PhoneIncoming } from 'lucide-react'
import Link from 'next/link'
import { useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { simulateLocally } from '@/lib/data.mock'
import { isMock } from '@/lib/env'
import { SIM_SCENARIOS, type SimScenarioId } from '@/lib/scenarios'

async function run(id: SimScenarioId): Promise<number> {
  if (isMock) return simulateLocally(id)
  const res = await fetch('/api/simulate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ scenario: id }),
  })
  const body = (await res.json().catch(() => ({}))) as { inserted?: number; error?: string }
  if (!res.ok) throw new Error(body.error ?? `Simulator failed (${res.status})`)
  return body.inserted ?? 0
}

/** Buttons that file realistic reports, so the whole demo runs without a phone. */
export function Simulator() {
  const [busy, setBusy] = useState<SimScenarioId | null>(null)
  const [log, setLog] = useState<{ at: string; label: string; n: number }[]>([])

  return (
    <div className="space-y-5">
      <div>
        <h1 className="flex items-center gap-2 font-display text-[32px] leading-tight font-semibold tracking-tight text-navy-deep">
          <FlaskConical className="size-7" aria-hidden /> Demo simulator
        </h1>
        <p className="max-w-3xl text-[15px] text-muted-foreground">
          Files realistic reports exactly as a phone would{isMock ? ' (into the in-browser demo data)' : ', through the same database triggers (numbers, flagging, rate limit)'}. Use it
          as a safety net if the phone or network fails on stage.{' '}
          {isMock ? (
            <>
              Demo data lives in this tab, so switch to the <Link href="/dashboard" className="font-medium text-navy underline">overview</Link> here (not a new tab) to see it.
            </>
          ) : (
            <>
              Keep the <Link href="/dashboard" className="font-medium text-navy underline">overview</Link> open on the projector to watch reports arrive.
            </>
          )}
        </p>
      </div>

      <div className="grid grid-cols-2 gap-4 max-lg:grid-cols-1">
        {SIM_SCENARIOS.map((s) => (
          <div key={s.id} className="surface flex items-start justify-between gap-4 p-5">
            <div>
              <h2 className="font-display text-lg font-semibold text-navy">{s.label}</h2>
              <p className="mt-1 text-[13px] text-muted-foreground">{s.detail}</p>
            </div>
            <Button
              className="shrink-0 bg-navy text-white hover:bg-navy/90"
              disabled={busy !== null}
              onClick={async () => {
                setBusy(s.id)
                try {
                  const n = await run(s.id)
                  setLog((l) => [{ at: new Date().toLocaleTimeString('en-IN'), label: s.label, n }, ...l].slice(0, 10))
                  toast.success(`${s.label}: ${n} report${n > 1 ? 's' : ''} filed`)
                } catch (e) {
                  toast.error(e instanceof Error ? e.message : 'Simulator failed')
                } finally {
                  setBusy(null)
                }
              }}
              aria-label={`Run: ${s.label}`}
            >
              {busy === s.id ? <Loader2 className="size-4 animate-spin" aria-hidden /> : <PhoneIncoming className="size-4" aria-hidden />}
              Run
            </Button>
          </div>
        ))}
      </div>

      {log.length > 0 && (
        <section className="surface p-5" aria-label="Simulator log">
          <h2 className="mb-2 font-display text-lg font-semibold text-navy">This session</h2>
          <ul className="space-y-1 text-sm">
            {log.map((l, i) => (
              <li key={i} className="flex gap-3">
                <span className="w-24 text-muted-foreground tabular-nums">{l.at}</span>
                <span>{l.label}</span>
                <span className="text-muted-foreground">
                  {l.n} report{l.n > 1 ? 's' : ''}
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  )
}
