import { cn } from '@/lib/utils'

/**
 * Generic police-style badge (shield + star). Deliberately not any real state or
 * national emblem: this is a prototype and must not pass for an official system.
 */
export function Crest({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 48 56" className={cn('shrink-0', className)} aria-hidden>
      <path
        d="M24 2 44 9v18c0 13-8.6 22.6-20 27C12.6 49.6 4 40 4 27V9Z"
        fill="var(--police-navy-deep)"
        stroke="var(--police-brass)"
        strokeWidth="2.5"
      />
      <path
        d="M24 7.5 39.5 13v14c0 10-6.4 17.6-15.5 21.4C14.9 44.6 8.5 37 8.5 27V13Z"
        fill="none"
        stroke="var(--police-brass)"
        strokeOpacity=".45"
        strokeWidth="1"
      />
      <path
        d="m24 15 3.1 6.6 7.2.8-5.4 4.9 1.5 7.1L24 30.8l-6.4 3.6 1.5-7.1-5.4-4.9 7.2-.8Z"
        fill="var(--police-brass)"
      />
      <rect x="15" y="38" width="18" height="3" rx="1" fill="var(--police-brass)" fillOpacity=".8" />
    </svg>
  )
}
