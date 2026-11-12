import type { Metadata, Viewport } from 'next'
import { Providers } from './providers'
import './globals.css'

export const metadata: Metadata = {
  title: { default: 'ScamShield — On-Device AI Call & SMS Protection', template: '%s · ScamShield' },
  description: 'On-device machine learning fraud defense against digital arrest, OTP theft, and scam calls.',
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
