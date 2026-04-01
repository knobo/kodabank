import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useState, useEffect } from 'react'
import { initiatePayment, fetchAccounts } from '../../../lib/api'
import type { Account, Payment } from '../../../lib/api'
import { formatCurrency, formatIban } from '../../../lib/format'

export const Route = createFileRoute('/$tenant/payments/new')({
  component: NewPaymentPage,
})

function NewPaymentPage() {
  const { tenant } = Route.useParams()
  const navigate = useNavigate()

  const [accounts, setAccounts] = useState<Account[]>([])
  const [loadingAccounts, setLoadingAccounts] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<Payment | null>(null)

  const [form, setForm] = useState({
    debitAccountId: '',
    creditorIban: '',
    creditorName: '',
    amount: '',
    reference: '',
    kid: '',
  })

  useEffect(() => {
    fetchAccounts(tenant)
      .then((data) => {
        setAccounts(data)
        if (data.length > 0) {
          setForm((f) => ({ ...f, debitAccountId: data[0].id }))
        }
        setLoadingAccounts(false)
      })
      .catch(() => {
        setLoadingAccounts(false)
      })
  }, [tenant])

  function updateField(field: string, value: string) {
    setForm((f) => ({ ...f, [field]: value }))
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)

    if (!form.creditorName.trim()) {
      setError('Mottakernavn er pakreved')
      return
    }
    if (!form.creditorIban.trim()) {
      setError('IBAN / kontonummer er pakreved')
      return
    }
    const amount = parseFloat(form.amount.replace(',', '.'))
    if (isNaN(amount) || amount <= 0) {
      setError('Oppgi et gyldig belop')
      return
    }
    if (!form.debitAccountId) {
      setError('Velg en konto a betale fra')
      return
    }

    setSubmitting(true)
    try {
      const payment = await initiatePayment(tenant, {
        creditorIban: form.creditorIban.replace(/\s/g, ''),
        creditorName: form.creditorName.trim(),
        amount,
        reference: form.reference.trim() || undefined,
        kid: form.kid.trim() || undefined,
        debitAccountId: form.debitAccountId,
      })
      setSuccess(payment)
    } catch {
      setError('Betalingen feilet. Prov igjen.')
      setSubmitting(false)
    }
  }

  // Success screen
  if (success) {
    return (
      <div className="mx-auto max-w-lg space-y-6 py-8">
        <div className="animate-fade-in rounded-2xl border border-slate-200 bg-white p-8 text-center shadow-sm">
          <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-green-50">
            <svg className="h-8 w-8 text-green-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
            </svg>
          </div>
          <h2 className="text-xl font-bold text-slate-900">Betaling sendt!</h2>
          <p className="mt-2 text-sm text-slate-500">
            {formatCurrency(success.amount, success.currency)} til {success.creditorName}
          </p>
          <div className="mt-4 rounded-lg bg-slate-50 p-3 text-left text-sm">
            <div className="flex justify-between py-1">
              <span className="text-slate-500">Status</span>
              <span className="font-medium text-slate-800">{statusLabel(success.status)}</span>
            </div>
            {success.reference && (
              <div className="flex justify-between py-1">
                <span className="text-slate-500">Referanse</span>
                <span className="font-medium text-slate-800">{success.reference}</span>
              </div>
            )}
          </div>
          <div className="mt-6 flex justify-center gap-3">
            <Link
              to="/$tenant/payments"
              params={{ tenant }}
              className="rounded-lg border border-slate-200 bg-white px-4 py-2 text-sm font-medium text-slate-700 no-underline hover:bg-slate-50"
            >
              Se betalinger
            </Link>
            <button
              onClick={() => {
                setSuccess(null)
                setForm({
                  debitAccountId: accounts[0]?.id || '',
                  creditorIban: '',
                  creditorName: '',
                  amount: '',
                  reference: '',
                  kid: '',
                })
              }}
              className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800"
            >
              Ny betaling
            </button>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-lg space-y-6">
      <div className="animate-fade-in">
        <div className="flex items-center gap-2 text-sm text-slate-500 mb-4">
          <Link
            to="/$tenant/payments"
            params={{ tenant }}
            className="text-slate-500 no-underline hover:text-slate-700"
          >
            Betalinger
          </Link>
          <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
          </svg>
          <span className="text-slate-800 font-medium">Ny betaling</span>
        </div>

        <h1 className="text-2xl font-bold text-slate-900">Ny betaling</h1>
        <p className="mt-1 text-slate-500">Fyll ut skjemaet for a opprette en betaling</p>
      </div>

      <form onSubmit={handleSubmit} className="animate-fade-in space-y-5 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm" style={{ animationDelay: '100ms' }}>
        {error && (
          <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            {error}
          </div>
        )}

        {/* From account */}
        <div>
          <label className="mb-1.5 block text-sm font-medium text-slate-700">
            Fra konto
          </label>
          {loadingAccounts ? (
            <div className="h-11 w-full rounded-lg bg-slate-100 animate-pulse" />
          ) : (
            <select
              value={form.debitAccountId}
              onChange={(e) => updateField('debitAccountId', e.target.value)}
              className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100"
            >
              {accounts.map((acc) => (
                <option key={acc.id} value={acc.id}>
                  {acc.name} - {formatIban(acc.iban)} ({formatCurrency(acc.balance, acc.currency)})
                </option>
              ))}
            </select>
          )}
        </div>

        {/* Creditor name */}
        <div>
          <label className="mb-1.5 block text-sm font-medium text-slate-700">
            Mottaker
          </label>
          <input
            type="text"
            placeholder="Navn pa mottaker"
            value={form.creditorName}
            onChange={(e) => updateField('creditorName', e.target.value)}
            className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100"
          />
        </div>

        {/* IBAN */}
        <div>
          <label className="mb-1.5 block text-sm font-medium text-slate-700">
            Kontonummer / IBAN
          </label>
          <input
            type="text"
            placeholder="NO12 3456 7890 1234"
            value={form.creditorIban}
            onChange={(e) => updateField('creditorIban', e.target.value)}
            className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 font-mono text-sm outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100"
          />
        </div>

        {/* Amount */}
        <div>
          <label className="mb-1.5 block text-sm font-medium text-slate-700">
            Belop (NOK)
          </label>
          <input
            type="text"
            inputMode="decimal"
            placeholder="0,00"
            value={form.amount}
            onChange={(e) => updateField('amount', e.target.value)}
            className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100"
          />
        </div>

        {/* Reference / KID */}
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label className="mb-1.5 block text-sm font-medium text-slate-700">
              Melding / referanse
            </label>
            <input
              type="text"
              placeholder="Valgfritt"
              value={form.reference}
              onChange={(e) => updateField('reference', e.target.value)}
              className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100"
            />
          </div>
          <div>
            <label className="mb-1.5 block text-sm font-medium text-slate-700">
              KID
            </label>
            <input
              type="text"
              placeholder="Valgfritt"
              value={form.kid}
              onChange={(e) => updateField('kid', e.target.value)}
              className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 font-mono text-sm outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100"
            />
          </div>
        </div>

        {/* Submit */}
        <div className="flex gap-3 pt-2">
          <button
            type="button"
            onClick={() => navigate({ to: '/$tenant/payments', params: { tenant } })}
            className="flex-1 rounded-lg border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 transition hover:bg-slate-50"
          >
            Avbryt
          </button>
          <button
            type="submit"
            disabled={submitting}
            className="flex-1 rounded-lg bg-slate-900 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-slate-800 disabled:opacity-50"
          >
            {submitting ? (
              <span className="flex items-center justify-center gap-2">
                <svg className="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                </svg>
                Sender...
              </span>
            ) : (
              'Send betaling'
            )}
          </button>
        </div>
      </form>
    </div>
  )
}

function statusLabel(status: string): string {
  const map: Record<string, string> = {
    COMPLETED: 'Fullfort',
    REQUESTED: 'Sendt',
    PENDING: 'Venter',
    FAILED: 'Feilet',
  }
  return map[status] || status
}
