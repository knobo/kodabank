import { createFileRoute } from '@tanstack/react-router'
import { useState, useEffect } from 'react'
import { fetchAccounts, initiateTransfer } from '../../lib/api'
import type { Account, Payment } from '../../lib/api'
import { formatCurrency, formatIban } from '../../lib/format'

export const Route = createFileRoute('/$tenant/transfers')({
  component: TransfersPage,
})

function TransfersPage() {
  const { tenant } = Route.useParams()

  const [accounts, setAccounts] = useState<Account[]>([])
  const [loadingAccounts, setLoadingAccounts] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<Payment | null>(null)

  const [fromAccountId, setFromAccountId] = useState('')
  const [toAccountId, setToAccountId] = useState('')
  const [amount, setAmount] = useState('')
  const [reference, setReference] = useState('')

  useEffect(() => {
    fetchAccounts(tenant)
      .then((data) => {
        setAccounts(data)
        if (data.length > 0) {
          setFromAccountId(data[0].id)
          if (data.length > 1) {
            setToAccountId(data[1].id)
          }
        }
        setLoadingAccounts(false)
      })
      .catch(() => {
        setLoadingAccounts(false)
      })
  }, [tenant])

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)

    if (fromAccountId === toAccountId) {
      setError('Velg ulike kontoer for overforingen')
      return
    }

    const parsedAmount = parseFloat(amount.replace(',', '.'))
    if (isNaN(parsedAmount) || parsedAmount <= 0) {
      setError('Oppgi et gyldig belop')
      return
    }

    setSubmitting(true)
    try {
      const result = await initiateTransfer(tenant, {
        fromAccountId,
        toAccountId,
        amount: parsedAmount,
        reference: reference.trim() || undefined,
      })
      setSuccess(result)
    } catch {
      setError('Overforingen feilet. Prov igjen.')
      setSubmitting(false)
    }
  }

  function resetForm() {
    setSuccess(null)
    setAmount('')
    setReference('')
    setError(null)
  }

  const fromAccount = accounts.find((a) => a.id === fromAccountId)
  const toAccount = accounts.find((a) => a.id === toAccountId)

  if (success) {
    return (
      <div className="mx-auto max-w-lg py-8">
        <div className="animate-fade-in rounded-2xl border border-slate-200 bg-white p-8 text-center shadow-sm">
          <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-green-50">
            <svg className="h-8 w-8 text-green-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
            </svg>
          </div>
          <h2 className="text-xl font-bold text-slate-900">Overforing utfort!</h2>
          <p className="mt-2 text-sm text-slate-500">
            {formatCurrency(success.amount, success.currency)} overfort
          </p>
          <button
            onClick={resetForm}
            className="mt-6 rounded-lg bg-slate-900 px-5 py-2.5 text-sm font-semibold text-white hover:bg-slate-800"
          >
            Ny overforing
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-lg space-y-6">
      <div className="animate-fade-in">
        <h1 className="text-2xl font-bold text-slate-900">Overforinger</h1>
        <p className="mt-1 text-slate-500">Overforing mellom egne kontoer</p>
      </div>

      <form
        onSubmit={handleSubmit}
        className="animate-fade-in space-y-5 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm"
        style={{ animationDelay: '100ms' }}
      >
        {error && (
          <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            {error}
          </div>
        )}

        {loadingAccounts ? (
          <div className="space-y-4">
            <div className="h-16 rounded-lg bg-slate-100 animate-pulse" />
            <div className="h-16 rounded-lg bg-slate-100 animate-pulse" />
          </div>
        ) : (
          <>
            {/* From account */}
            <div>
              <label className="mb-1.5 block text-sm font-medium text-slate-700">
                Fra konto
              </label>
              <select
                value={fromAccountId}
                onChange={(e) => setFromAccountId(e.target.value)}
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100"
              >
                {accounts.map((acc) => (
                  <option key={acc.id} value={acc.id}>
                    {acc.name} ({formatCurrency(acc.balance, acc.currency)})
                  </option>
                ))}
              </select>
              {fromAccount && (
                <p className="mt-1 font-mono text-xs text-slate-400">
                  {formatIban(fromAccount.iban)}
                </p>
              )}
            </div>

            {/* Swap button */}
            <div className="flex justify-center">
              <button
                type="button"
                onClick={() => {
                  setFromAccountId(toAccountId)
                  setToAccountId(fromAccountId)
                }}
                className="flex h-10 w-10 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-500 transition hover:bg-slate-50 hover:text-slate-700"
              >
                <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M7 16V4m0 0L3 8m4-4l4 4m6 0v12m0 0l4-4m-4 4l-4-4" />
                </svg>
              </button>
            </div>

            {/* To account */}
            <div>
              <label className="mb-1.5 block text-sm font-medium text-slate-700">
                Til konto
              </label>
              <select
                value={toAccountId}
                onChange={(e) => setToAccountId(e.target.value)}
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100"
              >
                {accounts.map((acc) => (
                  <option key={acc.id} value={acc.id}>
                    {acc.name} ({formatCurrency(acc.balance, acc.currency)})
                  </option>
                ))}
              </select>
              {toAccount && (
                <p className="mt-1 font-mono text-xs text-slate-400">
                  {formatIban(toAccount.iban)}
                </p>
              )}
            </div>
          </>
        )}

        {/* Amount */}
        <div>
          <label className="mb-1.5 block text-sm font-medium text-slate-700">
            Belop (NOK)
          </label>
          <input
            type="text"
            inputMode="decimal"
            placeholder="0,00"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100"
          />
        </div>

        {/* Reference */}
        <div>
          <label className="mb-1.5 block text-sm font-medium text-slate-700">
            Melding (valgfritt)
          </label>
          <input
            type="text"
            placeholder="F.eks. Husleie mars"
            value={reference}
            onChange={(e) => setReference(e.target.value)}
            className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100"
          />
        </div>

        {/* Submit */}
        <button
          type="submit"
          disabled={submitting || loadingAccounts || accounts.length < 2}
          className="w-full rounded-lg bg-slate-900 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-slate-800 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {submitting ? (
            <span className="flex items-center justify-center gap-2">
              <svg className="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
              </svg>
              Overforer...
            </span>
          ) : (
            'Overfore'
          )}
        </button>

        {accounts.length < 2 && !loadingAccounts && (
          <p className="text-center text-xs text-slate-400">
            Du trenger minst to kontoer for a gjore en overforing
          </p>
        )}
      </form>
    </div>
  )
}
