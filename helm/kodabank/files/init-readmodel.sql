-- KodaBank Read Model Schema
-- These tables are projections built from kodastore events.
-- They can be fully rebuilt by replaying all events from offset 0.

CREATE TABLE rm_tenants (
    tenant_id                   VARCHAR(50) PRIMARY KEY,
    bank_name                   VARCHAR(255) NOT NULL,
    bank_code                   VARCHAR(4) NOT NULL UNIQUE,
    iban_prefix                 VARCHAR(10) NOT NULL,
    country                     VARCHAR(2) NOT NULL DEFAULT 'NO',
    currency                    VARCHAR(3) NOT NULL DEFAULT 'NOK',
    primary_color               VARCHAR(7),
    secondary_color             VARCHAR(7),
    logo_url                    VARCHAR(500),
    tagline                     VARCHAR(255),
    owner_user_id               VARCHAR(100),
    access_policy_type          VARCHAR(20),
    access_policy_webhook_url   VARCHAR(500),
    transfer_policy_type        VARCHAR(20),
    transfer_policy_whitelist   JSONB DEFAULT '[]',
    transfer_policy_domain_code VARCHAR(100),
    status                      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    url_alias                   VARCHAR(100) UNIQUE,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE rm_customers (
    party_id        VARCHAR(100) PRIMARY KEY,
    tenant_id       VARCHAR(50) NOT NULL REFERENCES rm_tenants(tenant_id),
    first_name      VARCHAR(100),
    last_name       VARCHAR(100),
    national_id     VARCHAR(100),
    email           VARCHAR(255),
    phone           VARCHAR(20),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    kyc_level       VARCHAR(20),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE rm_accounts (
    account_id      VARCHAR(100) PRIMARY KEY,
    tenant_id       VARCHAR(50) NOT NULL REFERENCES rm_tenants(tenant_id),
    party_id        VARCHAR(100) NOT NULL REFERENCES rm_customers(party_id),
    iban            VARCHAR(34) NOT NULL UNIQUE,
    account_name    VARCHAR(255),
    account_type    VARCHAR(20) NOT NULL,  -- 'CURRENT' or 'SAVINGS'
    product_id      VARCHAR(100),
    balance         NUMERIC(18,2) NOT NULL DEFAULT 0,
    currency        VARCHAR(3) NOT NULL DEFAULT 'NOK',
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE rm_transactions (
    transaction_id      VARCHAR(100) PRIMARY KEY,
    tenant_id           VARCHAR(50) NOT NULL,
    account_id          VARCHAR(100) NOT NULL REFERENCES rm_accounts(account_id),
    event_type          VARCHAR(50) NOT NULL,
    amount              NUMERIC(18,2) NOT NULL,
    balance_after       NUMERIC(18,2),
    currency            VARCHAR(3) NOT NULL DEFAULT 'NOK',
    counterparty_name   VARCHAR(255),
    counterparty_iban   VARCHAR(34),
    reference           VARCHAR(255),
    remittance_info     VARCHAR(500),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE rm_cards (
    card_id             VARCHAR(100) PRIMARY KEY,
    tenant_id           VARCHAR(50) NOT NULL REFERENCES rm_tenants(tenant_id),
    party_id            VARCHAR(100) NOT NULL REFERENCES rm_customers(party_id),
    account_id          VARCHAR(100) NOT NULL REFERENCES rm_accounts(account_id),
    card_number_masked  VARCHAR(20) NOT NULL,
    expiry_date         VARCHAR(7),
    card_type           VARCHAR(20) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'ISSUED',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE rm_payments (
    payment_id          VARCHAR(100) PRIMARY KEY,
    tenant_id           VARCHAR(50) NOT NULL,
    debtor_account_id   VARCHAR(100) NOT NULL REFERENCES rm_accounts(account_id),
    debtor_iban         VARCHAR(34) NOT NULL,
    creditor_iban       VARCHAR(34) NOT NULL,
    creditor_name       VARCHAR(255),
    amount              NUMERIC(18,2) NOT NULL,
    currency            VARCHAR(3) NOT NULL DEFAULT 'NOK',
    payment_type        VARCHAR(20) NOT NULL,  -- 'INTERNAL' or 'INTERBANK'
    status              VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    reference           VARCHAR(255),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at        TIMESTAMPTZ
);

CREATE TABLE rm_products (
    product_id      VARCHAR(100) PRIMARY KEY,
    tenant_id       VARCHAR(50) NOT NULL REFERENCES rm_tenants(tenant_id),
    name            VARCHAR(255) NOT NULL,
    product_type    VARCHAR(20) NOT NULL,
    features        JSONB DEFAULT '{}',
    fees            JSONB DEFAULT '{}',
    interest_rate   NUMERIC(6,4),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
);

CREATE TABLE rm_transfer_policies (
    tenant_id          VARCHAR(50) PRIMARY KEY,
    policy_type        VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    whitelist          JSONB DEFAULT '[]',
    domain_code        VARCHAR(100)
);

CREATE TABLE rm_memberships (
    tenant_id       VARCHAR(50) NOT NULL,
    user_id         VARCHAR(100) NOT NULL,
    display_name    VARCHAR(200),
    email           VARCHAR(200),
    party_id        VARCHAR(100),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requested_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at     TIMESTAMPTZ,
    PRIMARY KEY (tenant_id, user_id)
);

-- Projection checkpoint tracking
CREATE TABLE projection_checkpoints (
    projection_name VARCHAR(100) PRIMARY KEY,
    last_offset     BIGINT NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- BFF session store
CREATE TABLE bff_sessions (
    session_id      VARCHAR(100) PRIMARY KEY,
    access_token    TEXT NOT NULL,
    refresh_token   TEXT,
    party_id        VARCHAR(100),
    tenant_id       VARCHAR(50),
    username        VARCHAR(100),
    first_name      VARCHAR(100),
    last_name       VARCHAR(100),
    sub             VARCHAR(100),
    created_at      BIGINT NOT NULL,
    expires_at      BIGINT NOT NULL
);

-- Indexes for common queries
CREATE INDEX idx_rm_customers_tenant ON rm_customers(tenant_id);
CREATE INDEX idx_rm_accounts_tenant ON rm_accounts(tenant_id);
CREATE INDEX idx_rm_accounts_party ON rm_accounts(party_id);
CREATE INDEX idx_rm_transactions_account ON rm_transactions(account_id);
CREATE INDEX idx_rm_transactions_tenant_date ON rm_transactions(tenant_id, created_at DESC);
CREATE INDEX idx_rm_cards_party ON rm_cards(party_id);
CREATE INDEX idx_rm_payments_tenant ON rm_payments(tenant_id);
CREATE INDEX idx_rm_payments_status ON rm_payments(status);
CREATE INDEX idx_rm_products_tenant ON rm_products(tenant_id);
CREATE INDEX idx_rm_memberships_tenant ON rm_memberships(tenant_id);
CREATE INDEX idx_rm_memberships_status ON rm_memberships(tenant_id, status);
CREATE INDEX idx_bff_sessions_expires ON bff_sessions(expires_at);
