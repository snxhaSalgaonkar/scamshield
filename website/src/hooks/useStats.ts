import { useQuery } from '@tanstack/react-query'
import { dataSource } from '@/lib/data'

export function useStats() {
  return useQuery({ queryKey: ['stats'], queryFn: () => dataSource.getStats() })
}
