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

/** "+03:12" offset from call start. */
export function callOffset(iso: string, startIso: string): string {
  const sec = Math.round((new Date(iso).getTime() - new Date(startIso).getTime()) / 1000)
  const sign = sec < 0 ? '-' : '+'
  const abs = Math.abs(sec)
  return `${sign}${String(Math.floor(abs / 60)).padStart(2, '0')}:${String(abs % 60).padStart(2, '0')}`
}

export function duration(startIso: string, endIso: string): string {
  const sec = Math.max(0, Math.round((new Date(endIso).getTime() - new Date(startIso).getTime()) / 1000))
  const m = Math.floor(sec / 60)
  return m ? `${m} min ${sec % 60}s` : `${sec}s`
}

/** Case reference in the style of a cyber-cell register entry, e.g. RC/CYB/2026/000123. */
export function caseRef(alert: { id: string; createdAt: string }): string {
  const year = new Date(alert.createdAt).getFullYear()
  const digits = alert.id.replace(/\D/g, '').slice(-6).padStart(6, '0')
  return `RC/CYB/${year}/${digits}`
}

export const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 })

export const pct = (n: number) => `${Math.round(n * 100)}%`
