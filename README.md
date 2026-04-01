# KodaBank

An open-source Banking-as-a-Service platform built on event sourcing. KodaBank lets you spin up fully isolated, multi-tenant virtual banks — each with their own customers, accounts, transfers, and access policies.

Built with Kotlin + Spring Boot, React (TanStack Start SSR), PostgreSQL, Keycloak, and [KodaStore](https://github.com/knobo/kodastore) as the event store.

## Architecture

```
Browser
  │
  ▼
nginx Ingress
  ├─ /api/*  ──────────► kodabank-bff (Spring Boot, BFF pattern)
  │                            │
  │                            ├─► kodabank-core    (domain logic + projections)
  │                            ├─► kodabank-clearing (interbank clearing)
  │                            ├─► kodabank-admin   (tenant/keycloak admin)
  │                            └─► kodabank-payment-gateway
  │
  └─ /*  ──────────────► nettbank (React SSR frontend)

keycloak.* ──────────────► Keycloak (IAM, OIDC)

All services write/read events from:
  KodaStore (append-only event log, PostgreSQL-backed)

Projections build read models in a separate PostgreSQL instance.
```

## Quick Start (Docker Compose)

**Prerequisites:** Docker, Docker Compose

```bash
git clone https://github.com/knobo/kodabank
cd kodabank
docker compose up
```

Services start on:
| Service | URL |
|---|---|
| Nettbank (frontend) | http://localhost:3100 |
| BFF | http://localhost:8085 |
| Keycloak | http://localhost:8180 |
| KodaStore | http://localhost:8080 |

Default Keycloak admin credentials: `admin` / `admin`

> First startup takes ~2 minutes as Gradle builds all services from source.
> For faster restarts, pre-build the images: `docker compose build`

## Quick Start (Kubernetes with Helm)

**Prerequisites:** Kubernetes cluster, Helm 3, nginx ingress controller

### Local cluster (minikube / k3s / kind)

```bash
# 1. Add local DNS entries
echo "127.0.0.1 kodabank.local" | sudo tee -a /etc/hosts
echo "127.0.0.1 keycloak.kodabank.local" | sudo tee -a /etc/hosts

# 2. For minikube, start a tunnel (separate terminal)
minikube tunnel

# 3. Install the Helm chart
helm install kodabank ./helm/kodabank \
  -f helm/kodabank/values-local.yaml

# 4. Wait for all pods to be ready
kubectl wait --for=condition=ready pod \
  -l app.kubernetes.io/instance=kodabank \
  --timeout=300s

# 5. Open http://kodabank.local
```

After install, follow the Keycloak redirect URI setup printed in the Helm notes:
```bash
helm status kodabank
```

### Production (with TLS and cert-manager)

```bash
helm install kodabank ./helm/kodabank \
  -f helm/kodabank/values-production.yaml \
  --set postgres.eventstore.password=$(openssl rand -hex 16) \
  --set postgres.readmodel.password=$(openssl rand -hex 16) \
  --set postgres.keycloak.password=$(openssl rand -hex 16) \
  --set keycloak.adminPassword=$(openssl rand -hex 16) \
  --set keycloak.clientSecret=$(openssl rand -hex 16)
```

See [`helm/kodabank/values-production.yaml`](helm/kodabank/values-production.yaml) for all options.

### Helm chart configuration reference

| Value | Default | Description |
|---|---|---|
| `host` | `kodabank.example.com` | Main hostname (frontend + BFF) |
| `keycloakHost` | `keycloak.example.com` | Keycloak hostname |
| `imageRegistry` | `ghcr.io/knobo` | Container image registry |
| `imageTag` | `latest` | Image tag for all services |
| `ingress.className` | `nginx` | Ingress class |
| `ingress.tls.enabled` | `false` | Enable TLS |
| `postgres.*.password` | changeme | PostgreSQL passwords |
| `keycloak.adminPassword` | changeme | Keycloak admin password |
| `storageClass` | `""` | PVC storage class (empty = cluster default) |

## CI/CD

Docker images are built and pushed to `ghcr.io/knobo/` on every push to `main`.

Images published:
- `ghcr.io/knobo/kodabank-core`
- `ghcr.io/knobo/kodabank-clearing`
- `ghcr.io/knobo/kodabank-bff`
- `ghcr.io/knobo/kodabank-admin`
- `ghcr.io/knobo/kodabank-payment-gateway`
- `ghcr.io/knobo/nettbank`
- `ghcr.io/knobo/kodastore`

**Frontend build variable:** Set `VITE_BFF_URL` as a GitHub Actions variable
(`vars.VITE_BFF_URL`) to your production host, e.g. `https://kodabank.knobo.no`.
This is baked into the frontend image at build time.

## Development

### Backend

```bash
cd backend
./gradlew :kodabank-core:bootRun
./gradlew :kodabank-bff:bootRun
# etc.
```

Requires a running PostgreSQL and KodaStore. Use `docker compose up postgres-kodastore postgres-readmodel keycloak kodastore` to start only the infrastructure.

### Frontend

```bash
cd frontend/nettbank
npm install
npm run dev
```

Connects to BFF at `http://localhost:8085` by default.

## Project Structure

```
kodabank/
├── backend/                    # Gradle multi-project (Kotlin/Spring Boot)
│   ├── kodabank-bff/           # Backend-for-Frontend (session, OIDC, routing)
│   ├── kodabank-core/          # Domain logic, projections, read model
│   ├── kodabank-clearing/      # Interbank clearing & settlement
│   ├── kodabank-admin/         # Tenant admin, Keycloak integration
│   ├── kodabank-payment-gateway/ # Payment initiation
│   └── shared/                 # Shared libraries
├── frontend/
│   └── nettbank/               # React SSR app (TanStack Start)
├── infrastructure/
│   ├── postgres/               # Read model init SQL
│   └── keycloak/               # Realm export
├── helm/
│   └── kodabank/               # Helm chart for Kubernetes deployment
└── docker-compose.yml          # Full local stack
```

## License

MIT
