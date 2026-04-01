import {
  createFileRoute,
  Outlet,
  Link,
  useNavigate,
  useMatchRoute,
  useRouterState,
} from '@tanstack/react-router'
import { useState, useEffect } from 'react'
import { fetchBranding, logout } from '../../lib/api'
import type { Branding } from '../../lib/api'

export const Route = createFileRoute('/$tenant')({
  component: TenantLayout,
})

const navItems = [
  { label: 'Oversikt', path: 'dashboard', icon: HomeIcon },
  { label: 'Kontoer', path: 'accounts', icon: WalletIcon },
  { label: 'Betalinger', path: 'payments', icon: CreditCardIcon },
  { label: 'Overforinger', path: 'transfers', icon: ArrowsIcon },
  { label: 'Kort', path: 'cards', icon: CardIcon },
] as const

function TenantLayout() {
  const { tenant } = Route.useParams()
  const navigate = useNavigate()
  const matchRoute = useMatchRoute()
  const routerState = useRouterState()
  const pathname = routerState.location.pathname
  const isLoginPage = pathname.endsWith('/login')
  const isJoinPage = pathname.endsWith('/join')
  const isAdminPage = pathname.endsWith('/admin')
  const isFullscreenPage = isLoginPage || isJoinPage || isAdminPage
  const [branding, setBranding] = useState<Branding | null>(null)
  const [mobileNavOpen, setMobileNavOpen] = useState(false)
  const [loggingOut, setLoggingOut] = useState(false)

  useEffect(() => {
    fetchBranding(tenant)
      .then(setBranding)
      .catch(() => {
        setBranding({
          bankName: tenant.charAt(0).toUpperCase() + tenant.slice(1),
          primaryColor: '#0074cd',
        })
      })
  }, [tenant])

  const primaryColor = branding?.primaryColor || '#0074cd'

  // Set CSS custom property for tenant color
  useEffect(() => {
    document.documentElement.style.setProperty('--tenant-primary', primaryColor)
  }, [primaryColor])

  async function handleLogout() {
    setLoggingOut(true)
    try {
      await logout(tenant)
    } catch {
      // Ignore logout errors
    }
    navigate({ to: '/$tenant/login', params: { tenant } })
  }

  function isActive(path: string) {
    if (path === 'dashboard') {
      return !!matchRoute({ to: '/$tenant/dashboard', params: { tenant } })
    }
    if (path === 'accounts') {
      return !!matchRoute({ to: '/$tenant/accounts', params: { tenant }, fuzzy: true })
    }
    if (path === 'payments') {
      return !!matchRoute({ to: '/$tenant/payments', params: { tenant }, fuzzy: true })
    }
    if (path === 'transfers') {
      return !!matchRoute({ to: '/$tenant/transfers', params: { tenant } })
    }
    if (path === 'cards') {
      return !!matchRoute({ to: '/$tenant/cards', params: { tenant } })
    }
    return false
  }

  function linkTo(path: string) {
    if (path === 'accounts') return '/$tenant/accounts' as const
    if (path === 'payments') return '/$tenant/payments' as const
    if (path === 'transfers') return '/$tenant/transfers' as const
    if (path === 'cards') return '/$tenant/cards' as const
    return '/$tenant/dashboard' as const
  }

  // Login, join, and admin pages get their own full-page layout
  if (isFullscreenPage) {
    return <Outlet />
  }

  return (
    <div className="flex min-h-screen flex-col bg-slate-50">
      {/* Header */}
      <header
        className="sticky top-0 z-30 border-b border-white/20 shadow-sm"
        style={{ backgroundColor: primaryColor }}
      >
        <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6">
          <div className="flex items-center gap-3">
            {branding?.logoUrl ? (
              <img src={branding.logoUrl} alt="" className="h-8 object-contain" />
            ) : (
              <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-white/20 text-lg font-bold text-white">
                {(branding?.bankName || tenant).charAt(0).toUpperCase()}
              </div>
            )}
            <span className="text-lg font-bold text-white">
              {branding?.bankName || tenant}
            </span>
          </div>

          {/* Desktop nav */}
          <nav className="hidden items-center gap-1 md:flex">
            {navItems.map(({ label, path, icon: Icon }) => {
              const active = isActive(path)
              return (
                <Link
                  key={path}
                  to={linkTo(path)}
                  params={{ tenant }}
                  className={`flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium no-underline transition ${
                    active
                      ? 'bg-white/25 text-white'
                      : 'text-white/75 hover:bg-white/15 hover:text-white'
                  }`}
                  onClick={() => setMobileNavOpen(false)}
                >
                  <Icon className="h-4 w-4" />
                  {label}
                </Link>
              )
            })}
          </nav>

          <div className="flex items-center gap-2">
            <button
              onClick={handleLogout}
              disabled={loggingOut}
              className="hidden items-center gap-1.5 rounded-lg bg-white/15 px-3 py-2 text-sm font-medium text-white no-underline transition hover:bg-white/25 md:flex"
            >
              <LogoutIcon className="h-4 w-4" />
              Logg ut
            </button>

            {/* Mobile menu button */}
            <button
              onClick={() => setMobileNavOpen(!mobileNavOpen)}
              className="flex h-9 w-9 items-center justify-center rounded-lg bg-white/15 text-white md:hidden"
            >
              {mobileNavOpen ? (
                <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                </svg>
              ) : (
                <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M4 6h16M4 12h16M4 18h16" />
                </svg>
              )}
            </button>
          </div>
        </div>

        {/* Mobile nav */}
        {mobileNavOpen && (
          <nav className="border-t border-white/15 px-4 py-3 md:hidden">
            {navItems.map(({ label, path, icon: Icon }) => {
              const active = isActive(path)
              return (
                <Link
                  key={path}
                  to={linkTo(path)}
                  params={{ tenant }}
                  className={`flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium no-underline transition ${
                    active
                      ? 'bg-white/25 text-white'
                      : 'text-white/75 hover:bg-white/15 hover:text-white'
                  }`}
                  onClick={() => setMobileNavOpen(false)}
                >
                  <Icon className="h-4 w-4" />
                  {label}
                </Link>
              )
            })}
            <button
              onClick={handleLogout}
              disabled={loggingOut}
              className="mt-2 flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium text-white/75 transition hover:bg-white/15 hover:text-white"
            >
              <LogoutIcon className="h-4 w-4" />
              Logg ut
            </button>
          </nav>
        )}
      </header>

      {/* Main content */}
      <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-6 sm:px-6 sm:py-8">
        <Outlet />
      </main>

      {/* Footer */}
      <footer className="border-t border-slate-200 bg-white px-4 py-4">
        <div className="mx-auto max-w-7xl text-center text-xs text-slate-400">
          {branding?.bankName || tenant} Nettbank - Demo
        </div>
      </footer>
    </div>
  )
}

// --- Icon components ---

function HomeIcon({ className }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
    </svg>
  )
}

function WalletIcon({ className }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z" />
    </svg>
  )
}

function CreditCardIcon({ className }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M17 9V7a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2m2 4h10a2 2 0 002-2v-6a2 2 0 00-2-2H9a2 2 0 00-2 2v6a2 2 0 002 2zm7-5a2 2 0 11-4 0 2 2 0 014 0z" />
    </svg>
  )
}

function ArrowsIcon({ className }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4" />
    </svg>
  )
}

function CardIcon({ className }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z" />
    </svg>
  )
}

function LogoutIcon({ className }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
    </svg>
  )
}
