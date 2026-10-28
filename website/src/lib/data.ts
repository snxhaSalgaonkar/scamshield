import { apiDataSource } from './data.api'
import { mockDataSource } from './data.mock'
import type { DataSource } from './types'

// VITE_DATA_SOURCE: "mock" (default, no backend needed) | "api" (FastAPI backend at VITE_API_URL).
// Every page goes through `dataSource`, so switching backends needs no other code changes.
export const isMock = (import.meta.env.VITE_DATA_SOURCE ?? 'mock') !== 'api'

export const dataSource: DataSource = isMock ? mockDataSource : apiDataSource

export * from './types'
