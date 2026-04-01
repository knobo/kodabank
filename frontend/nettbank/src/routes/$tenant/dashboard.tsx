import { createFileRoute, Link } from '@tanstack/react-router'
import { useState, useEffect } from 'react'
import { fetchDashboard } from '../../lib/api'
import type { DashboardData } from '../../lib/api'
import { formatCurrency, formatDateShort, formatIban } from '../../lib/format'

export const Route = createFileRoute('/$tenant/dashboard')({
  component: DashboardPage,
})

function DashboardPage() {
  const { tenant } = Route.useParams()
  const [data, setData] = useState<DashboardData | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchDashboard(tenant)
      .then((d) => {
        setData(d)
        setLoading(false)
      })
      .catch(() => {
        setError('Kunne ikke koble til server')
        setLoading(false)
      })
  }, [tenant])

  if (loading) return <LoadingSkeleton />
  if (error) return <ErrorMessage message={error} />
  if (!data) return null

  return (
    <div className="space-y-6">
      {/* Welcome + total balance */}
      <div className="animate-fade-in">
        <h1 className="text-2xl font-bold text-slate-900">Oversikt</h1>
        <p className="mt-1 text-slate-500">Velkommen til nettbanken</p>
      </div>

      {/* Total balance card */}
      <div className="animate-fade-in rounded-2xl border border-slate-200 bg-white p-6 shadow-sm"
           style={{ animationDelay: '100ms' }}>
        <p className="text-sm font-medium text-slate-500">Total saldo</p>
        <p className="mt-1 text-3xl font-bold text-slate-900">
          {formatCurrency(data.totalBalance, data.currency)}
        </p>
      </div>

      {/* Account cards */}
      <div>
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-lg font-semibold text-slate-800">Dine kontoer</h2>
          <Link
            to="/$tenant/accounts"
            params={{ tenant }}
            className="text-sm font-medium text-blue-600 no-underline hover:text-blue-700"
          >
            Se alle
          </Link>
        </div>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {data.accounts.map((account, i) => (
            <Link
              key={account.id}
              to="/$tenant/accounts/$id"
              params={{ tenant, id: account.id }}
              className="animate-fade-in group rounded-xl border border-slate-200 bg-white p-5 shadow-sm transition-all hover:-translate-y-0.5 hover:shadow-md no-underline"
              style={{ animationDelay: `${(i + 2) * 100}ms` }}
            >
              <div className="flex items-start justify-between">
                <div>
                  <p className="text-sm font-medium text-slate-500">{account.name}</p>
                  <p className="mt-1 text-xl font-bold text-slate-900">
                    {formatCurrency(account.balance, account.currency)}
                  </p>
                </div>
                <span className="rounded-lg bg-slate-100 px-2 py-1 text-xs font-medium text-slate-600">
                  {accountTypeLabel(account.type)}
                </span>
              </div>
              <p className="mt-3 font-mono text-xs text-slate-400">
                {formatIban(account.iban)}
              </p>
            </Link>
          ))}
        </div>
      </div>

      {/* Quick actions */}
      <div className="grid gap-3 sm:grid-cols-3">
        <Link
          to="/$tenant/payments/new"
          params={{ tenant }}
          className="flex items-center gap-3 rounded-xl border border-slate-200 bg-white p-4 shadow-sm transition-all hover:-translate-y-0.5 hover:shadow-md no-underline"
        >
          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-50 text-blue-600">
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
            </svg>
          </div>
          <div>
            <p className="text-sm font-semibold text-slate-800">Ny betaling</p>
            <p className="text-xs text-slate-500">Betal regninger</p>
          </div>
        </Link>
        <Link
          to="/$tenant/transfers"
          params={{ tenant }}
          className="flex items-center gap-3 rounded-xl border border-slate-200 bg-white p-4 shadow-sm transition-all hover:-translate-y-0.5 hover:shadow-md no-underline"
        >
          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-green-50 text-green-600">
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4" />
            </svg>
          </div>
          <div>
            <p className="text-sm font-semibold text-slate-800">Overforing</p>
            <p className="text-xs text-slate-500">Mellom egne kontoer</p>
          </div>
        </Link>
        <Link
          to="/$tenant/cards"
          params={{ tenant }}
          className="flex items-center gap-3 rounded-xl border border-slate-200 bg-white p-4 shadow-sm transition-all hover:-translate-y-0.5 hover:shadow-md no-underline"
        >
          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-purple-50 text-purple-600">
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z" />
            </svg>
          </div>
          <div>
            <p className="text-sm font-semibold text-slate-800">Kort</p>
            <p className="text-xs text-slate-500">Administrer kort</p>
          </div>
        </Link>
      </div>

      {/* Recent transactions */}
      <div>
        <h2 className="mb-3 text-lg font-semibold text-slate-800">
          Siste transaksjoner
        </h2>
        <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
          {data.recentTransactions.length === 0 ? (
            <div className="px-6 py-8 text-center text-sm text-slate-400">
              Ingen transaksjoner enna
            </div>
          ) : (
            <div className="divide-y divide-slate-100">
              {data.recentTransactions.map((tx) => (
                <div
                  key={tx.id}
                  className="flex items-center justify-between px-5 py-3.5 transition hover:bg-slate-50"
                >
                  <div className="flex items-center gap-3">
                    <div
                      className={`flex h-9 w-9 items-center justify-center rounded-lg ${
                        tx.amount >= 0
                          ? 'bg-green-50 text-green-600'
                          : 'bg-red-50 text-red-500'
                      }`}
                    >
                      {tx.amount >= 0 ? (
                        <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                          <path strokeLinecap="round" strokeLinejoin="round" d="M12 19V5m0 0l-4 4m4-4l4 4" />
                        </svg>
                      ) : (
                        <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                          <path strokeLinecap="round" strokeLinejoin="round" d="M12 5v14m0 0l4-4m-4 4l-4-4" />
                        </svg>
                      )}
                    </div>
                    <div>
                      <p className="text-sm font-medium text-slate-800">
                        {tx.description}
                      </p>
                      <p className="text-xs text-slate-400">
                        {formatDateShort(tx.date)}
                      </p>
                    </div>
                  </div>
                  <span
                    className={`text-sm font-semibold ${
                      tx.amount >= 0 ? 'text-green-600' : 'text-slate-800'
                    }`}
                  >
                    {tx.amount >= 0 ? '+' : ''}
                    {formatCurrency(tx.amount, tx.currency)}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

function accountTypeLabel(type: string): string {
  const map: Record<string, string> = {
    CHECKING: 'Brukskonto',
    SAVINGS: 'Sparekonto',
    CREDIT: 'Kredittkonto',
    LOAN: 'Lan',
  }
  return map[type] || type
}

function LoadingSkeleton() {
  return (
    <div className="space-y-6">
      <div>
        <div className="h-8 w-40 rounded bg-slate-200 animate-pulse" />
        <div className="mt-2 h-5 w-56 rounded bg-slate-200 animate-pulse" />
      </div>
      <div className="h-28 rounded-2xl bg-slate-200 animate-pulse" />
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {[1, 2, 3].map((n) => (
          <div key={n} className="h-32 rounded-xl bg-slate-200 animate-pulse" />
        ))}
      </div>
    </div>
  )
}

function ErrorMessage({ message }: { message: string }) {
  return (
    <div className="flex flex-col items-center justify-center py-16">
      <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-red-50">
        <svg className="h-8 w-8 text-red-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v2m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
      </div>
      <p className="text-lg font-semibold text-slate-800">{message}</p>
      <p className="mt-1 text-sm text-slate-500">
        Prov igjen senere eller kontakt support
      </p>
    </div>
  )
}
