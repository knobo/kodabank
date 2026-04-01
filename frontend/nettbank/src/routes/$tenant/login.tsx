import { createFileRoute, useNavigate, Link } from '@tanstack/react-router'
import { useState, useEffect } from 'react'
import { login, fetchBranding, loginRedirect } from '../../lib/api'
import type { Branding } from '../../lib/api'

export const Route = createFileRoute('/$tenant/login')({
  component: LoginPage,
})

function LoginPage() {
  const { tenant } = Route.useParams()
  const navigate = useNavigate()

  const [branding, setBranding] = useState<Branding | null>(null)
  const [showDemoLogin, setShowDemoLogin] = useState(false)
  const [nationalId, setNationalId] = useState('')
  const [loading, setLoading] = useState(false)
  const [waitingBankId, setWaitingBankId] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchBranding(tenant)
      .then(setBranding)
      .catch(() => {
        // Use fallback branding
        setBranding({
          bankName: tenant.charAt(0).toUpperCase() + tenant.slice(1),
          primaryColor: '#0074cd',
        })
      })
  }, [tenant])

  const primaryColor = branding?.primaryColor || '#0074cd'

  function handleKeycloakLogin() {
    loginRedirect(tenant)
  }

  async function handleDemoSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!nationalId.trim() || nationalId.replace(/\s/g, '').length !== 11) {
      setError('Vennligst oppgi et gyldig fodselsnummer (11 siffer)')
      return
    }

    setError(null)
    setWaitingBankId(true)

    // Simulate BankID waiting
    await new Promise((r) => setTimeout(r, 2000))

    setWaitingBankId(false)
    setLoading(true)

    try {
      await login(tenant, nationalId.replace(/\s/g, ''))
      navigate({ to: '/$tenant/dashboard', params: { tenant } })
    } catch {
      setError('Innlogging feilet. Sjekk fodselsnummer og prov igjen.')
      setLoading(false)
    }
  }

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-gradient-to-br from-slate-50 via-blue-50 to-slate-100 px-4">
      <Link
        to="/"
        className="mb-8 text-sm text-slate-400 no-underline hover:text-slate-600 transition-colors flex items-center gap-1"
      >
        <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
        </svg>
        Velg en annen bank
      </Link>

      <div className="w-full max-w-md animate-fade-in">
        <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-xl shadow-slate-200/50">
          {/* Bank header */}
          <div
            className="px-8 py-6 text-center"
            style={{ backgroundColor: primaryColor }}
          >
            {branding?.logoUrl ? (
              <img
                src={branding.logoUrl}
                alt={branding.bankName}
                className="mx-auto mb-2 h-12 object-contain"
              />
            ) : (
              <div className="mx-auto mb-2 flex h-14 w-14 items-center justify-center rounded-xl bg-white/20 text-2xl font-bold text-white">
                {(branding?.bankName || tenant).charAt(0).toUpperCase()}
              </div>
            )}
            <h1 className="text-xl font-bold text-white">
              {branding?.bankName || tenant}
            </h1>
            {branding?.tagline && (
              <p className="mt-1 text-sm text-white/80">{branding.tagline}</p>
            )}
          </div>

          {/* BankID waiting overlay */}
          {waitingBankId && (
            <div className="flex flex-col items-center gap-4 px-8 py-12">
              <div className="flex h-16 w-16 items-center justify-center rounded-full bg-blue-50">
                <svg className="h-8 w-8 animate-spin text-blue-500" viewBox="0 0 24 24" fill="none">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                </svg>
              </div>
              <div className="text-center">
                <p className="text-lg font-semibold text-slate-800">
                  Venter pa BankID...
                </p>
                <p className="mt-1 text-sm text-slate-500">
                  Apne BankID-appen pa mobilen din
                </p>
              </div>
              <div className="flex gap-2">
                <span className="bankid-dot" style={{ backgroundColor: primaryColor }} />
                <span className="bankid-dot" style={{ backgroundColor: primaryColor }} />
                <span className="bankid-dot" style={{ backgroundColor: primaryColor }} />
              </div>
            </div>
          )}

          {/* Login options */}
          {!waitingBankId && !showDemoLogin && (
            <div className="px-8 py-8">
              <h2 className="mb-6 text-center text-lg font-semibold text-slate-800">
                Logg inn
              </h2>

              {error && (
                <div className="mb-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                  {error}
                </div>
              )}

              {/* Primary: Keycloak login */}
              <button
                onClick={handleKeycloakLogin}
                className="w-full rounded-xl px-6 py-3.5 text-base font-semibold text-white shadow-lg transition-all hover:-translate-y-0.5 hover:shadow-xl"
                style={{
                  backgroundColor: primaryColor,
                  boxShadow: `0 8px 24px -4px ${primaryColor}40`,
                }}
              >
                <span className="flex items-center justify-center gap-2">
                  <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                  </svg>
                  Login with Keycloak
                </span>
              </button>

              {/* Divider */}
              <div className="my-6 flex items-center gap-4">
                <div className="h-px flex-1 bg-slate-200" />
                <span className="text-xs font-medium text-slate-400">OR</span>
                <div className="h-px flex-1 bg-slate-200" />
              </div>

              {/* Secondary: Demo BankID */}
              <button
                onClick={() => setShowDemoLogin(true)}
                className="w-full rounded-xl border border-slate-300 bg-slate-50 px-6 py-3 text-sm font-medium text-slate-600 transition-all hover:border-slate-400 hover:bg-slate-100"
              >
                <span className="flex items-center justify-center gap-2">
                  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M15 9h3.75M15 12h3.75M15 15h3.75M4.5 19.5h15a2.25 2.25 0 002.25-2.25V6.75A2.25 2.25 0 0019.5 4.5h-15a2.25 2.25 0 00-2.25 2.25v10.5A2.25 2.25 0 004.5 19.5zm6-10.125a1.875 1.875 0 11-3.75 0 1.875 1.875 0 013.75 0zm1.294 6.336a6.721 6.721 0 01-3.17.789 6.721 6.721 0 01-3.168-.789 3.376 3.376 0 016.338 0z" />
                  </svg>
                  Demo Login (BankID)
                </span>
              </button>

              {/* Join link */}
              <div className="mt-6 text-center">
                <span className="text-sm text-slate-500">Ikke medlem? </span>
                <Link
                  to="/$tenant/join"
                  params={{ tenant }}
                  className="text-sm font-medium no-underline hover:underline"
                  style={{ color: primaryColor }}
                >
                  Bli med i banken
                </Link>
              </div>
            </div>
          )}

          {/* Demo BankID form */}
          {!waitingBankId && showDemoLogin && (
            <form onSubmit={handleDemoSubmit} className="px-8 py-8">
              <div className="mb-6 flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => { setShowDemoLogin(false); setError(null) }}
                  className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 transition hover:bg-slate-100 hover:text-slate-600"
                >
                  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
                  </svg>
                </button>
                <h2 className="text-lg font-semibold text-slate-800">
                  Demo Login - BankID
                </h2>
              </div>

              {error && (
                <div className="mb-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                  {error}
                </div>
              )}

              <label className="mb-2 block text-sm font-medium text-slate-700">
                Fodselsnummer
              </label>
              <input
                type="text"
                inputMode="numeric"
                maxLength={13}
                placeholder="12345678901"
                value={nationalId}
                onChange={(e) => setNationalId(e.target.value)}
                className="mb-6 w-full rounded-xl border border-slate-300 bg-slate-50 px-4 py-3 text-lg tracking-wider text-slate-800 placeholder-slate-400 outline-none transition focus:border-blue-400 focus:bg-white focus:ring-2 focus:ring-blue-100"
                disabled={loading}
                autoFocus
              />

              <button
                type="submit"
                disabled={loading || !nationalId.trim()}
                className="w-full rounded-xl px-6 py-3.5 text-base font-semibold text-white shadow-lg transition-all hover:-translate-y-0.5 hover:shadow-xl disabled:opacity-50 disabled:hover:translate-y-0 disabled:cursor-not-allowed"
                style={{
                  backgroundColor: primaryColor,
                  boxShadow: `0 8px 24px -4px ${primaryColor}40`,
                }}
              >
                {loading ? (
                  <span className="flex items-center justify-center gap-2">
                    <svg className="h-5 w-5 animate-spin" viewBox="0 0 24 24" fill="none">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                    </svg>
                    Logger inn...
                  </span>
                ) : (
                  'Logg inn'
                )}
              </button>

              <p className="mt-4 text-center text-xs text-slate-400">
                Ikke bruk ekte personnummer. Dette er en demo.
              </p>
            </form>
          )}
        </div>

        <p className="mt-6 text-center text-xs text-slate-400">
          Demo-applikasjon. Ikke bruk ekte personnummer.
        </p>
      </div>
    </div>
  )
}
