import { useState, useEffect, useCallback } from 'react'

// -- Config --

// In dev mode, use empty string so calls go through Vite's proxy (/api/v1/...)
// In production or when explicitly set, use the full gateway URL
const GATEWAY_URL = import.meta.env.VITE_GATEWAY_URL || ''
const CHECKOUT_URL = import.meta.env.VITE_CHECKOUT_URL || 'http://localhost:3100'

// -- Types --

interface Product {
  id: string
  name: string
  description: string
  price: number
  currency: string
  image: string
  recurring?: boolean
  interval?: string
}

interface PaymentOrderResponse {
  orderId: string
  status: string
}

interface SubscriptionResponse {
  subscriptionId: string
  status: string
}

interface Settings {
  apiKey: string
  tenant: string
}

// -- Product data --

const products: Product[] = [
  { id: 'gold-100', name: '100 Gold Coins', description: 'A pouch of gold coins', price: 49.0, currency: 'NOK', image: '\u{1FA99}' },
  { id: 'gem-pack', name: 'Gem Pack', description: '10 rare gems for crafting', price: 149.0, currency: 'NOK', image: '\u{1F48E}' },
  { id: 'legendary-sword', name: 'Legendary Sword', description: 'The Blade of Eternal Fire', price: 299.0, currency: 'NOK', image: '\u2694\uFE0F' },
  { id: 'health-potion', name: 'Health Potion x5', description: 'Restore your health in battle', price: 25.0, currency: 'NOK', image: '\u{1F9EA}' },
  { id: 'monthly-vip', name: 'VIP Membership', description: 'Monthly subscription - exclusive perks', price: 99.0, currency: 'NOK', recurring: true, interval: 'MONTHLY', image: '\u{1F451}' },
]

// -- Helpers --

function loadSettings(): Settings {
  try {
    const stored = localStorage.getItem('webshop-settings')
    if (stored) return JSON.parse(stored) as Settings
  } catch {
    // ignore
  }
  return { apiKey: '', tenant: '' }
}

function saveSettings(settings: Settings) {
  localStorage.setItem('webshop-settings', JSON.stringify(settings))
}

function formatPrice(price: number, currency: string): string {
  return new Intl.NumberFormat('nb-NO', {
    style: 'currency',
    currency,
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  }).format(price)
}

// -- Components --

function SettingsPanel({
  settings,
  onSave,
  onClose,
}: {
  settings: Settings
  onSave: (s: Settings) => void
  onClose: () => void
}) {
  const [apiKey, setApiKey] = useState(settings.apiKey)
  const [tenant, setTenant] = useState(settings.tenant)

  function handleSave() {
    const next = { apiKey: apiKey.trim(), tenant: tenant.trim() }
    saveSettings(next)
    onSave(next)
    onClose()
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
      <div className="w-full max-w-md rounded-2xl border border-dragon-700/50 bg-[#1a1035] p-6 shadow-2xl">
        <h2 className="mb-4 font-display text-xl font-bold text-dragon-300">Settings</h2>

        <label className="mb-1 block text-sm font-medium text-dragon-200">API Key</label>
        <input
          type="text"
          value={apiKey}
          onChange={(e) => setApiKey(e.target.value)}
          placeholder="Enter your merchant API key"
          className="mb-4 w-full rounded-lg border border-dragon-700/50 bg-dragon-950/50 px-3 py-2.5 text-sm text-white placeholder-dragon-600 outline-none focus:border-dragon-500 focus:ring-1 focus:ring-dragon-500"
        />

        <label className="mb-1 block text-sm font-medium text-dragon-200">Bank Tenant</label>
        <input
          type="text"
          value={tenant}
          onChange={(e) => setTenant(e.target.value)}
          placeholder="e.g. kodabank"
          className="mb-6 w-full rounded-lg border border-dragon-700/50 bg-dragon-950/50 px-3 py-2.5 text-sm text-white placeholder-dragon-600 outline-none focus:border-dragon-500 focus:ring-1 focus:ring-dragon-500"
        />

        <div className="flex gap-3">
          <button
            type="button"
            onClick={onClose}
            className="flex-1 rounded-lg border border-dragon-700/50 px-4 py-2.5 text-sm font-semibold text-dragon-300 transition hover:bg-dragon-900/30"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={handleSave}
            className="flex-1 rounded-lg bg-dragon-600 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-dragon-500"
          >
            Save
          </button>
        </div>
      </div>
    </div>
  )
}

function SuccessBanner({ orderId, onDismiss }: { orderId: string; onDismiss: () => void }) {
  return (
    <div className="animate-fade-in-up mb-6 flex items-center justify-between rounded-xl border border-green-500/30 bg-green-500/10 px-5 py-4">
      <div className="flex items-center gap-3">
        <span className="text-2xl">{'\u2705'}</span>
        <div>
          <p className="font-semibold text-green-300">Payment successful!</p>
          <p className="text-sm text-green-400/80">
            Your items have been added. Order: {orderId}
          </p>
        </div>
      </div>
      <button
        onClick={onDismiss}
        className="rounded-lg px-3 py-1.5 text-sm text-green-400 transition hover:bg-green-500/10"
      >
        Dismiss
      </button>
    </div>
  )
}

function ProductCard({
  product,
  onBuy,
  loading,
}: {
  product: Product
  onBuy: (p: Product) => void
  loading: boolean
}) {
  const isRecurring = product.recurring

  return (
    <div className="card-hover flex flex-col rounded-2xl border border-dragon-700/30 bg-gradient-to-b from-[#1e1245]/80 to-[#150e30]/90 p-5 backdrop-blur-sm">
      {/* Product image */}
      <div className="mb-4 flex items-center justify-center">
        <span className="text-[4.5rem] leading-none drop-shadow-lg">{product.image}</span>
      </div>

      {/* Product info */}
      <h3 className="mb-1 text-center font-display text-lg font-bold text-white">
        {product.name}
      </h3>
      <p className="mb-4 flex-1 text-center text-sm text-dragon-300/70">
        {product.description}
      </p>

      {/* Price */}
      <p className="mb-4 text-center text-xl font-bold text-gold-400">
        {formatPrice(product.price, product.currency)}
        {isRecurring && <span className="text-sm font-normal text-gold-400/60">/mnd</span>}
      </p>

      {/* Buy button */}
      <button
        type="button"
        disabled={loading}
        onClick={() => onBuy(product)}
        className={`w-full rounded-xl px-4 py-3 text-sm font-bold uppercase tracking-wide transition disabled:opacity-50 ${
          isRecurring
            ? 'bg-gradient-to-r from-gold-500 to-gold-600 text-black shadow-lg shadow-gold-500/20 hover:from-gold-400 hover:to-gold-500'
            : 'bg-gradient-to-r from-green-500 to-emerald-600 text-white shadow-lg shadow-green-500/20 hover:from-green-400 hover:to-emerald-500'
        }`}
      >
        {loading ? (
          <span className="flex items-center justify-center gap-2">
            <svg className="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
            </svg>
            Processing...
          </span>
        ) : isRecurring ? (
          'Subscribe'
        ) : (
          'Buy Now'
        )}
      </button>
    </div>
  )
}

// -- Main App --

export default function App() {
  const [settings, setSettings] = useState<Settings>(loadSettings)
  const [showSettings, setShowSettings] = useState(false)
  const [successOrderId, setSuccessOrderId] = useState<string | null>(null)
  const [loadingProductId, setLoadingProductId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  // Check for callback params on mount
  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    const orderId = params.get('orderId')
    const status = params.get('status')

    if (orderId && (status === 'authorized' || status === 'success')) {
      setSuccessOrderId(orderId)
      // Clean up the URL
      window.history.replaceState({}, '', window.location.pathname)
    }
  }, [])

  const handleBuy = useCallback(
    async (product: Product) => {
      if (!settings.apiKey) {
        setShowSettings(true)
        return
      }
      if (!settings.tenant) {
        setShowSettings(true)
        return
      }

      setError(null)
      setLoadingProductId(product.id)

      try {
        if (product.recurring) {
          // Subscribe flow - create subscription
          const res = await fetch(`${GATEWAY_URL}/api/v1/subscriptions`, {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              Authorization: `Bearer ${settings.apiKey}`,
            },
            body: JSON.stringify({
              amount: product.price,
              currency: product.currency,
              interval: product.interval,
              description: product.name,
              // These would normally come from the logged-in user
              userId: 'demo-user',
              payerAccountId: 'demo-account',
              startDate: new Date().toISOString().split('T')[0],
            }),
          })

          if (!res.ok) {
            const text = await res.text()
            throw new Error(`Subscription failed: ${res.status} ${text}`)
          }

          const data = (await res.json()) as SubscriptionResponse
          setSuccessOrderId(data.subscriptionId)
        } else {
          // Payment flow - create payment order
          const callbackUrl = window.location.origin + window.location.pathname

          const res = await fetch(`${GATEWAY_URL}/api/v1/payments`, {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              Authorization: `Bearer ${settings.apiKey}`,
            },
            body: JSON.stringify({
              amount: product.price,
              currency: product.currency,
              description: product.name,
              callbackUrl,
              items: [
                {
                  name: product.name,
                  quantity: 1,
                  unitPrice: product.price,
                },
              ],
            }),
          })

          if (!res.ok) {
            const text = await res.text()
            throw new Error(`Payment creation failed: ${res.status} ${text}`)
          }

          const data = (await res.json()) as PaymentOrderResponse

          // Redirect to bank checkout
          const checkoutUrl = `${CHECKOUT_URL}/${settings.tenant}/checkout/${data.orderId}`
          window.location.href = checkoutUrl
        }
      } catch (err) {
        console.error('Payment error:', err)
        setError(err instanceof Error ? err.message : 'Something went wrong')
      } finally {
        setLoadingProductId(null)
      }
    },
    [settings],
  )

  return (
    <div className="min-h-screen">
      {/* Header */}
      <header className="border-b border-dragon-800/50 bg-[#0f0a1a]/80 backdrop-blur-md">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-6 py-4">
          <div className="flex items-center gap-3">
            <span className="text-3xl">{'\u{1F409}'}</span>
            <div>
              <h1 className="font-display text-xl font-bold tracking-wide text-white">
                Dragon's Hoard
              </h1>
              <p className="text-xs text-dragon-400">Game Shop</p>
            </div>
          </div>
          <button
            type="button"
            onClick={() => setShowSettings(true)}
            className="flex items-center gap-2 rounded-lg border border-dragon-700/40 px-3 py-2 text-sm text-dragon-300 transition hover:border-dragon-500 hover:bg-dragon-900/30"
          >
            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.066 2.573c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.573 1.066c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.066-2.573c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
              <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
            </svg>
            Settings
          </button>
        </div>
      </header>

      {/* Main content */}
      <main className="mx-auto max-w-5xl px-6 py-8">
        {/* Config warning */}
        {(!settings.apiKey || !settings.tenant) && (
          <div className="animate-fade-in-up mb-6 flex items-center gap-3 rounded-xl border border-gold-500/30 bg-gold-500/10 px-5 py-4">
            <span className="text-xl">{'\u26A0\uFE0F'}</span>
            <div>
              <p className="font-semibold text-gold-300">Configuration required</p>
              <p className="text-sm text-gold-400/80">
                Set your API key and bank tenant in{' '}
                <button
                  onClick={() => setShowSettings(true)}
                  className="underline hover:text-gold-300"
                >
                  Settings
                </button>{' '}
                to start making payments.
              </p>
            </div>
          </div>
        )}

        {/* Success banner */}
        {successOrderId && (
          <SuccessBanner orderId={successOrderId} onDismiss={() => setSuccessOrderId(null)} />
        )}

        {/* Error banner */}
        {error && (
          <div className="animate-fade-in-up mb-6 flex items-center justify-between rounded-xl border border-red-500/30 bg-red-500/10 px-5 py-4">
            <div className="flex items-center gap-3">
              <span className="text-2xl">{'\u274C'}</span>
              <div>
                <p className="font-semibold text-red-300">Error</p>
                <p className="text-sm text-red-400/80">{error}</p>
              </div>
            </div>
            <button
              onClick={() => setError(null)}
              className="rounded-lg px-3 py-1.5 text-sm text-red-400 transition hover:bg-red-500/10"
            >
              Dismiss
            </button>
          </div>
        )}

        {/* Product grid */}
        <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {products.map((product, i) => (
            <div key={product.id} className="animate-fade-in-up" style={{ animationDelay: `${i * 80}ms` }}>
              <ProductCard
                product={product}
                onBuy={handleBuy}
                loading={loadingProductId === product.id}
              />
            </div>
          ))}
        </div>
      </main>

      {/* Footer */}
      <footer className="mt-12 border-t border-dragon-800/50 bg-[#0f0a1a]/60">
        <div className="mx-auto max-w-5xl px-6 py-6 text-center text-sm text-dragon-500">
          Powered by <span className="font-semibold text-dragon-400">KodaBank Payment Gateway</span>
        </div>
      </footer>

      {/* Settings modal */}
      {showSettings && (
        <SettingsPanel
          settings={settings}
          onSave={setSettings}
          onClose={() => setShowSettings(false)}
        />
      )}
    </div>
  )
}
