import { supabaseDataSource } from './data.supabase'
import type { DataSource } from './types'

// Every page reads through `dataSource`: the shared Supabase project the phones report into.
export const dataSource: DataSource = supabaseDataSource

export * from './types'
