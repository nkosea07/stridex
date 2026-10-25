import { useEffect, type ReactNode } from 'react'
import { Links, Meta, Outlet, Scripts, ScrollRestoration, type LinksFunction } from 'react-router'
import { I18nextProvider } from 'react-i18next'
import { AuthProvider } from '@/contexts/AuthContext'
import { ThemeProvider } from '@/contexts/ThemeContext'
import { Toaster } from '@/components/ui/sonner'
import i18n from '@/i18n'
import appStylesHref from '../src/index.css?url'
import mapboxStylesHref from 'mapbox-gl/dist/mapbox-gl.css?url'
import { PRODUCT } from '@/config/product'

export const links: LinksFunction = () => [
  { rel: 'stylesheet', href: appStylesHref },
  { rel: 'stylesheet', href: mapboxStylesHref },
  { rel: 'manifest', href: '/manifest.webmanifest' },
]

export const meta = () => [
  { title: `${PRODUCT.name} | Race-day operations and live tracking` },
  { name: 'description', content: PRODUCT.description },
  { property: 'og:title', content: PRODUCT.name },
  { property: 'og:description', content: PRODUCT.description },
  { name: 'theme-color', content: '#0f172a' },
]

function Document({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <head>
        <meta charSet="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        <Meta />
        <Links />
      </head>
      <body>
        {children}
        <ScrollRestoration />
        <Scripts />
      </body>
    </html>
  )
}

export function Layout({ children }: { children: ReactNode }) {
  return <Document>{children}</Document>
}

export default function Root() {
  useEffect(() => {
    if ('serviceWorker' in navigator && import.meta.env.PROD) {
      void navigator.serviceWorker.register('/sw.js').catch(() => undefined)
    }
  }, [])

  return (
    <I18nextProvider i18n={i18n}>
      <ThemeProvider>
        <AuthProvider>
          <Outlet />
          <Toaster />
        </AuthProvider>
      </ThemeProvider>
    </I18nextProvider>
  )
}
