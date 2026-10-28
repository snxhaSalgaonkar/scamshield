import { mockDataSource } from './data.mock'
import { supabaseDataSource } from './data.supabase'
import { isMock } from './env'
import type { DataSource } from './types'

// NEXT_PUBLIC_DATA_SOURCE: "mock" (default, no backend) | "supabase".
// Every page goes through `dataSource`, so switching needs no other code changes.
export const dataSource: DataSource = isMock ? mockDataSource : supabaseDataSource

export { isMock }
export * from './types'
