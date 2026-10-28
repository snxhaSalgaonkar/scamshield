import { useQuery } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { dataSource } from '@/lib/data'
import { dateTime } from '@/lib/format'
import { eventLabel } from '@/lib/rules'
import type { AlertEventType } from '@/lib/types'

function Section({ title, description, children }: { title: string; description?: string; children: ReactNode }) {
  return (
    <section className="surface p-5">
      <div className="mb-3">
        <h2 className="font-display text-lg font-semibold text-navy">{title}</h2>
        {description && <p className="text-[13px] text-muted-foreground">{description}</p>}
      </div>
      {children}
    </section>
  )
}

const headCls = 'h-10 text-[13px] font-semibold text-navy/75'

export function AdminPage() {
  const officers = useQuery({ queryKey: ['officers'], queryFn: () => dataSource.getOfficers() })
  const config = useQuery({ queryKey: ['config'], queryFn: () => dataSource.getConfig() })
  const audit = useQuery({ queryKey: ['audit'], queryFn: () => dataSource.getAuditLog(), refetchInterval: 10_000 })
  const cfg = config.data

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-display text-[32px] leading-tight font-semibold tracking-tight text-navy">Administration</h1>
        <p className="text-[15px] text-muted-foreground">Officer accounts, risk-engine settings and the record of who opened what.</p>
      </div>

      <div className="grid grid-cols-2 gap-5">
        <Section title="Officers" description="Accounts allowed into this console">
          {!officers.data ? (
            <Skeleton className="h-32" />
          ) : (
            <Table>
              <TableHeader>
                <TableRow className="hover:bg-transparent">
                  <TableHead className={headCls}>Name</TableHead>
                  <TableHead className={headCls}>Email</TableHead>
                  <TableHead className={headCls}>Role</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {officers.data.map((o) => (
                  <TableRow key={o.id}>
                    <TableCell className="font-medium">{o.name}</TableCell>
                    <TableCell className="text-muted-foreground">{o.email}</TableCell>
                    <TableCell>
                      <Badge variant="outline" className={o.role === 'admin' ? 'rounded-sm border-navy bg-navy text-white' : 'rounded-sm'}>
                        {o.role === 'admin' ? 'Admin' : 'Officer'}
                      </Badge>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </Section>

        <Section title="Fusion engine configuration" description="Read from the backend config table; tuned without redeploying the app">
          {!cfg ? (
            <Skeleton className="h-32" />
          ) : (
            <div className="grid grid-cols-2 gap-5 text-sm">
              <div>
                <p className="mb-1.5 text-xs font-semibold uppercase tracking-wider text-muted-foreground">Thresholds</p>
                <dl className="space-y-1">
                  <div className="flex justify-between"><dt>Warning banner</dt><dd className="font-mono">≥ {cfg.thresholds.warning}</dd></div>
                  <div className="flex justify-between"><dt>Red flag</dt><dd className="font-mono">≥ {cfg.thresholds.red}</dd></div>
                  <div className="flex justify-between"><dt>Post-call window</dt><dd className="font-mono">{cfg.postCallWindowMin} min</dd></div>
                </dl>
                <p className="mt-4 mb-1.5 text-xs font-semibold uppercase tracking-wider text-muted-foreground">Layer weights</p>
                <dl className="space-y-1">
                  <div className="flex justify-between"><dt>L1 voice / keyword</dt><dd className="font-mono">{cfg.weights.l1}</dd></div>
                  <div className="flex justify-between"><dt>L2 reputation</dt><dd className="font-mono">{cfg.weights.l2}</dd></div>
                  <div className="flex justify-between"><dt>L3 events</dt><dd className="font-mono">{cfg.weights.l3}</dd></div>
                </dl>
              </div>
              <div>
                <p className="mb-1.5 text-xs font-semibold uppercase tracking-wider text-muted-foreground">L3 event points</p>
                <dl className="space-y-1">
                  <div className="flex justify-between"><dt>Unknown caller (gate)</dt><dd className="font-mono">+{cfg.unknownCallerPoints}</dd></div>
                  {Object.entries(cfg.eventPoints).map(([type, pts]) => (
                    <div key={type} className="flex justify-between gap-2">
                      <dt className="truncate">{eventLabel[type as AlertEventType]}</dt>
                      <dd className="font-mono">+{pts}</dd>
                    </div>
                  ))}
                </dl>
              </div>
              <div className="col-span-2">
                <p className="mb-1.5 text-xs font-semibold uppercase tracking-wider text-muted-foreground">Combo rules (automatic red flag)</p>
                <ul className="list-inside list-disc space-y-0.5">
                  {cfg.comboRules.map((r) => (
                    <li key={r}>{r}</li>
                  ))}
                </ul>
              </div>
            </div>
          )}
        </Section>
      </div>

      <Section title="Audit log" description="Every sign-in, case view and status change. Refreshes every 10 seconds.">
        {!audit.data ? (
          <Skeleton className="h-40" />
        ) : (
          <Table>
            <TableHeader>
              <TableRow className="hover:bg-transparent">
                <TableHead className={headCls}>Time</TableHead>
                <TableHead className={headCls}>Officer</TableHead>
                <TableHead className={headCls}>Action</TableHead>
                <TableHead className={headCls}>Target</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {audit.data.map((e) => (
                <TableRow key={e.id}>
                  <TableCell className="whitespace-nowrap font-mono text-xs text-muted-foreground">{dateTime(e.ts)}</TableCell>
                  <TableCell className="font-medium">{e.officerName}</TableCell>
                  <TableCell>{e.action}</TableCell>
                  <TableCell className="font-mono text-xs">{e.target}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Section>
    </div>
  )
}
