# KodaBank TODO

## KodaCoins / Crypto Integration

Set up a local Ethereum-compatible blockchain with a "KodaCoin" ERC-20 token.

**Options:**
- **Hardhat** (`ghcr.io/foundry-rs/foundry` or `hardhat` npm) – local EVM node + Solidity toolchain
- **Ganache** (`trufflesuite/ganache`) – simpler GUI/CLI local Ethereum node
- **OpenZeppelin** contracts for the ERC-20 token standard

**What this would enable:**
- Each user has a KodaCoin wallet address
- Query token balances from the chain and show them in the dashboard
- Transfer KodaCoins between users via the KodaBank UI
- Bridge: NOK ↔ KodaCoin exchange within a bank

**Steps:**
1. Add a Hardhat or Ganache container to `docker-compose.yml`
2. Write a simple ERC-20 `KodaCoin.sol` contract (using OpenZeppelin)
3. Deploy it on startup via a deploy script
4. Expose a wallet endpoint from the BFF: `GET /{tenant}/wallet` → balance + address
5. Add a "KodaCoins" tab in the dashboard

---

## Other TODOs

- [ ] Playwright E2E test suite (full browser flow: login → bank creation → transfer → logout)
- [ ] Card creation in admin panel (currently cards exist in schema but no create flow)
- [ ] Branding editor in admin panel (live preview of colors/logo)
- [ ] Email notifications for membership requests (webhooks or SMTP)
- [ ] Mobile-responsive polish on the dashboard pages
