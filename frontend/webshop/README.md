# Dragon's Hoard - Demo Webshop

A minimal demo webshop that demonstrates the KodaBank payment gateway API. Built with Vite + React + Tailwind CSS 4.

## Running locally

```bash
cd frontend/webshop
npm install
npm run dev
```

The app runs on http://localhost:3200.

### Configuration

1. Click the Settings icon in the header
2. Enter your merchant API key (from the payment gateway admin)
3. Enter the bank tenant name (e.g. `kodabank`)

### Environment variables

| Variable | Default | Description |
|---|---|---|
| `VITE_GATEWAY_URL` | `http://localhost:8089` | Payment gateway API base URL |
| `VITE_CHECKOUT_URL` | `http://localhost:3100` | Nettbank frontend URL (for checkout redirect) |

### Dev proxy

In development, API calls to `/api/v1/*` are proxied to the payment gateway (localhost:8089) via Vite's dev server proxy. This avoids CORS issues when calling the gateway directly from the browser.

If you want to call the gateway directly (e.g. in production), set `VITE_GATEWAY_URL` to the full gateway URL and ensure the gateway has CORS configured.

## Docker Compose snippet

To add this service to the project's docker-compose.yml:

```yaml
  webshop:
    build:
      context: ./frontend/webshop
      dockerfile: Dockerfile
    ports:
      - "3200:3200"
    environment:
      - VITE_GATEWAY_URL=http://payment-gateway:8089
      - VITE_CHECKOUT_URL=http://localhost:3100
```

## Payment flow

1. User clicks "Buy Now" on a product
2. App creates a payment order via `POST /api/v1/payments`
3. User is redirected to the nettbank checkout page (`/{tenant}/checkout/{orderId}`)
4. After authorizing, user is redirected back to the webshop with `?orderId=...&status=authorized`
5. Webshop shows a success banner

## Subscription flow

1. User clicks "Subscribe" on VIP Membership
2. App creates a subscription via `POST /api/v1/subscriptions`
3. Subscription confirmation is shown immediately (no redirect needed)
