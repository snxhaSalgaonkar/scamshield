// Red-flag terms highlighted inline in the reasons a phone sends.
const TERMS = ['OTP', 'debit', 'remote-access', 'Remote-access', 'merged', 'primer', 'UPI', 'reported', 'impersonation', 'disconnect', 'wrong number', 'secrecy', 'isolation']

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
