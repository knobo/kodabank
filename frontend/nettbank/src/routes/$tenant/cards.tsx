import { createFileRoute } from '@tanstack/react-router'
import { useState, useEffect } from 'react'
import { fetchCards, toggleCardBlock } from '../../lib/api'
import type { Card } from '../../lib/api'

export const Route = createFileRoute('/$tenant/cards')({
  component: CardsPage,
})

function CardsPage() {
  const { tenant } = Route.useParams()
  const [cards, setCards] = useState<Card[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [togglingId, setTogglingId] = useState<string | null>(null)

  useEffect(() => {
    fetchCards(tenant)
      .then((data) => {
        setCards(data)
        setLoading(false)
      })
      .catch(() => {
        setError('Kunne ikke koble til server')
        setLoading(false)
      })
  }, [tenant])

  async function handleToggleBlock(card: Card) {
    setTogglingId(card.id)
    try {
      const shouldBlock = card.status === 'ACTIVE'
      const updated = await toggleCardBlock(tenant, card.id, shouldBlock)
      setCards((prev) => prev.map((c) => (c.id === card.id ? updated : c)))
    } catch {
      // Show inline error - for demo just ignore
    }
    setTogglingId(null)
  }

  if (loading) {
    return (
      <div className="space-y-4">
        <div className="h-8 w-32 rounded bg-slate-200 animate-pulse" />
        <div className="grid gap-4 sm:grid-cols-2">
          {[1, 2].map((n) => (
            <div key={n} className="h-52 rounded-2xl bg-slate-200 animate-pulse" />
          ))}
        </div>
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
      <div className="animate-fade-in">
        <h1 className="text-2xl font-bold text-slate-900">Kort</h1>
        <p className="mt-1 text-slate-500">Administrer bankkortene dine</p>
      </div>

      {cards.length === 0 ? (
        <div className="rounded-xl border border-slate-200 bg-white px-6 py-12 text-center">
          <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-slate-100">
            <svg className="h-6 w-6 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z" />
            </svg>
          </div>
          <p className="text-sm text-slate-500">Ingen kort registrert</p>
        </div>
      ) : (
        <div className="grid gap-6 sm:grid-cols-2">
          {cards.map((card, i) => (
            <div
              key={card.id}
              className="animate-fade-in"
              style={{ animationDelay: `${(i + 1) * 100}ms` }}
            >
              {/* Card visual */}
              <div
                className={`relative overflow-hidden rounded-2xl p-6 text-white shadow-lg ${
                  card.status === 'BLOCKED'
                    ? 'bg-gradient-to-br from-slate-500 to-slate-700'
                    : card.status === 'EXPIRED'
                      ? 'bg-gradient-to-br from-slate-400 to-slate-500'
                      : card.type === 'VISA'
                        ? 'bg-gradient-to-br from-blue-600 to-indigo-700'
                        : 'bg-gradient-to-br from-slate-800 to-slate-900'
                }`}
              >
                {/* Decorative circles */}
                <div className="pointer-events-none absolute -right-8 -top-8 h-32 w-32 rounded-full bg-white/10" />
                <div className="pointer-events-none absolute -bottom-10 -left-10 h-28 w-28 rounded-full bg-white/5" />

                <div className="relative">
                  {/* Card type */}
                  <div className="mb-8 flex items-center justify-between">
                    <span className="text-sm font-medium text-white/70">
                      {card.type}
                    </span>
                    {card.status === 'BLOCKED' && (
                      <span className="rounded bg-red-500/80 px-2 py-0.5 text-xs font-medium">
                        SPERRET
                      </span>
                    )}
                    {card.status === 'EXPIRED' && (
                      <span className="rounded bg-amber-500/80 px-2 py-0.5 text-xs font-medium">
                        UTLOPT
                      </span>
                    )}
                  </div>

                  {/* Card number */}
                  <p className="mb-6 font-mono text-xl tracking-widest">
                    {card.maskedNumber}
                  </p>

                  {/* Card holder + expiry */}
                  <div className="flex items-end justify-between">
                    <div>
                      <p className="text-xs text-white/60">Kortinnehaver</p>
                      <p className="text-sm font-medium">{card.cardholderName}</p>
                    </div>
                    <div className="text-right">
                      <p className="text-xs text-white/60">Utloper</p>
                      <p className="font-mono text-sm font-medium">
                        {card.expiryDate}
                      </p>
                    </div>
                  </div>
                </div>
              </div>

              {/* Card actions */}
              <div className="mt-3 flex gap-2">
                {card.status !== 'EXPIRED' && (
                  <button
                    onClick={() => handleToggleBlock(card)}
                    disabled={togglingId === card.id}
                    className={`flex-1 rounded-lg border px-4 py-2 text-sm font-medium transition disabled:opacity-50 ${
                      card.status === 'ACTIVE'
                        ? 'border-red-200 bg-red-50 text-red-700 hover:bg-red-100'
                        : 'border-green-200 bg-green-50 text-green-700 hover:bg-green-100'
                    }`}
                  >
                    {togglingId === card.id ? (
                      <span className="flex items-center justify-center gap-2">
                        <svg className="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none">
                          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                        </svg>
                      </span>
                    ) : card.status === 'ACTIVE' ? (
                      'Sperr kort'
                    ) : (
                      'Aktiver kort'
                    )}
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
