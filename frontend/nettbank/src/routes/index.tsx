import { createFileRoute, Link } from '@tanstack/react-router'
import { useState, useEffect, useRef } from 'react'
import type { PublicBank, AuthUser, MyBank } from '../lib/api'
import { fetchPublicBanks, fetchMe, fetchMyBanks } from '../lib/api'

export const Route = createFileRoute('/')({
  component: LandingPage,
})

function LandingPage() {
  const [banks, setBanks] = useState<PublicBank[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [user, setUser] = useState<AuthUser | null>(null)
  const [myBanks, setMyBanks] = useState<MyBank[]>([])
  const [banksDropdownOpen, setBanksDropdownOpen] = useState(false)
  const [copiedId, setCopiedId] = useState<string | null>(null)
  const dropdownRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    fetchPublicBanks()
      .then((data) => {
        setBanks(data)
        setLoading(false)
      })
      .catch(() => {
        setError('Could not connect to server')
        setLoading(false)
      })
    fetchMe().then((u) => {
      setUser(u)
      if (u.authenticated) fetchMyBanks().then(setMyBanks)
    })
  }, [])

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setBanksDropdownOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [])

  function copyBankLink(bankId: string) {
    const url = `${window.location.origin}/${bankId}/login`
    navigator.clipboard.writeText(url)
    setCopiedId(bankId)
    setTimeout(() => setCopiedId(null), 2000)
  }

  return (
    <div className="min-h-screen bg-slate-950 text-white">
      {/* Navigation */}
      <nav className="relative z-10 mx-auto flex max-w-7xl items-center justify-between px-6 py-5">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-blue-500 to-violet-600 shadow-lg shadow-blue-500/25">
            <svg className="h-6 w-6 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 21v-2m0-14V3m9 9h-2M5 12H3m15.364 6.364l-1.414-1.414M7.05 7.05L5.636 5.636m12.728 0l-1.414 1.414M7.05 16.95l-1.414 1.414" />
            </svg>
          </div>
          <span className="text-xl font-bold tracking-tight">KodaBank</span>
        </div>
        <div className="flex items-center gap-3">
          {user?.authenticated ? (
            <>
              {myBanks.length > 0 && (
                <div className="relative" ref={dropdownRef}>
                  <button
                    onClick={() => setBanksDropdownOpen((o) => !o)}
                    className="flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm font-medium text-slate-300 transition hover:bg-slate-800 hover:text-white"
                  >
                    My Banks
                    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                      <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
                    </svg>
                  </button>
                  {banksDropdownOpen && (
                    <div className="absolute right-0 top-full mt-2 w-64 rounded-xl border border-slate-700 bg-slate-900 shadow-2xl shadow-black/50 z-50">
                      <div className="p-1.5">
                        {myBanks.map((bank) => (
                          <div key={bank.id} className="rounded-lg p-2 hover:bg-slate-800">
                            <div className="flex items-center gap-2">
                              <div
                                className="h-7 w-7 flex-shrink-0 rounded-lg"
                                style={{ backgroundColor: bank.primaryColor || '#6366f1' }}
                              />
                              <a
                                href={`/${bank.id}/login`}
                                className="flex-1 text-sm font-medium text-slate-200 no-underline hover:text-white"
                              >
                                {bank.name}
                              </a>
                              <a
                                href={`/${bank.id}/admin`}
                                title="Admin panel"
                                className="rounded-md p-1 text-slate-500 no-underline transition hover:bg-slate-700 hover:text-slate-300"
                              >
                                <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                                  <path strokeLinecap="round" strokeLinejoin="round" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
                                  <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                                </svg>
                              </a>
                              <button
                                onClick={() => copyBankLink(bank.id)}
                                title="Copy shareable link"
                                className="rounded-md p-1 text-slate-500 transition hover:bg-slate-700 hover:text-slate-300"
                              >
                                {copiedId === bank.id ? (
                                  <svg className="h-4 w-4 text-emerald-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                                    <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                                  </svg>
                                ) : (
                                  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                                    <path strokeLinecap="round" strokeLinejoin="round" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
                                  </svg>
                                )}
                              </button>
                            </div>
                          </div>
                        ))}
                        <div className="mt-1 border-t border-slate-800 pt-1">
                          <Link
                            to="/create-bank"
                            className="flex items-center gap-2 rounded-lg p-2 text-sm text-slate-400 no-underline hover:bg-slate-800 hover:text-slate-200"
                          >
                            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                              <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
                            </svg>
                            Create another bank
                          </Link>
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              )}
              {myBanks.length === 0 && (
                <Link
                  to="/create-bank"
                  className="rounded-lg px-4 py-2 text-sm font-medium text-slate-300 no-underline transition hover:text-white"
                >
                  Create Bank
                </Link>
              )}
              <div className="flex items-center gap-2 rounded-lg border border-slate-700 bg-slate-800/50 px-3 py-2">
                <div className="flex h-6 w-6 items-center justify-center rounded-full bg-blue-600 text-xs font-bold text-white">
                  {(user.firstName?.[0] || user.username?.[0] || '?').toUpperCase()}
                </div>
                <span className="text-sm text-slate-200">
                  {user.firstName ? `${user.firstName} ${user.lastName ?? ''}`.trim() : user.username}
                </span>
              </div>
            </>
          ) : (
            <a
              href="http://localhost:8085/api/v1/auth/login"
              className="rounded-lg border border-slate-700 bg-slate-800/50 px-4 py-2 text-sm font-medium text-slate-200 no-underline transition hover:border-slate-600 hover:bg-slate-800"
            >
              Log in
            </a>
          )}
        </div>
      </nav>

      {/* Hero Section */}
      <section className="relative overflow-hidden px-6 pb-20 pt-16 sm:pt-24">
        {/* Background glow effects */}
        <div className="pointer-events-none absolute inset-0 overflow-hidden">
          <div className="absolute -top-40 left-1/2 h-[600px] w-[800px] -translate-x-1/2 rounded-full bg-blue-600/10 blur-3xl" />
          <div className="absolute -top-20 left-1/3 h-[400px] w-[600px] -translate-x-1/2 rounded-full bg-violet-600/8 blur-3xl" />
        </div>

        <div className="relative mx-auto max-w-4xl text-center">
          <div className="mb-6 inline-flex items-center gap-2 rounded-full border border-slate-700/60 bg-slate-800/60 px-4 py-1.5 text-sm text-slate-300 backdrop-blur-sm">
            <span className="h-2 w-2 rounded-full bg-emerald-400 animate-pulse" />
            Virtual banking for games and simulations
          </div>

          <h1 className="animate-fade-in text-5xl font-extrabold leading-tight tracking-tight sm:text-6xl lg:text-7xl">
            <span className="bg-gradient-to-r from-white via-slate-200 to-slate-400 bg-clip-text text-transparent">
              Gaming Banking
            </span>
            <br />
            <span className="bg-gradient-to-r from-blue-400 via-violet-400 to-purple-400 bg-clip-text text-transparent">
              Platform
            </span>
          </h1>

          <p className="animate-fade-in mx-auto mt-6 max-w-2xl text-lg leading-relaxed text-slate-400 sm:text-xl" style={{ animationDelay: '100ms' }}>
            Create and run virtual banks for your games, classroom simulations, and events.
            Full banking features -- accounts, transfers, cards, and payments -- all in a safe sandbox.
          </p>

          <div className="animate-fade-in mt-10 flex flex-col items-center justify-center gap-4 sm:flex-row" style={{ animationDelay: '200ms' }}>
            <Link
              to="/create-bank"
              className="group flex items-center gap-2 rounded-xl bg-gradient-to-r from-blue-600 to-violet-600 px-8 py-3.5 text-base font-semibold text-white no-underline shadow-lg shadow-blue-600/25 transition-all hover:-translate-y-0.5 hover:shadow-xl hover:shadow-blue-600/30"
            >
              Create Your Own Bank
              <svg className="h-5 w-5 transition-transform group-hover:translate-x-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M13 7l5 5m0 0l-5 5m5-5H6" />
              </svg>
            </Link>
            <a
              href="#banks"
              className="rounded-xl border border-slate-700 bg-slate-800/50 px-8 py-3.5 text-base font-semibold text-slate-200 no-underline transition-all hover:-translate-y-0.5 hover:border-slate-600 hover:bg-slate-800"
            >
              Browse Banks
            </a>
          </div>
        </div>

        {/* Feature highlights */}
        <div className="relative mx-auto mt-24 grid max-w-5xl gap-6 sm:grid-cols-3">
          {[
            {
              icon: (
                <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M12 21v-8.25M15.75 21v-8.25M8.25 21v-8.25M3 9l9-6 9 6m-1.5 12V10.332A48.36 48.36 0 0012 9.75c-2.551 0-5.056.2-7.5.582V21M3 21h18M12 6.75h.008v.008H12V6.75z" />
                </svg>
              ),
              title: 'Multi-Tenant Banks',
              desc: 'Each bank is an isolated tenant with its own branding, accounts, and policies.',
            },
            {
              icon: (
                <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M7.5 21L3 16.5m0 0L7.5 12M3 16.5h13.5m0-13.5L21 7.5m0 0L16.5 12M21 7.5H7.5" />
                </svg>
              ),
              title: 'Real-Time Transfers',
              desc: 'Instant account-to-account transfers, scheduled payments, and cross-bank transactions.',
            },
            {
              icon: (
                <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M9.594 3.94c.09-.542.56-.94 1.11-.94h2.593c.55 0 1.02.398 1.11.94l.213 1.281c.063.374.313.686.645.87.074.04.147.083.22.127.324.196.72.257 1.075.124l1.217-.456a1.125 1.125 0 011.37.49l1.296 2.247a1.125 1.125 0 01-.26 1.431l-1.003.827c-.293.24-.438.613-.431.992a6.759 6.759 0 010 .255c-.007.378.138.75.43.99l1.005.828c.424.35.534.954.26 1.43l-1.298 2.247a1.125 1.125 0 01-1.369.491l-1.217-.456c-.355-.133-.75-.072-1.076.124a6.57 6.57 0 01-.22.128c-.331.183-.581.495-.644.869l-.213 1.28c-.09.543-.56.941-1.11.941h-2.594c-.55 0-1.02-.398-1.11-.94l-.213-1.281c-.062-.374-.312-.686-.644-.87a6.52 6.52 0 01-.22-.127c-.325-.196-.72-.257-1.076-.124l-1.217.456a1.125 1.125 0 01-1.369-.49l-1.297-2.247a1.125 1.125 0 01.26-1.431l1.004-.827c.292-.24.437-.613.43-.992a6.932 6.932 0 010-.255c.007-.378-.138-.75-.43-.99l-1.004-.828a1.125 1.125 0 01-.26-1.43l1.297-2.247a1.125 1.125 0 011.37-.491l1.216.456c.356.133.751.072 1.076-.124.072-.044.146-.087.22-.128.332-.183.582-.495.644-.869l.214-1.281z" />
                  <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                </svg>
              ),
              title: 'Full Control',
              desc: 'Configure access policies, transfer rules, and custom branding for your bank.',
            },
          ].map((feature, i) => (
            <div
              key={feature.title}
              className="animate-fade-in rounded-2xl border border-slate-800 bg-slate-900/60 p-6 backdrop-blur-sm transition hover:border-slate-700"
              style={{ animationDelay: `${(i + 3) * 100}ms` }}
            >
              <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-xl bg-slate-800 text-blue-400">
                {feature.icon}
              </div>
              <h3 className="text-base font-semibold text-white">{feature.title}</h3>
              <p className="mt-2 text-sm leading-relaxed text-slate-400">{feature.desc}</p>
            </div>
          ))}
        </div>
      </section>

      {/* Banks Grid Section */}
      <section id="banks" className="px-6 pb-24 pt-8">
        <div className="mx-auto max-w-7xl">
          <div className="mb-10 text-center">
            <h2 className="text-3xl font-bold tracking-tight text-white sm:text-4xl">
              Active Banks
            </h2>
            <p className="mt-3 text-lg text-slate-400">
              Join an existing bank or create your own
            </p>
          </div>

          {loading && (
            <div className="flex items-center justify-center gap-3 py-16 text-slate-400">
              <svg className="h-5 w-5 animate-spin" viewBox="0 0 24 24" fill="none">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
              </svg>
              Loading banks...
            </div>
          )}

          {error && (
            <div className="mx-auto max-w-md rounded-xl border border-red-900/50 bg-red-950/50 px-6 py-4 text-center text-red-300">
              <div className="flex items-center justify-center gap-2">
                <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v2m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                {error}
              </div>
            </div>
          )}

          {!loading && !error && banks.length === 0 && (
            <div className="mx-auto max-w-md rounded-xl border border-slate-800 bg-slate-900/60 px-8 py-12 text-center">
              <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-2xl bg-slate-800 text-slate-500">
                <svg className="h-8 w-8" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M12 21v-8.25M15.75 21v-8.25M8.25 21v-8.25M3 9l9-6 9 6m-1.5 12V10.332A48.36 48.36 0 0012 9.75c-2.551 0-5.056.2-7.5.582V21M3 21h18M12 6.75h.008v.008H12V6.75z" />
                </svg>
              </div>
              <p className="text-lg font-medium text-slate-300">No banks yet</p>
              <p className="mt-2 text-sm text-slate-500">Be the first to create a virtual bank on the platform.</p>
              <Link
                to="/create-bank"
                className="mt-6 inline-flex items-center gap-2 rounded-xl bg-gradient-to-r from-blue-600 to-violet-600 px-6 py-2.5 text-sm font-semibold text-white no-underline transition hover:-translate-y-0.5"
              >
                Create a Bank
                <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
                </svg>
              </Link>
            </div>
          )}

          <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {banks.map((bank, i) => {
              const color = bank.primaryColor || '#0074cd'
              return (
                <Link
                  key={bank.id}
                  to="/$tenant/login"
                  params={{ tenant: bank.id }}
                  className="animate-fade-in group relative overflow-hidden rounded-2xl border border-slate-800 bg-slate-900/60 p-6 no-underline backdrop-blur-sm transition-all duration-200 hover:-translate-y-1 hover:border-slate-700 hover:shadow-lg hover:shadow-black/20"
                  style={{ animationDelay: `${(i + 1) * 80}ms` }}
                >
                  {/* Color accent bar */}
                  <div
                    className="absolute inset-x-0 top-0 h-1 transition-all group-hover:h-1.5"
                    style={{ backgroundColor: color }}
                  />

                  <div className="mb-4 mt-2 flex items-center gap-3">
                    {bank.logoUrl ? (
                      <img
                        src={bank.logoUrl}
                        alt={bank.name}
                        className="h-11 w-11 rounded-xl object-contain"
                      />
                    ) : (
                      <div
                        className="flex h-11 w-11 items-center justify-center rounded-xl text-lg font-bold text-white shadow-inner"
                        style={{ backgroundColor: color }}
                      >
                        {bank.name.charAt(0)}
                      </div>
                    )}
                    <div>
                      <h3 className="text-lg font-semibold text-white">
                        {bank.name}
                      </h3>
                    </div>
                  </div>

                  {bank.tagline && (
                    <p className="text-sm leading-relaxed text-slate-400">
                      {bank.tagline}
                    </p>
                  )}

                  <div className="mt-5 flex items-center justify-between">
                    <span
                      className="flex items-center gap-1 text-sm font-medium transition-colors"
                      style={{ color }}
                    >
                      Enter Bank
                      <svg className="h-4 w-4 transition-transform group-hover:translate-x-1" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                        <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
                      </svg>
                    </span>
                  </div>
                </Link>
              )
            })}

            {/* Create bank CTA card */}
            {!loading && !error && banks.length > 0 && (
              <Link
                to="/create-bank"
                className="animate-fade-in group flex flex-col items-center justify-center rounded-2xl border border-dashed border-slate-700 bg-slate-900/30 p-6 no-underline transition-all duration-200 hover:-translate-y-1 hover:border-slate-600 hover:bg-slate-900/50"
                style={{ animationDelay: `${(banks.length + 1) * 80}ms` }}
              >
                <div className="mb-3 flex h-14 w-14 items-center justify-center rounded-2xl border border-slate-700 bg-slate-800 text-slate-400 transition group-hover:border-blue-600/50 group-hover:bg-blue-600/10 group-hover:text-blue-400">
                  <svg className="h-7 w-7" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
                  </svg>
                </div>
                <p className="text-sm font-semibold text-slate-300 transition group-hover:text-white">
                  Create Your Own Bank
                </p>
                <p className="mt-1 text-xs text-slate-500">
                  Set up in minutes
                </p>
              </Link>
            )}
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="border-t border-slate-800/60 px-6 py-8">
        <div className="mx-auto max-w-7xl text-center text-sm text-slate-500">
          KodaBank — Virtual banking platform for games and simulations
          <span className="mx-2 text-slate-700">·</span>
          <span className="inline-flex items-center gap-1 text-slate-600">
            <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M20.25 6.375c0 2.278-3.694 4.125-8.25 4.125S3.75 8.653 3.75 6.375m16.5 0c0-2.278-3.694-4.125-8.25-4.125S3.75 4.097 3.75 6.375m16.5 0v11.25c0 2.278-3.694 4.125-8.25 4.125s-8.25-1.847-8.25-4.125V6.375m16.5 5.625c0 2.278-3.694 4.125-8.25 4.125s-8.25-1.847-8.25-4.125" />
            </svg>
            Backed by KodaStore
          </span>
        </div>
      </footer>
    </div>
  )
}
