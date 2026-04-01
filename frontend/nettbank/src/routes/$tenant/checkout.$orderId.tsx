import { createFileRoute } from '@tanstack/react-router'
import { useState, useEffect } from 'react'
import { getCheckoutOrder, authorizePayment, fetchAccounts } from '../../lib/api'
import type { CheckoutOrder, Account } from '../../lib/api'
import { formatCurrency } from '../../lib/format'

export const Route = createFileRoute('/$tenant/checkout/$orderId')({
  component: CheckoutPage,
})

function CheckoutPage() {
  const { tenant, orderId } = Route.useParams()

  const [order, setOrder] = useState<CheckoutOrder | null>(null)
  const [accounts, setAccounts] = useState<Account[]>([])
  const [selectedAccountId, setSelectedAccountId] = useState('')
  const [loading, setLoading] = useState(true)
  const [authorizing, setAuthorizing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [authorized, setAuthorized] = useState(false)

  useEffect(() => {
    Promise.all([
      getCheckoutOrder(tenant, orderId),
      fetchAccounts(tenant),
    ])
      .then(([orderData, accountsData]) => {
        setOrder(orderData)
        setAccounts(accountsData)
        if (accountsData.length > 0) {
          setSelectedAccountId(accountsData[0].id)
        }
        setLoading(false)
      })
      .catch((err) => {
        if (err?.status === 404) {
          setError('Betalingsordren ble ikke funnet.')
        } else if (err?.status === 401) {
          setError('Du ma logge inn for a godkjenne denne betalingen.')
        } else {
          setError('Kunne ikke hente betalingsinformasjon. Prov igjen senere.')
        }
        setLoading(false)
      })
  }, [tenant, orderId])

  const isExpired = order ? new Date(order.expiresAt) < new Date() : false
  const canAuthorize = order?.status === 'CREATED' && !isExpired

  async function handleAuthorize() {
    if (!order || !selectedAccountId) return
    setError(null)
    setAuthorizing(true)

    try {
      const result = await authorizePayment(tenant, orderId, selectedAccountId)
      setOrder(result)
      setAuthorized(true)
    } catch (err: any) {
      let message = 'Betalingen kunne ikke godkjennes. Prov igjen.'
      if (err?.body) {
        try {
          const parsed = JSON.parse(err.body)
          if (parsed.message) message = parsed.message
        } catch {
          // use default
        }
      }
      setError(message)
    } finally {
      setAuthorizing(false)
    }
  }

  function handleCancel() {
    if (order?.callbackUrl) {
      const url = new URL(order.callbackUrl)
      url.searchParams.set('orderId', orderId)
      url.searchParams.set('status', 'cancelled')
      window.location.href = url.toString()
    } else {
      window.history.back()
    }
  }

  function handleReturnToMerchant() {
    if (order?.callbackUrl) {
      const url = new URL(order.callbackUrl)
      url.searchParams.set('orderId', orderId)
      url.searchParams.set('status', 'authorized')
      window.location.href = url.toString()
    }
  }

  // Loading state
  if (loading) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <div className="text-center">
          <div className="mx-auto mb-4 h-10 w-10 animate-spin rounded-full border-4 border-slate-200 border-t-slate-600" />
          <p className="text-sm text-slate-500">Henter betalingsinformasjon...</p>
        </div>
      </div>
    )
  }

  // Error state (no order loaded)
  if (error && !order) {
    return (
      <div className="mx-auto max-w-lg py-12">
        <div className="rounded-2xl border border-red-200 bg-white p-8 text-center shadow-sm">
          <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-red-50">
            <svg className="h-8 w-8 text-red-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L3.732 16.5c-.77.833.192 2.5 1.732 2.5z" />
            </svg>
          </div>
          <h2 className="text-xl font-bold text-slate-900">Noe gikk galt</h2>
          <p className="mt-2 text-sm text-slate-500">{error}</p>
        </div>
      </div>
    )
  }

  if (!order) return null

  // Expired state
  if (isExpired && order.status === 'CREATED') {
    return (
      <div className="mx-auto max-w-lg py-12">
        <div className="rounded-2xl border border-amber-200 bg-white p-8 text-center shadow-sm">
          <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-amber-50">
            <svg className="h-8 w-8 text-amber-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          </div>
          <h2 className="text-xl font-bold text-slate-900">Betalingen har utlopt</h2>
          <p className="mt-2 text-sm text-slate-500">
            Denne betalingsforesporselen er ikke lenger gyldig. Ga tilbake til butikken for a starte en ny betaling.
          </p>
          {order.callbackUrl && (
            <button
              onClick={handleCancel}
              className="mt-6 rounded-lg bg-slate-900 px-6 py-2.5 text-sm font-semibold text-white transition hover:bg-slate-800"
            >
              Tilbake til {order.merchantName}
            </button>
          )}
        </div>
      </div>
    )
  }

  // Success / authorized state
  if (authorized || order.status === 'AUTHORIZED') {
    return (
      <div className="mx-auto max-w-lg py-12">
        <div className="animate-fade-in rounded-2xl border border-slate-200 bg-white p-8 text-center shadow-sm">
          <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-green-50">
            <svg className="h-8 w-8 text-green-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
            </svg>
          </div>
          <h2 className="text-xl font-bold text-slate-900">Betaling godkjent</h2>
          <p className="mt-2 text-sm text-slate-500">
            Du har godkjent betalingen pa {formatCurrency(order.amount, order.currency)} til {order.merchantName}.
          </p>
          <div className="mt-4 rounded-lg bg-slate-50 p-4 text-left text-sm">
            <div className="flex justify-between py-1.5">
              <span className="text-slate-500">Butikk</span>
              <span className="font-medium text-slate-800">{order.merchantName}</span>
            </div>
            <div className="flex justify-between py-1.5">
              <span className="text-slate-500">Belop</span>
              <span className="font-medium text-slate-800">{formatCurrency(order.amount, order.currency)}</span>
            </div>
            {order.description && (
              <div className="flex justify-between py-1.5">
                <span className="text-slate-500">Beskrivelse</span>
                <span className="font-medium text-slate-800">{order.description}</span>
              </div>
            )}
            <div className="flex justify-between py-1.5">
              <span className="text-slate-500">Status</span>
              <span className="inline-flex items-center gap-1.5 font-medium text-green-700">
                <span className="h-1.5 w-1.5 rounded-full bg-green-500" />
                Godkjent
              </span>
            </div>
          </div>
          {order.callbackUrl && (
            <button
              onClick={handleReturnToMerchant}
              className="mt-6 w-full rounded-lg bg-slate-900 px-6 py-3 text-sm font-semibold text-white transition hover:bg-slate-800"
            >
              Tilbake til {order.merchantName}
            </button>
          )}
        </div>
      </div>
    )
  }

  // Already cancelled/captured/refunded
  if (order.status !== 'CREATED') {
    return (
      <div className="mx-auto max-w-lg py-12">
        <div className="rounded-2xl border border-slate-200 bg-white p-8 text-center shadow-sm">
          <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-slate-100">
            <svg className="h-8 w-8 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          </div>
          <h2 className="text-xl font-bold text-slate-900">Betalingen kan ikke behandles</h2>
          <p className="mt-2 text-sm text-slate-500">
            Denne betalingen har status: {statusLabel(order.status)}.
          </p>
          {order.callbackUrl && (
            <button
              onClick={handleCancel}
              className="mt-6 rounded-lg bg-slate-900 px-6 py-2.5 text-sm font-semibold text-white transition hover:bg-slate-800"
            >
              Tilbake til {order.merchantName}
            </button>
          )}
        </div>
      </div>
    )
  }

  // Main checkout form
  const selectedAccount = accounts.find(a => a.id === selectedAccountId)

  return (
    <div className="mx-auto max-w-lg py-8">
      {/* Merchant header */}
      <div className="animate-fade-in mb-6 text-center">
        <div className="mx-auto mb-3 flex h-14 w-14 items-center justify-center rounded-xl bg-slate-900 text-xl font-bold text-white">
          {order.merchantName.charAt(0).toUpperCase()}
        </div>
        <h1 className="text-lg font-semibold text-slate-900">{order.merchantName}</h1>
        <p className="mt-0.5 text-xs text-slate-400">ber om betaling</p>
      </div>

      {/* Payment card */}
      <div className="animate-fade-in rounded-2xl border border-slate-200 bg-white shadow-sm" style={{ animationDelay: '80ms' }}>
        {/* Amount display */}
        <div className="border-b border-slate-100 px-6 py-6 text-center">
          <p className="text-sm font-medium text-slate-500">Totalbelop</p>
          <p className="mt-1 text-3xl font-bold text-slate-900">
            {formatCurrency(order.amount, order.currency)}
          </p>
          {order.description && (
            <p className="mt-2 text-sm text-slate-500">{order.description}</p>
          )}
        </div>

        {/* Items breakdown */}
        {order.items.length > 0 && (
          <div className="border-b border-slate-100 px-6 py-4">
            <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-400">Ordredetaljer</p>
            <div className="space-y-2">
              {order.items.map((item, i) => (
                <div key={i} className="flex items-center justify-between text-sm">
                  <div className="flex items-center gap-2">
                    <span className="text-slate-700">{item.name}</span>
                    <span className="text-slate-400">x{item.quantity}</span>
                  </div>
                  <span className="font-medium text-slate-800">
                    {formatCurrency(item.unitPrice * item.quantity, order.currency)}
                  </span>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Account selector */}
        <div className="px-6 py-5">
          <label className="mb-2 block text-sm font-medium text-slate-700">
            Betal fra konto
          </label>
          {accounts.length === 0 ? (
            <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-700">
              Ingen kontoer tilgjengelig. Du ma ha en aktiv konto for a godkjenne betalingen.
            </div>
          ) : (
            <select
              value={selectedAccountId}
              onChange={(e) => setSelectedAccountId(e.target.value)}
              className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100"
            >
              {accounts.map((acc) => (
                <option key={acc.id} value={acc.id}>
                  {acc.name} ({formatCurrency(acc.balance, acc.currency)})
                </option>
              ))}
            </select>
          )}
          {selectedAccount && selectedAccount.balance < order.amount && (
            <p className="mt-2 text-xs text-amber-600">
              Kontoen har ikke nok midler. Saldo: {formatCurrency(selectedAccount.balance, selectedAccount.currency)}
            </p>
          )}
        </div>

        {/* Error message */}
        {error && (
          <div className="mx-6 mb-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            {error}
          </div>
        )}

        {/* Actions */}
        <div className="flex gap-3 border-t border-slate-100 px-6 py-5">
          <button
            type="button"
            onClick={handleCancel}
            disabled={authorizing}
            className="flex-1 rounded-lg border border-slate-200 bg-white px-4 py-3 text-sm font-semibold text-slate-700 transition hover:bg-slate-50 disabled:opacity-50"
          >
            Avbryt
          </button>
          <button
            type="button"
            onClick={handleAuthorize}
            disabled={authorizing || !canAuthorize || accounts.length === 0}
            className="flex-[2] rounded-lg bg-slate-900 px-4 py-3 text-sm font-semibold text-white shadow-sm transition hover:bg-slate-800 disabled:opacity-50"
          >
            {authorizing ? (
              <span className="flex items-center justify-center gap-2">
                <svg className="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                </svg>
                Godkjenner...
              </span>
            ) : (
              `Godkjenn ${formatCurrency(order.amount, order.currency)}`
            )}
          </button>
        </div>
      </div>

      {/* Security footer */}
      <div className="mt-4 flex items-center justify-center gap-1.5 text-xs text-slate-400">
        <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
        </svg>
        Sikker betaling gjennom din nettbank
      </div>

      {/* Expiry notice */}
      <div className="mt-2 text-center text-xs text-slate-400">
        Utloper: {new Date(order.expiresAt).toLocaleString('nb-NO', {
          day: 'numeric',
          month: 'long',
          year: 'numeric',
          hour: '2-digit',
          minute: '2-digit',
        })}
      </div>
    </div>
  )
}

function statusLabel(status: string): string {
  const map: Record<string, string> = {
    CREATED: 'Opprettet',
    AUTHORIZED: 'Godkjent',
    CAPTURED: 'Gjennomfort',
    CANCELLED: 'Kansellert',
    REFUNDED: 'Refundert',
    EXPIRED: 'Utlopt',
  }
  return map[status] || status
}
