'use client'

import { Check, Copy } from 'lucide-react'
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { caseRef, dateTime, deviceHash, formatPhone } from '@/lib/format'
import { detectedViaLabel, scamTypeLabel } from '@/lib/labels'
import { actionLabel, bandLabel, bandOf } from '@/lib/risk'
import type { Report } from '@/lib/types'

/** Plain-text summary an officer can paste into a 1930 / cybercrime.gov.in escalation. */
export function complaintSummary(r: Report): string {
  const lines = [
    `Case reference: ${caseRef(r)}`,
    `Received: ${dateTime(r.created_at)}`,
    `Category: ${scamTypeLabel[r.scam_type]} (suspected)`,
    `Caller number: ${formatPhone(r.caller_number)} (series ${r.number_prefix}-XXXXX${r.telecom_circle ? `, ${r.telecom_circle} circle, approx.` : ''})`,
    `Risk score: ${r.risk_score}/100, ${bandLabel[bandOf(r.risk_score)]} (L1 content ${r.l1_score}, L2 reputation ${r.l2_score}, L3 correlation ${r.l3_score})`,
    `Detected via: ${r.detected_via.map((v) => detectedViaLabel[v]).join(', ') || 'n/a'}`,
    `Action on phone: ${actionLabel[r.action_taken]}`,
  ]
  if (r.matched_phrases.length) lines.push(`Detected tactics: ${r.matched_phrases.map((p) => `"${p}"`).join(', ')}`)
  if (r.reasons.length) lines.push(`Reasons: ${r.reasons.join('; ')}`)
  if (r.similarity) lines.push(`Pattern match: "${r.similarity.matched}" (${Math.round(r.similarity.score * 100)}% similar)`)
  lines.push(`Reporting device: ${deviceHash(r.device_id)} (opaque reference; no citizen identity or conversation is held)`)
  return lines.join('\n')
}

interface Props {
  report: Report
  open: boolean
  onOpenChange: (open: boolean) => void
  onConfirm: () => void
  pending: boolean
}

export function EscalateDialog({ report, open, onOpenChange, onConfirm, pending }: Props) {
  const [copied, setCopied] = useState(false)
  const summary = complaintSummary(report)

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>Escalate {caseRef(report)}</DialogTitle>
          <DialogDescription>
            Marks the report as escalated. Use the summary below for the National Cyber Crime Helpline (1930) or the cyber-crime
            reporting portal.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-1.5">
          <div className="flex items-center justify-between">
            <Label>Escalation summary</Label>
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
          <pre className="max-h-64 overflow-auto rounded border border-border bg-navy-soft/50 p-3 font-mono text-xs leading-relaxed whitespace-pre-wrap">{summary}</pre>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button className="bg-red-700 text-white hover:bg-red-800" disabled={pending} onClick={onConfirm}>
            {pending ? 'Escalating…' : 'Mark escalated'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
