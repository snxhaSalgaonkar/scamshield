import { Check, Copy } from 'lucide-react'
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { caseRef, dateTime, inr } from '@/lib/format'
import { eventLabel, firedCombos } from '@/lib/rules'
import type { Alert } from '@/lib/types'

function complaintSummary(a: Alert): string {
  const debits = a.events.filter((e) => e.type === 'debit_sms' && e.amount)
  const lines = [
    `Case reference: ${caseRef(a)}`,
    `Received: ${dateTime(a.createdAt)}`,
    `Category: ${a.scamType} (suspected)`,
    `Caller: ${a.callerPrefix} (ref #${a.callerHash})`,
    `Region: ${a.region}`,
    `Risk score: ${a.score}/100 (L1 ${a.l1 ?? 'n/a'}, L2 ${a.l2}, L3 ${a.l3})`,
    `Phone events: ${a.events.filter((e) => !['call_start', 'call_end'].includes(e.type)).map((e) => eventLabel[e.type]).join('; ') || 'none'}`,
  ]
  if (debits.length) lines.push(`Reported debits: ${debits.map((d) => inr.format(d.amount!)).join(', ')}`)
  const combos = firedCombos(a.events)
  if (combos.length) lines.push(`Combo rules: ${combos.join('; ')}`)
  lines.push(
    a.complaintSubmitted
      ? 'Citizen consent: complaint submitted (case_sharing). Identity details available on request.'
      : 'Citizen consent: not given. Identity withheld; device reference only.',
  )
  return lines.join('\n')
}

interface Props {
  alert: Alert
  open: boolean
  onOpenChange: (open: boolean) => void
  onConfirm: (note: string) => void
  pending: boolean
}

export function EscalateDialog({ alert, open, onOpenChange, onConfirm, pending }: Props) {
  const [note, setNote] = useState('')
  const [copied, setCopied] = useState(false)
  const summary = complaintSummary(alert)

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>Escalate {caseRef(alert)}</DialogTitle>
          <DialogDescription>
            Marks the case as escalated and records your note in the audit log. Use the summary below for the National Cyber
            Crime Helpline (1930) or the cyber-crime reporting portal.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-1.5">
          <div className="flex items-center justify-between">
            <Label>Complaint summary</Label>
            <Button
              variant="ghost"
              size="sm"
              onClick={async () => {
                try {
                  await navigator.clipboard.writeText(summary)
                  setCopied(true)
                  window.setTimeout(() => setCopied(false), 2000)
                } catch {
                  // clipboard blocked; the text is still selectable
                }
              }}
            >
              {copied ? <Check className="size-3.5" aria-hidden /> : <Copy className="size-3.5" aria-hidden />}
              {copied ? 'Copied' : 'Copy'}
            </Button>
          </div>
          <pre className="max-h-52 overflow-auto rounded border border-border bg-navy-soft/50 p-3 font-mono text-xs leading-relaxed whitespace-pre-wrap">
            {summary}
          </pre>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="escalate-note">Officer note</Label>
          <Textarea
            id="escalate-note"
            placeholder="e.g. Forwarded to cyber cell, matches pattern of earlier digital-arrest cases"
            value={note}
            onChange={(e) => setNote(e.target.value)}
            rows={3}
          />
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button className="bg-red-700 hover:bg-red-800" disabled={pending} onClick={() => onConfirm(note.trim())}>
            {pending ? 'Escalating…' : 'Escalate case'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
