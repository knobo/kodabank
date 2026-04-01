import { createFileRoute, Link } from '@tanstack/react-router'
import { useState, useEffect } from 'react'
import { fetchPayments } from '../../../lib/api'
import type { Payment } from '../../../lib/api'
import { formatCurrency, formatDateShort } from '../../../lib/format'

export const Route = createFileRoute('/$tenant/payments/')({
  component: PaymentHistoryPage,
})

function PaymentHistoryPage() {
  const { tenant } = Route.useParams()
  const [payments, setPayments] = useState<Payment[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchPayments(tenant)
      .then((data) => {
        setPayments(data)
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
        <div className="h-8 w-40 rounded bg-slate-200 animate-pulse" />
        {[1, 2, 3].map((n) => (
          <div key={n} className="h-20 rounded-xl bg-slate-200 animate-pulse" />
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

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between animate-fade-in">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Betalinger</h1>
          <p className="mt-1 text-slate-500">Oversikt over betalinger</p>
        </div>
        <Link
          to="/$tenant/payments/new"
          params={{ tenant }}
          className="flex items-center gap-2 rounded-xl bg-slate-900 px-4 py-2.5 text-sm font-semibold text-white shadow-sm no-underline transition hover:bg-slate-800"
        >
          <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
          </svg>
          Ny betaling
        </Link>
      </div>

      <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
        {payments.length === 0 ? (
          <div className="px-6 py-12 text-center">
            <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-slate-100">
              <svg className="h-6 w-6 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
              </svg>
            </div>
            <p className="text-sm text-slate-500">Ingen betalinger enna</p>
            <Link
              to="/$tenant/payments/new"
              params={{ tenant }}
              className="mt-3 inline-block text-sm font-medium text-blue-600 no-underline hover:text-blue-700"
            >
              Opprett din forste betaling
            </Link>
          </div>
        ) : (
          <div className="divide-y divide-slate-100">
            {payments.map((payment, i) => (
              <div
                key={payment.id}
                className="animate-fade-in flex items-center justify-between px-5 py-4 transition hover:bg-slate-50"
                style={{ animationDelay: `${i * 60}ms` }}
              >
                <div className="flex items-center gap-4">
                  <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-slate-100">
                    <svg className="h-5 w-5 text-slate-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                      <path strokeLinecap="round" strokeLinejoin="round" d="M17 9V7a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2m2 4h10a2 2 0 002-2v-6a2 2 0 00-2-2H9a2 2 0 00-2 2v6a2 2 0 002 2zm7-5a2 2 0 11-4 0 2 2 0 014 0z" />
                    </svg>
                  </div>
                  <div>
                    <p className="text-sm font-semibold text-slate-800">
                      {payment.creditorName}
                    </p>
                    <p className="text-xs text-slate-400">
                      {formatDateShort(payment.createdAt)}
                      {payment.reference && ` - Ref: ${payment.reference}`}
                      {payment.kid && ` - KID: ${payment.kid}`}
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-4">
                  <span className="text-sm font-semibold text-slate-800">
                    {formatCurrency(payment.amount, payment.currency)}
                  </span>
                  <StatusBadge status={payment.status} />
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

function StatusBadge({ status }: { status: Payment['status'] }) {
  const styles: Record<string, string> = {
    COMPLETED: 'bg-green-50 text-green-700 border-green-200',
    REQUESTED: 'bg-blue-50 text-blue-700 border-blue-200',
    PENDING: 'bg-amber-50 text-amber-700 border-amber-200',
    FAILED: 'bg-red-50 text-red-700 border-red-200',
  }

  const labels: Record<string, string> = {
    COMPLETED: 'Fullfort',
    REQUESTED: 'Sendt',
    PENDING: 'Venter',
    FAILED: 'Feilet',
  }

  return (
    <span
      className={`rounded-full border px-2.5 py-0.5 text-xs font-medium ${styles[status] || 'bg-slate-50 text-slate-600 border-slate-200'}`}
    >
      {labels[status] || status}
    </span>
  )
}
