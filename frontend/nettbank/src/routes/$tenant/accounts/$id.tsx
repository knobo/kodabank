import { createFileRoute, Link } from '@tanstack/react-router'
import { useState, useEffect, useMemo } from 'react'
import { fetchAccount, fetchTransactions } from '../../../lib/api'
import type { Account, Transaction } from '../../../lib/api'
import { formatCurrency, formatDateShort, formatIban } from '../../../lib/format'

export const Route = createFileRoute('/$tenant/accounts/$id')({
  component: AccountDetailPage,
})

function AccountDetailPage() {
  const { tenant, id } = Route.useParams()
  const [account, setAccount] = useState<Account | null>(null)
  const [transactions, setTransactions] = useState<Transaction[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [searchQuery, setSearchQuery] = useState('')
  const [filterType, setFilterType] = useState<'all' | 'income' | 'expense'>('all')

  useEffect(() => {
    Promise.all([fetchAccount(tenant, id), fetchTransactions(tenant, id)])
      .then(([acc, txs]) => {
        setAccount(acc)
        setTransactions(txs)
        setLoading(false)
      })
      .catch(() => {
        setError('Kunne ikke koble til server')
        setLoading(false)
      })
  }, [tenant, id])

  const filteredTransactions = useMemo(() => {
    let result = transactions
    if (searchQuery) {
      const q = searchQuery.toLowerCase()
      result = result.filter(
        (tx) =>
          tx.description.toLowerCase().includes(q) ||
          (tx.counterparty && tx.counterparty.toLowerCase().includes(q)),
      )
    }
    if (filterType === 'income') {
      result = result.filter((tx) => tx.amount >= 0)
    } else if (filterType === 'expense') {
      result = result.filter((tx) => tx.amount < 0)
    }
    return result
  }, [transactions, searchQuery, filterType])

  if (loading) {
    return (
      <div className="space-y-4">
        <div className="h-8 w-48 rounded bg-slate-200 animate-pulse" />
        <div className="h-32 rounded-xl bg-slate-200 animate-pulse" />
        <div className="h-64 rounded-xl bg-slate-200 animate-pulse" />
      </div>
    )
  }

  if (error || !account) {
    return (
      <div className="rounded-xl border border-red-200 bg-red-50 px-6 py-4 text-red-700">
        {error || 'Konto ikke funnet'}
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {/* Breadcrumb */}
      <div className="flex items-center gap-2 text-sm text-slate-500">
        <Link
          to="/$tenant/accounts"
          params={{ tenant }}
          className="text-slate-500 no-underline hover:text-slate-700"
        >
          Kontoer
        </Link>
        <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
        </svg>
        <span className="text-slate-800 font-medium">{account.name}</span>
      </div>

      {/* Account info card */}
      <div className="animate-fade-in rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <h1 className="text-2xl font-bold text-slate-900">{account.name}</h1>
            <p className="mt-1 font-mono text-sm text-slate-400">
              {formatIban(account.iban)}
            </p>
            <span className="mt-2 inline-block rounded-lg bg-slate-100 px-3 py-1 text-xs font-medium text-slate-600">
              {accountTypeLabel(account.type)}
            </span>
          </div>
          <div className="text-right">
            <p className="text-sm text-slate-500">Saldo</p>
            <p className="text-3xl font-bold text-slate-900">
              {formatCurrency(account.balance, account.currency)}
            </p>
            {account.availableBalance !== undefined && account.availableBalance !== account.balance && (
              <p className="mt-1 text-sm text-slate-500">
                Disponibelt: {formatCurrency(account.availableBalance, account.currency)}
              </p>
            )}
          </div>
        </div>
      </div>

      {/* Transactions section */}
      <div>
        <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <h2 className="text-lg font-semibold text-slate-800">
            Transaksjoner
            <span className="ml-2 text-sm font-normal text-slate-400">
              ({filteredTransactions.length})
            </span>
          </h2>

          <div className="flex gap-2">
            {/* Search */}
            <div className="relative flex-1 sm:flex-initial">
              <svg
                className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
                strokeWidth={2}
              >
                <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
              <input
                type="text"
                placeholder="Sok..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full rounded-lg border border-slate-200 bg-white py-2 pl-9 pr-3 text-sm outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100 sm:w-56"
              />
            </div>

            {/* Filter buttons */}
            <div className="flex rounded-lg border border-slate-200 bg-white">
              {(['all', 'income', 'expense'] as const).map((type) => (
                <button
                  key={type}
                  onClick={() => setFilterType(type)}
                  className={`px-3 py-2 text-xs font-medium transition ${
                    filterType === type
                      ? 'bg-slate-900 text-white'
                      : 'text-slate-600 hover:bg-slate-50'
                  } ${type === 'all' ? 'rounded-l-lg' : ''} ${type === 'expense' ? 'rounded-r-lg' : ''}`}
                >
                  {type === 'all' ? 'Alle' : type === 'income' ? 'Inn' : 'Ut'}
                </button>
              ))}
            </div>
          </div>
        </div>

        <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
          {filteredTransactions.length === 0 ? (
            <div className="px-6 py-12 text-center text-sm text-slate-400">
              {searchQuery || filterType !== 'all'
                ? 'Ingen transaksjoner funnet med valgt filter'
                : 'Ingen transaksjoner enna'}
            </div>
          ) : (
            <>
              {/* Table header */}
              <div className="hidden border-b border-slate-100 bg-slate-50 px-5 py-2.5 text-xs font-semibold uppercase tracking-wider text-slate-500 sm:grid sm:grid-cols-12 sm:gap-4">
                <span className="col-span-2">Dato</span>
                <span className="col-span-5">Beskrivelse</span>
                <span className="col-span-2 text-right">Belop</span>
                <span className="col-span-3 text-right">Saldo etter</span>
              </div>

              <div className="divide-y divide-slate-100">
                {filteredTransactions.map((tx) => (
                  <div
                    key={tx.id}
                    className="grid grid-cols-12 items-center gap-2 px-5 py-3.5 transition hover:bg-slate-50 sm:gap-4"
                  >
                    <span className="col-span-4 text-sm text-slate-500 sm:col-span-2">
                      {formatDateShort(tx.date)}
                    </span>
                    <div className="col-span-8 sm:col-span-5">
                      <p className="text-sm font-medium text-slate-800 truncate">
                        {tx.description}
                      </p>
                      {tx.category && (
                        <span className="text-xs text-slate-400">{tx.category}</span>
                      )}
                    </div>
                    <span
                      className={`col-span-6 text-right text-sm font-semibold sm:col-span-2 ${
                        tx.amount >= 0 ? 'text-green-600' : 'text-slate-800'
                      }`}
                    >
                      {tx.amount >= 0 ? '+' : ''}
                      {formatCurrency(tx.amount, tx.currency)}
                    </span>
                    <span className="col-span-6 text-right text-sm text-slate-400 sm:col-span-3">
                      {tx.balanceAfter !== undefined
                        ? formatCurrency(tx.balanceAfter, tx.currency)
                        : '-'}
                    </span>
                  </div>
                ))}
              </div>
            </>
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
