import { createFileRoute, Link } from '@tanstack/react-router'
import { useState, useEffect } from 'react'
import { fetchAccounts } from '../../../lib/api'
import type { Account } from '../../../lib/api'
import { formatCurrency, formatIban } from '../../../lib/format'

export const Route = createFileRoute('/$tenant/accounts/')({
  component: AccountsListPage,
})

function AccountsListPage() {
  const { tenant } = Route.useParams()
  const [accounts, setAccounts] = useState<Account[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchAccounts(tenant)
      .then((data) => {
        setAccounts(data)
        setLoading(false)
      })
      .catch(() => {
        setError('Kunne ikke koble til server')
        setLoading(false)
      })
  }, [tenant])

  if (loading) {
    return (
      <div className="space-y-4">
        <div className="h-8 w-32 rounded bg-slate-200 animate-pulse" />
        {[1, 2, 3].map((n) => (
          <div key={n} className="h-24 rounded-xl bg-slate-200 animate-pulse" />
        ))}
      </div>
    )
  }

  if (error) {
    return (
      <div className="rounded-xl border border-red-200 bg-red-50 px-6 py-4 text-red-700">
        {error}
      </div>
    )
  }

  const totalBalance = accounts.reduce((sum, a) => sum + a.balance, 0)

  return (
    <div className="space-y-6">
      <div className="animate-fade-in">
        <h1 className="text-2xl font-bold text-slate-900">Kontoer</h1>
        <p className="mt-1 text-slate-500">
          Total saldo: {formatCurrency(totalBalance)}
        </p>
      </div>

      <div className="space-y-3">
        {accounts.map((account, i) => (
          <Link
            key={account.id}
            to="/$tenant/accounts/$id"
            params={{ tenant, id: account.id }}
            className="animate-fade-in flex items-center justify-between rounded-xl border border-slate-200 bg-white p-5 shadow-sm transition-all hover:-translate-y-0.5 hover:shadow-md no-underline"
            style={{ animationDelay: `${(i + 1) * 80}ms` }}
          >
            <div className="flex items-center gap-4">
              <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-slate-100 text-slate-600">
                <AccountIcon type={account.type} />
              </div>
              <div>
                <p className="text-base font-semibold text-slate-800">
                  {account.name}
                </p>
                <p className="mt-0.5 font-mono text-xs text-slate-400">
                  {formatIban(account.iban)}
                </p>
              </div>
            </div>
            <div className="text-right">
              <p className="text-lg font-bold text-slate-900">
                {formatCurrency(account.balance, account.currency)}
              </p>
              <span className="rounded-md bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-500">
                {accountTypeLabel(account.type)}
              </span>
            </div>
          </Link>
        ))}
      </div>

      {accounts.length === 0 && (
        <div className="rounded-xl border border-slate-200 bg-white px-6 py-12 text-center">
          <p className="text-slate-500">Ingen kontoer funnet</p>
        </div>
      )}
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

function AccountIcon({ type }: { type: string }) {
  if (type === 'SAVINGS') {
    return (
      <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
    )
  }
  return (
    <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z" />
    </svg>
  )
}
