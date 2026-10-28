// Red-flag terms highlighted inline wherever case text is shown.
const TERMS = [
  'OTP',
  'debit',
  'remote-access',
  'merged',
  'merge',
  'forwarding',
  'APK',
  'combo rule',
  'reported',
  'international',
  'VoIP',
  'long call',
]

const pattern = new RegExp(`(${TERMS.map((t) => t.replace(/[-]/g, '\\-')).join('|')})`, 'gi')

export function HighlightedText({ text }: { text: string }) {
  const parts = text.split(pattern)
  return (
    <>
      {parts.map((part, i) =>
        i % 2 === 1 ? (
          <mark key={i} className="rounded-sm bg-red-100 px-0.5 font-semibold text-red-800">
            {part}
          </mark>
        ) : (
          <span key={i}>{part}</span>
        ),
      )}
    </>
  )
}
