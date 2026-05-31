import type { ReactNode } from 'react'
import { Links, Meta, Outlet, Scripts, ScrollRestoration, type LinksFunction } from 'react-router'
import { AuthProvider } from '@/contexts/AuthContext'
import { ThemeProvider } from '@/contexts/ThemeContext'
import { Toaster } from '@/components/ui/sonner'
import appStylesHref from '../src/index.css?url'
import mapboxStylesHref from 'mapbox-gl/dist/mapbox-gl.css?url'

export const links: LinksFunction = () => [
  { rel: 'stylesheet', href: appStylesHref },
  { rel: 'stylesheet', href: mapboxStylesHref },
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
  return (
    <ThemeProvider>
      <AuthProvider>
        <Outlet />
        <Toaster />
      </AuthProvider>
    </ThemeProvider>
  )
}
