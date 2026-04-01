import { createFileRoute, Link } from '@tanstack/react-router'
import { useState, useEffect } from 'react'
import {
  fetchBranding,
  listMemberships,
  approveMembership,
  rejectMembership,
  fetchBankAdminSettings,
  updateBankAdminSettings,
} from '../../lib/api'
import type { Branding, Membership, BankAdminSettings } from '../../lib/api'

export const Route = createFileRoute('/$tenant/admin')({
  component: BankAdminPage,
})

function BankAdminPage() {
  const { tenant } = Route.useParams()

  const [branding, setBranding] = useState<Branding | null>(null)
  const [memberships, setMemberships] = useState<Membership[]>([])
  const [settings, setSettings] = useState<BankAdminSettings | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionLoading, setActionLoading] = useState<string | null>(null)
  const [activeTab, setActiveTab] = useState<'pending' | 'approved' | 'rejected'>('pending')
  const [urlAliasInput, setUrlAliasInput] = useState('')
  const [aliasSaving, setAliasSaving] = useState(false)
  const [aliasSaved, setAliasSaved] = useState(false)
  const [copiedLink, setCopiedLink] = useState(false)

  useEffect(() => {
    Promise.all([
      fetchBranding(tenant).catch(() => ({
        bankName: tenant.charAt(0).toUpperCase() + tenant.slice(1),
        primaryColor: '#0074cd',
      })),
      listMemberships(tenant).catch(() => []),
      fetchBankAdminSettings(tenant).catch(() => null),
    ])
      .then(([b, m, s]) => {
        setBranding(b)
        setMemberships(m)
        if (s) {
          setSettings(s)
          setUrlAliasInput(s.urlAlias || '')
        }
        setLoading(false)
      })
      .catch((err) => {
        setError(err instanceof Error ? err.message : 'Failed to load admin data')
        setLoading(false)
      })
  }, [tenant])

  async function saveUrlAlias() {
    setAliasSaving(true)
    try {
      const result = await updateBankAdminSettings(tenant, { urlAlias: urlAliasInput })
      setSettings((prev) => prev ? { ...prev, urlAlias: result.urlAlias } : prev)
      setAliasSaved(true)
      setTimeout(() => setAliasSaved(false), 3000)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to save alias')
    } finally {
      setAliasSaving(false)
    }
  }

  function copyShareLink() {
    const alias = settings?.urlAlias
    const url = alias
      ? `${window.location.protocol}//${window.location.host.replace(/:\d+/, ':8085')}/go/${alias}`
      : `${window.location.origin}/${tenant}/login`
    navigator.clipboard.writeText(url)
    setCopiedLink(true)
    setTimeout(() => setCopiedLink(false), 2000)
  }

  const primaryColor = branding?.primaryColor || '#0074cd'

  async function handleApprove(userId: string) {
    setActionLoading(userId)
    try {
      const updated = await approveMembership(tenant, userId)
      setMemberships((prev) =>
        prev.map((m) => (m.userId === userId ? updated : m)),
      )
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to approve membership')
    } finally {
      setActionLoading(null)
    }
  }

  async function handleReject(userId: string) {
    setActionLoading(userId)
    try {
      const updated = await rejectMembership(tenant, userId)
      setMemberships((prev) =>
        prev.map((m) => (m.userId === userId ? updated : m)),
      )
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to reject membership')
    } finally {
      setActionLoading(null)
    }
  }

  const filteredMemberships = memberships.filter((m) => m.status === activeTab.toUpperCase())

  const pendingCount = memberships.filter((m) => m.status === 'PENDING').length
  const approvedCount = memberships.filter((m) => m.status === 'APPROVED').length
  const rejectedCount = memberships.filter((m) => m.status === 'REJECTED').length

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-950">
        <div className="flex items-center gap-3 text-slate-400">
          <svg className="h-5 w-5 animate-spin" viewBox="0 0 24 24" fill="none">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
          </svg>
          Loading admin panel...
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-slate-950">
      {/* Header */}
      <header className="border-b border-slate-800" style={{ backgroundColor: primaryColor }}>
        <div className="mx-auto flex max-w-5xl items-center justify-between px-6 py-4">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-white/20 text-lg font-bold text-white">
              {(branding?.bankName || tenant).charAt(0).toUpperCase()}
            </div>
            <div>
              <h1 className="text-lg font-bold text-white">
                {branding?.bankName || tenant}
              </h1>
              <p className="text-xs text-white/70">Bank Administration</p>
            </div>
          </div>
          <div className="flex items-center gap-3">
            <Link
              to="/$tenant/dashboard"
              params={{ tenant }}
              className="rounded-lg bg-white/15 px-3 py-2 text-sm font-medium text-white no-underline transition hover:bg-white/25"
            >
              Dashboard
            </Link>
            <Link
              to="/"
              className="rounded-lg bg-white/15 px-3 py-2 text-sm font-medium text-white no-underline transition hover:bg-white/25"
            >
              Home
            </Link>
          </div>
        </div>
      </header>

      <div className="mx-auto max-w-5xl px-6 py-8">
        {error && (
          <div className="mb-6 rounded-xl border border-red-900/50 bg-red-950/50 px-4 py-3 text-sm text-red-300">
            {error}
            <button
              onClick={() => setError(null)}
              className="ml-2 text-red-400 hover:text-red-300"
            >
              Dismiss
            </button>
          </div>
        )}

        {/* Stats row */}
        <div className="mb-8 grid gap-4 sm:grid-cols-3">
          <StatCard label="Pending Requests" value={pendingCount} color="text-amber-400" />
          <StatCard label="Approved Members" value={approvedCount} color="text-emerald-400" />
          <StatCard label="Rejected" value={rejectedCount} color="text-red-400" />
        </div>

        {/* Membership management */}
        <div className="rounded-2xl border border-slate-800 bg-slate-900/60 backdrop-blur-sm">
          <div className="border-b border-slate-800 px-6 py-4">
            <h2 className="text-lg font-semibold text-white">Membership Requests</h2>
          </div>

          {/* Tabs */}
          <div className="flex border-b border-slate-800">
            {(['pending', 'approved', 'rejected'] as const).map((tab) => {
              const count = tab === 'pending' ? pendingCount : tab === 'approved' ? approvedCount : rejectedCount
              return (
                <button
                  key={tab}
                  onClick={() => setActiveTab(tab)}
                  className={`flex-1 px-4 py-3 text-sm font-medium capitalize transition ${
                    activeTab === tab
                      ? 'border-b-2 border-blue-500 text-blue-400'
                      : 'text-slate-400 hover:text-slate-300'
                  }`}
                >
                  {tab}
                  {count > 0 && (
                    <span className={`ml-2 rounded-full px-2 py-0.5 text-xs ${
                      activeTab === tab ? 'bg-blue-500/20 text-blue-400' : 'bg-slate-800 text-slate-500'
                    }`}>
                      {count}
                    </span>
                  )}
                </button>
              )
            })}
          </div>

          {/* Members list */}
          <div className="divide-y divide-slate-800">
            {filteredMemberships.length === 0 ? (
              <div className="px-6 py-12 text-center text-sm text-slate-500">
                No {activeTab} membership requests.
              </div>
            ) : (
              filteredMemberships.map((m) => (
                <div
                  key={m.userId}
                  className="flex items-center justify-between px-6 py-4 transition hover:bg-slate-800/30"
                >
                  <div className="flex items-center gap-3">
                    <div className="flex h-10 w-10 items-center justify-center rounded-full bg-slate-800 text-sm font-semibold text-slate-300">
                      {m.displayName.charAt(0).toUpperCase()}
                    </div>
                    <div>
                      <p className="text-sm font-medium text-white">{m.displayName}</p>
                      <p className="text-xs text-slate-400">{m.email}</p>
                      {m.message && (
                        <p className="mt-1 text-xs text-slate-500 italic">"{m.message}"</p>
                      )}
                    </div>
                  </div>

                  <div className="flex items-center gap-2">
                    <span className="text-xs text-slate-500">
                      {formatDate(m.createdAt)}
                    </span>

                    {m.status === 'PENDING' && (
                      <>
                        <button
                          onClick={() => handleApprove(m.userId)}
                          disabled={actionLoading === m.userId}
                          className="rounded-lg bg-emerald-600/20 px-3 py-1.5 text-xs font-medium text-emerald-400 transition hover:bg-emerald-600/30 disabled:opacity-50"
                        >
                          {actionLoading === m.userId ? '...' : 'Approve'}
                        </button>
                        <button
                          onClick={() => handleReject(m.userId)}
                          disabled={actionLoading === m.userId}
                          className="rounded-lg bg-red-600/20 px-3 py-1.5 text-xs font-medium text-red-400 transition hover:bg-red-600/30 disabled:opacity-50"
                        >
                          Reject
                        </button>
                      </>
                    )}

                    {m.status === 'APPROVED' && (
                      <span className="rounded-full bg-emerald-500/10 px-2.5 py-1 text-xs font-medium text-emerald-400">
                        Active
                      </span>
                    )}

                    {m.status === 'REJECTED' && (
                      <span className="rounded-full bg-red-500/10 px-2.5 py-1 text-xs font-medium text-red-400">
                        Rejected
                      </span>
                    )}
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        {/* Bank Settings */}
        <div className="mt-8 rounded-2xl border border-slate-800 bg-slate-900/60 backdrop-blur-sm">
          <div className="border-b border-slate-800 px-6 py-4">
            <h2 className="text-lg font-semibold text-white">Bank Settings</h2>
          </div>
          <div className="grid gap-6 p-6 sm:grid-cols-2">
            <SettingCard
              title="Access Policy"
              description="Controls how new members join your bank."
              current={settings?.accessPolicyType || 'AUTO_APPROVE'}
            />
            <SettingCard
              title="Transfer Policy"
              description="Rules for how transfers work in your bank."
              current={settings?.transferPolicyType || 'OPEN'}
            />
            <SettingCard
              title="Bank Code"
              description="Unique bank identifier used in IBANs."
              current={settings?.bankCode || tenant}
            />
            <SettingCard
              title="Currency"
              description="The primary currency of this bank."
              current={settings?.currency || 'NOK'}
            />
          </div>
        </div>

        {/* Custom URL / Share Link */}
        <div className="mt-8 rounded-2xl border border-slate-800 bg-slate-900/60 backdrop-blur-sm">
          <div className="border-b border-slate-800 px-6 py-4">
            <h2 className="text-lg font-semibold text-white">Custom URL & Sharing</h2>
            <p className="mt-1 text-sm text-slate-400">Set a memorable short link for your bank.</p>
          </div>
          <div className="p-6 space-y-4">
            <div>
              <label className="block text-sm font-medium text-slate-300 mb-2">
                URL Alias
              </label>
              <div className="flex items-center gap-2">
                <span className="text-sm text-slate-500 whitespace-nowrap">localhost:8085/go/</span>
                <input
                  type="text"
                  value={urlAliasInput}
                  onChange={(e) => setUrlAliasInput(e.target.value.toLowerCase().replace(/[^a-z0-9-]/g, '-'))}
                  placeholder="my-bank"
                  className="flex-1 rounded-lg border border-slate-700 bg-slate-800 px-3 py-2 text-sm text-white placeholder-slate-500 focus:border-blue-500 focus:outline-none"
                />
                <button
                  onClick={saveUrlAlias}
                  disabled={aliasSaving}
                  className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-blue-500 disabled:opacity-50"
                >
                  {aliasSaving ? 'Saving...' : aliasSaved ? '✓ Saved' : 'Save'}
                </button>
              </div>
              <p className="mt-2 text-xs text-slate-500">
                Only lowercase letters, numbers, and hyphens. Once set, share this link:
              </p>
            </div>

            <div className="rounded-xl border border-slate-700 bg-slate-800/50 p-4">
              <div className="flex items-center justify-between gap-3">
                <div className="min-w-0">
                  <p className="text-xs text-slate-500 mb-1">Shareable login link</p>
                  <p className="font-mono text-sm text-slate-200 truncate">
                    {settings?.urlAlias
                      ? `http://localhost:8085/go/${settings.urlAlias}`
                      : `http://localhost:3100/${tenant}/login`}
                  </p>
                </div>
                <button
                  onClick={copyShareLink}
                  className="flex-shrink-0 flex items-center gap-1.5 rounded-lg border border-slate-700 px-3 py-2 text-sm text-slate-300 transition hover:border-slate-600 hover:text-white"
                >
                  {copiedLink ? (
                    <>
                      <svg className="h-4 w-4 text-emerald-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                        <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                      </svg>
                      Copied!
                    </>
                  ) : (
                    <>
                      <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                        <path strokeLinecap="round" strokeLinejoin="round" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
                      </svg>
                      Copy link
                    </>
                  )}
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

function StatCard({ label, value, color }: { label: string; value: number; color: string }) {
  return (
    <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-5">
      <p className="text-sm text-slate-400">{label}</p>
      <p className={`mt-1 text-2xl font-bold ${color}`}>{value}</p>
    </div>
  )
}

function SettingCard({
  title,
  description,
  current,
}: {
  title: string
  description: string
  current: string
}) {
  return (
    <div className="rounded-xl border border-slate-800 bg-slate-800/30 p-4">
      <h3 className="text-sm font-semibold text-white">{title}</h3>
      <p className="mt-1 text-xs text-slate-400">{description}</p>
      <p className="mt-3 rounded-lg bg-slate-800/60 px-3 py-2 text-xs font-mono text-slate-300 break-all">
        {current}
      </p>
    </div>
  )
}

function formatDate(dateStr: string): string {
  try {
    return new Date(dateStr).toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    })
  } catch {
    return dateStr
  }
}
