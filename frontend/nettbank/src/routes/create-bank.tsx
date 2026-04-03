import { createFileRoute, Link } from '@tanstack/react-router'
import { useState, useEffect } from 'react'
import { registerBank, fetchMe, BFF_URL } from '../lib/api'
import type { RegisterBankData, RegisterBankResult, AuthUser } from '../lib/api'

export const Route = createFileRoute('/create-bank')({
  component: CreateBankPage,
})

const TOTAL_STEPS = 5

const ACCESS_POLICIES = [
  {
    value: 'AUTO_APPROVE' as const,
    label: 'Auto-Approve',
    desc: 'Anyone who requests membership is immediately approved.',
  },
  {
    value: 'MANUAL_APPROVAL' as const,
    label: 'Manual Approval',
    desc: 'Bank owner must approve each membership request.',
  },
  {
    value: 'WEBHOOK' as const,
    label: 'Webhook',
    desc: 'An external webhook decides whether to approve or reject.',
  },
]

const TRANSFER_POLICIES = [
  {
    value: 'OPEN' as const,
    label: 'Open',
    desc: 'Members can transfer to anyone, including other banks.',
  },
  {
    value: 'CLOSED' as const,
    label: 'Closed',
    desc: 'Transfers are only allowed within this bank.',
  },
  {
    value: 'WHITELIST' as const,
    label: 'Whitelist',
    desc: 'Only transfers to whitelisted banks or accounts are allowed.',
  },
  {
    value: 'DOMAIN_CODE' as const,
    label: 'Domain Code',
    desc: 'Transfers require a domain-specific code to authorize.',
  },
]

const PRESET_COLORS = [
  '#2563eb', '#7c3aed', '#db2777', '#ea580c',
  '#16a34a', '#0891b2', '#4f46e5', '#9333ea',
  '#e11d48', '#ca8a04', '#059669', '#0284c7',
]

type AccessPolicyType = 'AUTO_APPROVE' | 'MANUAL_APPROVAL' | 'WEBHOOK'
type TransferPolicyType = 'OPEN' | 'CLOSED' | 'WHITELIST' | 'DOMAIN_CODE'

interface FormData {
  bankName: string
  currency: string
  primaryColor: string
  tagline: string
  accessPolicy: AccessPolicyType
  transferPolicy: TransferPolicyType
}

function CreateBankPage() {
  const [step, setStep] = useState(1)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<RegisterBankResult | null>(null)
  const [user, setUser] = useState<AuthUser | null>(null)
  const [authChecked, setAuthChecked] = useState(false)

  useEffect(() => {
    const bffUrl = BFF_URL
    fetchMe().then((u) => {
      setUser(u)
      setAuthChecked(true)
      if (!u.authenticated) {
        window.location.href = `${bffUrl}/api/v1/auth/login?returnTo=/create-bank`
      }
    })
  }, [])

  const [form, setForm] = useState<FormData>({
    bankName: '',
    currency: 'NOK',
    primaryColor: '#2563eb',
    tagline: '',
    accessPolicy: 'AUTO_APPROVE',
    transferPolicy: 'OPEN',
  })

  function updateForm(patch: Partial<FormData>) {
    setForm((prev) => ({ ...prev, ...patch }))
  }

  function canAdvance(): boolean {
    if (step === 1) return form.bankName.trim().length >= 2
    return true
  }

  function next() {
    if (step < TOTAL_STEPS) setStep(step + 1)
  }

  function back() {
    if (step > 1) setStep(step - 1)
  }

  async function handleSubmit() {
    setSubmitting(true)
    setError(null)
    try {
      const data: RegisterBankData = {
        bankName: form.bankName.trim(),
        currency: form.currency,
        branding: {
          primaryColor: form.primaryColor,
          tagline: form.tagline.trim() || undefined,
        },
        accessPolicy: { type: form.accessPolicy },
        transferPolicy: { type: form.transferPolicy },
      }
      const res = await registerBank(data)
      setResult(res)
    } catch (err) {
      if (err instanceof Error && err.message.includes('401')) {
        // Not logged in, redirect to BFF login
        const bffUrl = BFF_URL
        window.location.href = `${bffUrl}/api/v1/auth/login?returnTo=/create-bank`
        return
      }
      setError(err instanceof Error ? err.message : 'Failed to create bank. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  if (!authChecked) {
    return <div className="flex min-h-screen items-center justify-center bg-slate-950" />
  }

  // Success state
  if (result) {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center bg-slate-950 px-4">
        <div className="animate-fade-in w-full max-w-lg text-center">
          <div className="mx-auto mb-6 flex h-20 w-20 items-center justify-center rounded-2xl bg-emerald-500/10 text-emerald-400">
            <svg className="h-10 w-10" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          </div>
          <h1 className="text-3xl font-bold text-white">Bank Created!</h1>
          <p className="mt-3 text-lg text-slate-400">
            <span className="font-semibold text-white">{result.bankName}</span> is ready to go.
          </p>

          <div className="mt-8 rounded-xl border border-slate-800 bg-slate-900/60 p-6">
            <p className="text-sm text-slate-400">Your bank URL</p>
            <p className="mt-2 rounded-lg bg-slate-800 px-4 py-3 font-mono text-sm text-blue-400">
              {window.location.origin}/{result.id}
            </p>
          </div>

          <div className="mt-8 flex flex-col gap-3 sm:flex-row sm:justify-center">
            <Link
              to="/$tenant/login"
              params={{ tenant: result.id }}
              className="rounded-xl bg-gradient-to-r from-blue-600 to-violet-600 px-6 py-3 text-sm font-semibold text-white no-underline transition hover:-translate-y-0.5"
            >
              Go to Your Bank
            </Link>
            <Link
              to="/"
              className="rounded-xl border border-slate-700 bg-slate-800/50 px-6 py-3 text-sm font-semibold text-slate-200 no-underline transition hover:border-slate-600 hover:bg-slate-800"
            >
              Back to Home
            </Link>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="flex min-h-screen flex-col bg-slate-950">
      {/* Header */}
      <nav className="mx-auto flex w-full max-w-7xl items-center justify-between px-6 py-5">
        <Link to="/" className="flex items-center gap-3 no-underline">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-blue-500 to-violet-600">
            <svg className="h-6 w-6 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 21v-2m0-14V3m9 9h-2M5 12H3m15.364 6.364l-1.414-1.414M7.05 7.05L5.636 5.636m12.728 0l-1.414 1.414M7.05 16.95l-1.414 1.414" />
            </svg>
          </div>
          <span className="text-xl font-bold tracking-tight text-white">KodaBank</span>
        </Link>
        <div className="flex items-center gap-3">
          {user?.authenticated && (
            <div className="flex items-center gap-2 rounded-lg border border-slate-700 bg-slate-800/50 px-3 py-2">
              <div className="flex h-6 w-6 items-center justify-center rounded-full bg-blue-600 text-xs font-bold text-white">
                {(user.firstName?.[0] || user.username?.[0] || '?').toUpperCase()}
              </div>
              <span className="text-sm text-slate-200">
                {user.firstName ? `${user.firstName} ${user.lastName ?? ''}`.trim() : user.username}
              </span>
            </div>
          )}
          <Link
            to="/"
            className="text-sm text-slate-400 no-underline transition hover:text-white"
          >
            Cancel
          </Link>
        </div>
      </nav>

      {/* Main content */}
      <div className="flex flex-1 flex-col items-center justify-center px-4 pb-16">
        <div className="w-full max-w-xl">
          {/* Step indicator */}
          <div className="mb-8">
            <div className="flex items-center justify-between">
              {Array.from({ length: TOTAL_STEPS }, (_, i) => i + 1).map((s) => (
                <div key={s} className="flex items-center">
                  <div
                    className={`flex h-8 w-8 items-center justify-center rounded-full text-xs font-semibold transition-colors ${
                      s < step
                        ? 'bg-blue-600 text-white'
                        : s === step
                          ? 'bg-blue-600 text-white ring-4 ring-blue-600/20'
                          : 'bg-slate-800 text-slate-500'
                    }`}
                  >
                    {s < step ? (
                      <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
                        <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                      </svg>
                    ) : (
                      s
                    )}
                  </div>
                  {s < TOTAL_STEPS && (
                    <div className={`mx-2 h-px w-8 sm:w-12 ${s < step ? 'bg-blue-600' : 'bg-slate-800'}`} />
                  )}
                </div>
              ))}
            </div>
            <p className="mt-4 text-center text-sm text-slate-500">
              Step {step} of {TOTAL_STEPS}
            </p>
          </div>

          {/* Form card */}
          <div className="rounded-2xl border border-slate-800 bg-slate-900/60 p-8 backdrop-blur-sm">
            {/* Step 1: Name & Currency */}
            {step === 1 && (
              <div className="animate-fade-in">
                <h2 className="text-2xl font-bold text-white">Name your bank</h2>
                <p className="mt-2 text-sm text-slate-400">
                  Choose a name and base currency for your virtual bank.
                </p>

                <div className="mt-8 space-y-6">
                  <div>
                    <label className="mb-2 block text-sm font-medium text-slate-300">
                      Bank Name
                    </label>
                    <input
                      type="text"
                      value={form.bankName}
                      onChange={(e) => updateForm({ bankName: e.target.value })}
                      placeholder="e.g. Dragon Treasury, Pixel Bank"
                      className="w-full rounded-xl border border-slate-700 bg-slate-800 px-4 py-3 text-white placeholder-slate-500 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                      autoFocus
                    />
                  </div>

                  <div>
                    <label className="mb-2 block text-sm font-medium text-slate-300">
                      Currency
                    </label>
                    <select
                      value={form.currency}
                      onChange={(e) => updateForm({ currency: e.target.value })}
                      className="w-full rounded-xl border border-slate-700 bg-slate-800 px-4 py-3 text-white outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                    >
                      <option value="NOK">NOK - Norwegian Krone</option>
                      <option value="USD">USD - US Dollar</option>
                      <option value="EUR">EUR - Euro</option>
                      <option value="GBP">GBP - British Pound</option>
                      <option value="SEK">SEK - Swedish Krona</option>
                      <option value="DKK">DKK - Danish Krone</option>
                      <option value="GOLD">GOLD - Gold Coins (virtual)</option>
                      <option value="GEM">GEM - Gems (virtual)</option>
                    </select>
                  </div>
                </div>
              </div>
            )}

            {/* Step 2: Branding */}
            {step === 2 && (
              <div className="animate-fade-in">
                <h2 className="text-2xl font-bold text-white">Customize branding</h2>
                <p className="mt-2 text-sm text-slate-400">
                  Pick a theme color and tagline for your bank.
                </p>

                <div className="mt-8 space-y-6">
                  <div>
                    <label className="mb-3 block text-sm font-medium text-slate-300">
                      Primary Color
                    </label>
                    <div className="flex flex-wrap gap-3">
                      {PRESET_COLORS.map((color) => (
                        <button
                          key={color}
                          onClick={() => updateForm({ primaryColor: color })}
                          className={`h-10 w-10 rounded-xl transition-all hover:scale-110 ${
                            form.primaryColor === color
                              ? 'ring-2 ring-white ring-offset-2 ring-offset-slate-900'
                              : ''
                          }`}
                          style={{ backgroundColor: color }}
                        />
                      ))}
                      <div className="relative">
                        <input
                          type="color"
                          value={form.primaryColor}
                          onChange={(e) => updateForm({ primaryColor: e.target.value })}
                          className="absolute inset-0 h-10 w-10 cursor-pointer opacity-0"
                        />
                        <div className="flex h-10 w-10 items-center justify-center rounded-xl border border-dashed border-slate-600 text-slate-400 transition hover:border-slate-500 hover:text-slate-300">
                          <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                            <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
                          </svg>
                        </div>
                      </div>
                    </div>
                  </div>

                  <div>
                    <label className="mb-2 block text-sm font-medium text-slate-300">
                      Tagline (optional)
                    </label>
                    <input
                      type="text"
                      value={form.tagline}
                      onChange={(e) => updateForm({ tagline: e.target.value })}
                      placeholder="e.g. Banking for the brave"
                      className="w-full rounded-xl border border-slate-700 bg-slate-800 px-4 py-3 text-white placeholder-slate-500 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                    />
                  </div>

                  {/* Preview */}
                  <div>
                    <label className="mb-2 block text-sm font-medium text-slate-300">Preview</label>
                    <div className="overflow-hidden rounded-xl border border-slate-700">
                      <div className="px-6 py-4 text-center" style={{ backgroundColor: form.primaryColor }}>
                        <div className="mx-auto mb-1 flex h-10 w-10 items-center justify-center rounded-lg bg-white/20 text-lg font-bold text-white">
                          {form.bankName ? form.bankName.charAt(0).toUpperCase() : 'B'}
                        </div>
                        <p className="font-semibold text-white">
                          {form.bankName || 'Your Bank'}
                        </p>
                        {form.tagline && (
                          <p className="mt-0.5 text-xs text-white/70">{form.tagline}</p>
                        )}
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            )}

            {/* Step 3: Access Policy */}
            {step === 3 && (
              <div className="animate-fade-in">
                <h2 className="text-2xl font-bold text-white">Access policy</h2>
                <p className="mt-2 text-sm text-slate-400">
                  How should new members join your bank?
                </p>

                <div className="mt-8 space-y-3">
                  {ACCESS_POLICIES.map((policy) => (
                    <button
                      key={policy.value}
                      onClick={() => updateForm({ accessPolicy: policy.value })}
                      className={`w-full rounded-xl border p-4 text-left transition ${
                        form.accessPolicy === policy.value
                          ? 'border-blue-500 bg-blue-500/10'
                          : 'border-slate-700 bg-slate-800/50 hover:border-slate-600'
                      }`}
                    >
                      <div className="flex items-center gap-3">
                        <div
                          className={`flex h-5 w-5 items-center justify-center rounded-full border-2 ${
                            form.accessPolicy === policy.value
                              ? 'border-blue-500'
                              : 'border-slate-600'
                          }`}
                        >
                          {form.accessPolicy === policy.value && (
                            <div className="h-2.5 w-2.5 rounded-full bg-blue-500" />
                          )}
                        </div>
                        <span className="font-medium text-white">{policy.label}</span>
                      </div>
                      <p className="mt-1.5 pl-8 text-sm text-slate-400">{policy.desc}</p>
                    </button>
                  ))}
                </div>
              </div>
            )}

            {/* Step 4: Transfer Policy */}
            {step === 4 && (
              <div className="animate-fade-in">
                <h2 className="text-2xl font-bold text-white">Transfer policy</h2>
                <p className="mt-2 text-sm text-slate-400">
                  What transfer rules apply to your bank?
                </p>

                <div className="mt-8 space-y-3">
                  {TRANSFER_POLICIES.map((policy) => (
                    <button
                      key={policy.value}
                      onClick={() => updateForm({ transferPolicy: policy.value })}
                      className={`w-full rounded-xl border p-4 text-left transition ${
                        form.transferPolicy === policy.value
                          ? 'border-blue-500 bg-blue-500/10'
                          : 'border-slate-700 bg-slate-800/50 hover:border-slate-600'
                      }`}
                    >
                      <div className="flex items-center gap-3">
                        <div
                          className={`flex h-5 w-5 items-center justify-center rounded-full border-2 ${
                            form.transferPolicy === policy.value
                              ? 'border-blue-500'
                              : 'border-slate-600'
                          }`}
                        >
                          {form.transferPolicy === policy.value && (
                            <div className="h-2.5 w-2.5 rounded-full bg-blue-500" />
                          )}
                        </div>
                        <span className="font-medium text-white">{policy.label}</span>
                      </div>
                      <p className="mt-1.5 pl-8 text-sm text-slate-400">{policy.desc}</p>
                    </button>
                  ))}
                </div>
              </div>
            )}

            {/* Step 5: Review */}
            {step === 5 && (
              <div className="animate-fade-in">
                <h2 className="text-2xl font-bold text-white">Review & create</h2>
                <p className="mt-2 text-sm text-slate-400">
                  Confirm your bank details before creating.
                </p>

                <div className="mt-8 space-y-4">
                  <ReviewRow label="Bank Name" value={form.bankName} />
                  <ReviewRow label="Currency" value={form.currency} />
                  <ReviewRow
                    label="Primary Color"
                    value={
                      <div className="flex items-center gap-2">
                        <div className="h-5 w-5 rounded" style={{ backgroundColor: form.primaryColor }} />
                        <span>{form.primaryColor}</span>
                      </div>
                    }
                  />
                  {form.tagline && <ReviewRow label="Tagline" value={form.tagline} />}
                  <ReviewRow
                    label="Access Policy"
                    value={ACCESS_POLICIES.find((p) => p.value === form.accessPolicy)?.label || form.accessPolicy}
                  />
                  <ReviewRow
                    label="Transfer Policy"
                    value={TRANSFER_POLICIES.find((p) => p.value === form.transferPolicy)?.label || form.transferPolicy}
                  />
                </div>

                {error && (
                  <div className="mt-6 rounded-xl border border-red-900/50 bg-red-950/50 px-4 py-3 text-sm text-red-300">
                    {error}
                  </div>
                )}
              </div>
            )}

            {/* Navigation buttons */}
            <div className="mt-8 flex items-center justify-between">
              <button
                onClick={back}
                disabled={step === 1}
                className="flex items-center gap-1 rounded-xl px-4 py-2.5 text-sm font-medium text-slate-400 transition hover:text-white disabled:invisible"
              >
                <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
                </svg>
                Back
              </button>

              {step < TOTAL_STEPS ? (
                <button
                  onClick={next}
                  disabled={!canAdvance()}
                  className="flex items-center gap-1 rounded-xl bg-blue-600 px-6 py-2.5 text-sm font-semibold text-white transition hover:bg-blue-700 disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  Next
                  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
                  </svg>
                </button>
              ) : (
                <button
                  onClick={handleSubmit}
                  disabled={submitting}
                  className="flex items-center gap-2 rounded-xl bg-gradient-to-r from-blue-600 to-violet-600 px-6 py-2.5 text-sm font-semibold text-white shadow-lg shadow-blue-600/25 transition hover:-translate-y-0.5 disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {submitting ? (
                    <>
                      <svg className="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none">
                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                      </svg>
                      Creating...
                    </>
                  ) : (
                    <>
                      Create Bank
                      <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                        <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                      </svg>
                    </>
                  )}
                </button>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

function ReviewRow({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between rounded-lg border border-slate-800 bg-slate-800/30 px-4 py-3">
      <span className="text-sm text-slate-400">{label}</span>
      <span className="text-sm font-medium text-white">{value}</span>
    </div>
  )
}
