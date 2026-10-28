const rtf = new Intl.RelativeTimeFormat('en', { numeric: 'auto', style: 'short' })

export function timeAgo(iso: string, now = Date.now()): string {
  const diffSec = Math.round((new Date(iso).getTime() - now) / 1000)
  const abs = Math.abs(diffSec)
  if (abs < 45) return 'just now'
  if (abs < 3600) return rtf.format(Math.round(diffSec / 60), 'minute')
  if (abs < 86400) return rtf.format(Math.round(diffSec / 3600), 'hour')
  return rtf.format(Math.round(diffSec / 86400), 'day')
}

export function clockTime(iso: string): string {
  return new Date(iso).toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', hour12: true })
}

export function dateTime(iso: string): string {
  return new Date(iso).toLocaleString('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: true,
  })
}

export function shortDate(iso: string): string {
  return new Date(iso).toLocaleDateString('en-IN', { day: '2-digit', month: 'short' })
}

/** '+919876543210' → '+91 98765 43210'. Non-Indian numbers are returned unchanged. */
export function formatPhone(e164: string): string {
  const m = /^\+91(\d{5})(\d{5})$/.exec(e164)
  return m ? `+91 ${m[1]} ${m[2]}` : e164
}

/** '+919876543210' → '+91 98XXX XX210' (middle digits hidden for screen-sharing). */
export function maskPhone(e164: string): string {
  const m = /^\+91(\d{2})\d{3}\d{2}(\d{3})$/.exec(e164)
  return m ? `+91 ${m[1]}XXX XX${m[2]}` : e164
}

/** Digits only, for searching numbers typed with spaces/dashes/+91. */
export const digitsOf = (s: string) => s.replace(/\D/g, '')

/** Short, stable, opaque tag for a device_id (FNV-1a 32-bit). Never show the raw id. */
export function deviceHash(deviceId: string): string {
  let h = 0x811c9dc5
  for (let i = 0; i < deviceId.length; i++) {
    h ^= deviceId.charCodeAt(i)
    h = Math.imul(h, 0x01000193)
  }
  return `dev-${(h >>> 0).toString(16).padStart(8, '0')}`
}

/** Case reference in the style of a cyber-cell register entry, e.g. RC/CYB/2026/3F9A1C. */
export function caseRef(r: { id: string; created_at: string }): string {
  const year = new Date(r.created_at).getFullYear()
  return `RC/CYB/${year}/${r.id.replace(/-/g, '').slice(0, 6).toUpperCase()}`
}

export const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 })

export const pct = (n: number) => `${Math.round(n * 100)}%`
