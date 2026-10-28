import { DashboardFrame } from '@/components/DashboardFrame'
import { Simulator } from '@/components/Simulator'
import { isMock } from '@/lib/env'

export const metadata = { title: 'Demo simulator' }

export default function SimulatePage() {
  const enabled = isMock || process.env.SIMULATOR_ENABLED === 'true'
  return (
    <DashboardFrame simulatorEnabled={enabled}>
      {enabled ? (
        <Simulator />
      ) : (
        <div className="surface max-w-xl p-6">
          <h1 className="font-display text-2xl font-semibold text-navy">Simulator disabled</h1>
          <p className="mt-2 text-sm text-muted-foreground">
            Set <code className="font-mono">SIMULATOR_ENABLED=true</code> in the server environment and restart to use the demo simulator.
          </p>
        </div>
      )}
    </DashboardFrame>
  )
}
