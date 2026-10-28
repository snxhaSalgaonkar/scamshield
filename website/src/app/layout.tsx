import type { Metadata, Viewport } from 'next'
import { Providers } from './providers'
import './globals.css'

export const metadata: Metadata = {
  title: { default: 'RakshaCall Authority Console', template: '%s · RakshaCall' },
  description: 'Live fraud-call intelligence for police and cyber-crime officials.',
  robots: { index: false, follow: false },
}

export const viewport: Viewport = { themeColor: '#07203F' }

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  )
}
