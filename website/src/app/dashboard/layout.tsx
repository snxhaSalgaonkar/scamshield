import { DashboardFrame } from '@/components/DashboardFrame'
import { isMock } from '@/lib/env'

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const simulatorEnabled = isMock || process.env.SIMULATOR_ENABLED === 'true'
  return <DashboardFrame simulatorEnabled={simulatorEnabled}>{children}</DashboardFrame>
}
