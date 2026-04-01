const BFF_URL = import.meta.env.VITE_BFF_URL || 'http://localhost:8085';

async function apiFetch<T>(path: string, options?: RequestInit): Promise<T> {
  const url = `${BFF_URL}${path}`;
  const res = await fetch(url, {
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
    ...options,
  });

  if (!res.ok) {
    const body = await res.text().catch(() => '');
    throw new ApiError(res.status, body || res.statusText);
  }

  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

export class ApiError extends Error {
  constructor(
    public status: number,
    public body: string,
  ) {
    super(`API error ${status}: ${body}`);
    this.name = 'ApiError';
  }
}

// --- Types ---

export interface Tenant {
  id: string;
  name: string;
  tagline?: string;
  primaryColor: string;
  logoUrl?: string;
}

export interface Branding {
  bankName: string;
  primaryColor: string;
  secondaryColor?: string;
  logoUrl?: string;
  tagline?: string;
}

export interface Account {
  id: string;
  name: string;
  iban: string;
  type: string;
  balance: number;
  currency: string;
  availableBalance?: number;
}

export interface Transaction {
  id: string;
  date: string;
  description: string;
  amount: number;
  currency: string;
  balanceAfter?: number;
  category?: string;
  counterparty?: string;
}

export interface DashboardData {
  accounts: Account[];
  recentTransactions: Transaction[];
  totalBalance: number;
  currency: string;
}

export interface Payment {
  id: string;
  creditorName: string;
  creditorIban: string;
  amount: number;
  currency: string;
  reference?: string;
  kid?: string;
  status: 'REQUESTED' | 'COMPLETED' | 'FAILED' | 'PENDING';
  createdAt: string;
}

export interface Card {
  id: string;
  maskedNumber: string;
  cardholderName: string;
  expiryDate: string;
  type: string;
  status: 'ACTIVE' | 'BLOCKED' | 'EXPIRED';
}

export interface UserProfile {
  name: string;
  nationalId: string;
  email?: string;
  phone?: string;
}

// --- API functions ---

export interface AuthUser {
  authenticated: boolean;
  username?: string;
  firstName?: string;
  lastName?: string;
  tenantId?: string;
}

export async function fetchMe(): Promise<AuthUser> {
  try {
    return await apiFetch<AuthUser>('/api/v1/auth/me');
  } catch {
    return { authenticated: false };
  }
}

export interface MyBank {
  id: string;
  name: string;
  primaryColor: string;
}

export async function fetchMyBanks(): Promise<MyBank[]> {
  try {
    return await apiFetch<MyBank[]>('/api/v1/auth/my-banks');
  } catch {
    return [];
  }
}

export async function fetchTenants(): Promise<Tenant[]> {
  return apiFetch<Tenant[]>('/api/v1/tenants');
}

export async function fetchBranding(tenant: string): Promise<Branding> {
  return apiFetch<Branding>(`/api/v1/tenants/${tenant}/branding`);
}

export async function login(tenant: string, nationalId: string): Promise<{ success: boolean; message?: string }> {
  return apiFetch(`/api/v1/${tenant}/auth/bankid`, {
    method: 'POST',
    body: JSON.stringify({ nationalId }),
  });
}

export async function fetchDashboard(tenant: string): Promise<DashboardData> {
  return apiFetch<DashboardData>(`/api/v1/${tenant}/dashboard`);
}

export async function fetchAccounts(tenant: string): Promise<Account[]> {
  return apiFetch<Account[]>(`/api/v1/${tenant}/accounts`);
}

export async function fetchTransactions(tenant: string, accountId: string): Promise<Transaction[]> {
  return apiFetch<Transaction[]>(`/api/v1/${tenant}/accounts/${accountId}/transactions`);
}

export async function fetchAccount(tenant: string, accountId: string): Promise<Account> {
  return apiFetch<Account>(`/api/v1/${tenant}/accounts/${accountId}`);
}

export async function initiatePayment(tenant: string, data: {
  creditorIban: string;
  creditorName: string;
  amount: number;
  reference?: string;
  kid?: string;
  debitAccountId: string;
}): Promise<Payment> {
  return apiFetch<Payment>(`/api/v1/${tenant}/payments`, {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export async function fetchPayments(tenant: string): Promise<Payment[]> {
  return apiFetch<Payment[]>(`/api/v1/${tenant}/payments`);
}

export async function fetchCards(tenant: string): Promise<Card[]> {
  return apiFetch<Card[]>(`/api/v1/${tenant}/cards`);
}

export async function toggleCardBlock(tenant: string, cardId: string, block: boolean): Promise<Card> {
  return apiFetch<Card>(`/api/v1/${tenant}/cards/${cardId}/${block ? 'block' : 'unblock'}`, {
    method: 'POST',
  });
}

export async function fetchProfile(tenant: string): Promise<UserProfile> {
  return apiFetch<UserProfile>(`/api/v1/${tenant}/profile`);
}

export async function logout(tenant: string): Promise<void> {
  return apiFetch<void>(`/api/v1/${tenant}/auth/logout`, {
    method: 'POST',
  });
}

export async function initiateTransfer(tenant: string, data: {
  fromAccountId: string;
  toAccountId: string;
  amount: number;
  reference?: string;
}): Promise<Payment> {
  return apiFetch<Payment>(`/api/v1/${tenant}/transfers/internal`, {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

// --- Public / Platform API ---

export interface PublicBank {
  id: string;
  name: string;
  bankCode: string;
  country: string;
  currency: string;
  primaryColor?: string;
  logoUrl?: string;
  tagline?: string;
}

export interface RegisterBankData {
  bankName: string;
  currency: string;
  branding: {
    primaryColor: string;
    tagline?: string;
  };
  accessPolicy?: { type: 'AUTO_APPROVE' | 'MANUAL_APPROVAL' | 'WEBHOOK'; webhookUrl?: string };
  transferPolicy?: { type: 'OPEN' | 'CLOSED' | 'WHITELIST' | 'DOMAIN_CODE'; whitelist?: string[]; domainCode?: string };
}

export interface RegisterBankResult {
  id: string;
  bankName: string;
  url: string;
}

export interface MembershipRequest {
  displayName: string;
  email: string;
  message?: string;
}

export interface Membership {
  userId: string;
  displayName: string;
  email: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  message?: string;
  createdAt: string;
}

export interface CheckoutOrderItem {
  name: string;
  quantity: number;
  unitPrice: number;
}

export interface CheckoutOrder {
  orderId: string;
  merchantId: string;
  merchantName: string;
  tenantId: string;
  amount: number;
  currency: string;
  description: string;
  callbackUrl?: string;
  expiresAt: string;
  items: CheckoutOrderItem[];
  status: 'CREATED' | 'AUTHORIZED' | 'CAPTURED' | 'CANCELLED' | 'REFUNDED' | 'EXPIRED';
  userId?: string;
  payerAccountId?: string;
  capturedAmount?: number;
  refundedAmount?: number;
  version: number;
}

export async function fetchPublicBanks(): Promise<PublicBank[]> {
  return apiFetch<PublicBank[]>('/api/v1/tenants');
}

export async function registerBank(data: RegisterBankData): Promise<RegisterBankResult> {
  return apiFetch<RegisterBankResult>('/api/v1/tenants/register', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export async function requestMembership(tenant: string, data: MembershipRequest): Promise<Membership> {
  return apiFetch<Membership>(`/api/v1/${tenant}/memberships`, {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export async function getMembership(tenant: string): Promise<Membership> {
  return apiFetch<Membership>(`/api/v1/${tenant}/membership`);
}

export async function listMemberships(tenant: string): Promise<Membership[]> {
  return apiFetch<Membership[]>(`/api/v1/${tenant}/admin/memberships`);
}

export async function approveMembership(tenant: string, userId: string): Promise<Membership> {
  return apiFetch<Membership>(`/api/v1/${tenant}/admin/memberships/${userId}/approve`, {
    method: 'POST',
  });
}

export async function rejectMembership(tenant: string, userId: string): Promise<Membership> {
  return apiFetch<Membership>(`/api/v1/${tenant}/admin/memberships/${userId}/reject`, {
    method: 'POST',
  });
}

export async function getCheckoutOrder(tenant: string, orderId: string): Promise<CheckoutOrder> {
  return apiFetch<CheckoutOrder>(`/api/v1/${tenant}/checkout/${orderId}`);
}

export async function authorizePayment(tenant: string, orderId: string, accountId: string): Promise<CheckoutOrder> {
  return apiFetch<CheckoutOrder>(`/api/v1/${tenant}/checkout/${orderId}/authorize`, {
    method: 'POST',
    body: JSON.stringify({ accountId }),
  });
}

export interface BankAdminSettings {
  tenantId: string;
  bankName: string;
  bankCode: string;
  currency: string;
  primaryColor: string;
  tagline: string;
  logoUrl: string;
  status: string;
  urlAlias: string;
  accessPolicyType: string;
  transferPolicyType: string;
}

export async function fetchBankAdminSettings(tenant: string): Promise<BankAdminSettings> {
  return apiFetch<BankAdminSettings>(`/api/v1/tenants/${tenant}/admin/settings`);
}

export async function updateBankAdminSettings(tenant: string, data: { urlAlias?: string }): Promise<{ updated: boolean; urlAlias: string }> {
  return apiFetch(`/api/v1/tenants/${tenant}/admin/settings`, {
    method: 'PATCH',
    body: JSON.stringify(data),
  });
}

export function loginRedirect(tenant: string): void {
  window.location.href = `${BFF_URL}/api/v1/${tenant}/auth/login`;
}
