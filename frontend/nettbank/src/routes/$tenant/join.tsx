import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useState, useEffect } from 'react'
import { fetchBranding, requestMembership, getMembership } from '../../lib/api'
import type { Branding, Membership } from '../../lib/api'

export const Route = createFileRoute('/$tenant/join')({
  component: JoinBankPage,
})

function JoinBankPage() {
  const { tenant } = Route.useParams()
  const navigate = useNavigate()

  const [branding, setBranding] = useState<Branding | null>(null)
  const [membership, setMembership] = useState<Membership | null>(null)
  const [checkingStatus, setCheckingStatus] = useState(true)
  const [displayName, setDisplayName] = useState('')
  const [email, setEmail] = useState('')
  const [message, setMessage] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [submitted, setSubmitted] = useState(false)

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

  useEffect(() => {
    getMembership(tenant)
      .then((m) => {
        setMembership(m)
        if (m.status === 'APPROVED') {
          navigate({ to: '/$tenant/dashboard', params: { tenant } })
        }
        setCheckingStatus(false)
      })
      .catch(() => {
        // No existing membership
        setCheckingStatus(false)
      })
  }, [tenant, navigate])

  const primaryColor = branding?.primaryColor || '#0074cd'

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!displayName.trim() || !email.trim()) {
      setError('Please fill in your name and email.')
      return
    }

    setSubmitting(true)
    setError(null)

    try {
      const result = await requestMembership(tenant, {
        displayName: displayName.trim(),
        email: email.trim(),
        message: message.trim() || undefined,
      })
      setMembership(result)
      setSubmitted(true)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to submit request. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  if (checkingStatus) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-950">
        <div className="flex items-center gap-3 text-slate-400">
          <svg className="h-5 w-5 animate-spin" viewBox="0 0 24 24" fill="none">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
          </svg>
          Checking membership status...
        </div>
      </div>
    )
  }

  // Pending approval state
  if (membership?.status === 'PENDING' || submitted) {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center bg-slate-950 px-4">
        <div className="animate-fade-in w-full max-w-md text-center">
          <div className="mx-auto mb-6 flex h-20 w-20 items-center justify-center rounded-2xl bg-amber-500/10 text-amber-400">
            <svg className="h-10 w-10" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          </div>
          <h1 className="text-2xl font-bold text-white">Request Pending</h1>
          <p className="mt-3 text-slate-400">
            Your membership request for <span className="font-medium text-white">{branding?.bankName || tenant}</span> has been submitted. The bank owner will review it shortly.
          </p>
          <Link
            to="/"
            className="mt-8 inline-flex items-center gap-2 rounded-xl border border-slate-700 bg-slate-800/50 px-6 py-2.5 text-sm font-medium text-slate-200 no-underline transition hover:border-slate-600 hover:bg-slate-800"
          >
            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
            </svg>
            Back to Home
          </Link>
        </div>
      </div>
    )
  }

  // Rejected state
  if (membership?.status === 'REJECTED') {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center bg-slate-950 px-4">
        <div className="animate-fade-in w-full max-w-md text-center">
          <div className="mx-auto mb-6 flex h-20 w-20 items-center justify-center rounded-2xl bg-red-500/10 text-red-400">
            <svg className="h-10 w-10" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M18.364 18.364A9 9 0 005.636 5.636m12.728 12.728A9 9 0 015.636 5.636m12.728 12.728L5.636 5.636" />
            </svg>
          </div>
          <h1 className="text-2xl font-bold text-white">Request Rejected</h1>
          <p className="mt-3 text-slate-400">
            Your membership request for <span className="font-medium text-white">{branding?.bankName || tenant}</span> was not approved. Please contact the bank owner for more information.
          </p>
          <Link
            to="/"
            className="mt-8 inline-flex items-center gap-2 rounded-xl border border-slate-700 bg-slate-800/50 px-6 py-2.5 text-sm font-medium text-slate-200 no-underline transition hover:border-slate-600 hover:bg-slate-800"
          >
            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
            </svg>
            Back to Home
          </Link>
        </div>
      </div>
    )
  }

  // Join form
  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-slate-950 px-4">
      <Link
        to="/"
        className="mb-8 flex items-center gap-1 text-sm text-slate-500 no-underline transition-colors hover:text-slate-300"
      >
        <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
        </svg>
        Back to all banks
      </Link>

      <div className="w-full max-w-md animate-fade-in">
        <div className="overflow-hidden rounded-2xl border border-slate-800 bg-slate-900/60 shadow-xl backdrop-blur-sm">
          {/* Bank header */}
          <div className="px-8 py-6 text-center" style={{ backgroundColor: primaryColor }}>
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

          {/* Join form */}
          <form onSubmit={handleSubmit} className="px-8 py-8">
            <h2 className="mb-2 text-center text-lg font-semibold text-white">
              Request Membership
            </h2>
            <p className="mb-6 text-center text-sm text-slate-400">
              Fill in your details to join this bank.
            </p>

            {error && (
              <div className="mb-4 rounded-lg border border-red-900/50 bg-red-950/50 px-4 py-3 text-sm text-red-300">
                {error}
              </div>
            )}

            <div className="space-y-4">
              <div>
                <label className="mb-1.5 block text-sm font-medium text-slate-300">
                  Display Name
                </label>
                <input
                  type="text"
                  value={displayName}
                  onChange={(e) => setDisplayName(e.target.value)}
                  placeholder="Your name"
                  className="w-full rounded-xl border border-slate-700 bg-slate-800 px-4 py-3 text-white placeholder-slate-500 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                  autoFocus
                />
              </div>

              <div>
                <label className="mb-1.5 block text-sm font-medium text-slate-300">
                  Email
                </label>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="you@example.com"
                  className="w-full rounded-xl border border-slate-700 bg-slate-800 px-4 py-3 text-white placeholder-slate-500 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                />
              </div>

              <div>
                <label className="mb-1.5 block text-sm font-medium text-slate-300">
                  Message (optional)
                </label>
                <textarea
                  value={message}
                  onChange={(e) => setMessage(e.target.value)}
                  placeholder="Why would you like to join?"
                  rows={3}
                  className="w-full resize-none rounded-xl border border-slate-700 bg-slate-800 px-4 py-3 text-white placeholder-slate-500 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                />
              </div>
            </div>

            <button
              type="submit"
              disabled={submitting || !displayName.trim() || !email.trim()}
              className="mt-6 w-full rounded-xl px-6 py-3.5 text-base font-semibold text-white shadow-lg transition-all hover:-translate-y-0.5 hover:shadow-xl disabled:opacity-50 disabled:hover:translate-y-0 disabled:cursor-not-allowed"
              style={{
                backgroundColor: primaryColor,
                boxShadow: `0 8px 24px -4px ${primaryColor}40`,
              }}
            >
              {submitting ? (
                <span className="flex items-center justify-center gap-2">
                  <svg className="h-5 w-5 animate-spin" viewBox="0 0 24 24" fill="none">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                  </svg>
                  Submitting...
                </span>
              ) : (
                'Request to Join'
              )}
            </button>
          </form>
        </div>

        <p className="mt-6 text-center text-xs text-slate-500">
          Already a member?{' '}
          <Link
            to="/$tenant/login"
            params={{ tenant }}
            className="text-blue-400 no-underline hover:text-blue-300"
          >
            Log in instead
          </Link>
        </p>
      </div>
    </div>
  )
}
