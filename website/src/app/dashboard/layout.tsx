import { DashboardFrame } from '@/components/DashboardFrame'

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  return <DashboardFrame>{children}</DashboardFrame>
}
