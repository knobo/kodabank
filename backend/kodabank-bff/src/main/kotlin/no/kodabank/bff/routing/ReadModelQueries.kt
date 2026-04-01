package no.kodabank.bff.routing

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Instant

data class AccountView(
    val accountId: String,
    val tenantId: String,
    val partyId: String,
    val iban: String,
    val accountName: String?,
    val accountType: String,
    val productId: String?,
    val balance: BigDecimal,
    val currency: String,
    val status: String,
    val createdAt: Instant
)

data class TransactionView(
    val transactionId: String,
    val tenantId: String,
    val accountId: String,
    val eventType: String,
    val amount: BigDecimal,
    val balanceAfter: BigDecimal?,
    val currency: String,
    val counterpartyName: String?,
    val counterpartyIban: String?,
    val reference: String?,
    val remittanceInfo: String?,
    val createdAt: Instant
)

data class CardView(
    val cardId: String,
    val tenantId: String,
    val partyId: String,
    val accountId: String,
    val cardNumberMasked: String,
    val expiryDate: String?,
    val cardType: String,
    val status: String,
    val createdAt: Instant
)

data class PaymentView(
    val paymentId: String,
    val tenantId: String,
    val debtorAccountId: String,
    val debtorIban: String,
    val creditorIban: String,
    val creditorName: String?,
    val amount: BigDecimal,
    val currency: String,
    val paymentType: String,
    val status: String,
    val reference: String?,
    val createdAt: Instant,
    val completedAt: Instant?
)

data class TenantBrandingView(
    val tenantId: String,
    val bankName: String,
    val bankCode: String,
    val ibanPrefix: String,
    val country: String,
    val currency: String,
    val primaryColor: String?,
    val secondaryColor: String?,
    val logoUrl: String?,
    val tagline: String?,
    val status: String
)

data class TenantAdminView(
    val tenantId: String,
    val bankName: String,
    val bankCode: String,
    val currency: String,
    val primaryColor: String?,
    val tagline: String?,
    val logoUrl: String?,
    val status: String,
    val ownerUserId: String?,
    val urlAlias: String?,
    val accessPolicyType: String?,
    val transferPolicyType: String?
)

data class DashboardView(
    val accounts: List<AccountView>,
    val recentTransactions: List<TransactionView>,
    val cards: List<CardView>
)

@Component
class ReadModelQueries(
    private val jdbcTemplate: JdbcTemplate
) {

    fun getAccountsForParty(tenantId: String, partyId: String): List<AccountView> {
        return jdbcTemplate.query(
            """SELECT account_id, tenant_id, party_id, iban, account_name, account_type,
                      product_id, balance, currency, status, created_at
               FROM rm_accounts
               WHERE tenant_id = ? AND party_id = ?
               ORDER BY created_at""",
            { rs, _ ->
                AccountView(
                    accountId = rs.getString("account_id"),
                    tenantId = rs.getString("tenant_id"),
                    partyId = rs.getString("party_id"),
                    iban = rs.getString("iban"),
                    accountName = rs.getString("account_name"),
                    accountType = rs.getString("account_type"),
                    productId = rs.getString("product_id"),
                    balance = rs.getBigDecimal("balance"),
                    currency = rs.getString("currency"),
                    status = rs.getString("status"),
                    createdAt = rs.getTimestamp("created_at").toInstant()
                )
            },
            tenantId, partyId
        )
    }

    fun getTransactions(accountId: String, limit: Int = 50): List<TransactionView> {
        return jdbcTemplate.query(
            """SELECT transaction_id, tenant_id, account_id, event_type, amount,
                      balance_after, currency, counterparty_name, counterparty_iban,
                      reference, remittance_info, created_at
               FROM rm_transactions
               WHERE account_id = ?
               ORDER BY created_at DESC
               LIMIT ?""",
            { rs, _ ->
                TransactionView(
                    transactionId = rs.getString("transaction_id"),
                    tenantId = rs.getString("tenant_id"),
                    accountId = rs.getString("account_id"),
                    eventType = rs.getString("event_type"),
                    amount = rs.getBigDecimal("amount"),
                    balanceAfter = rs.getBigDecimal("balance_after"),
                    currency = rs.getString("currency"),
                    counterpartyName = rs.getString("counterparty_name"),
                    counterpartyIban = rs.getString("counterparty_iban"),
                    reference = rs.getString("reference"),
                    remittanceInfo = rs.getString("remittance_info"),
                    createdAt = rs.getTimestamp("created_at").toInstant()
                )
            },
            accountId, limit
        )
    }

    fun getCardsForParty(tenantId: String, partyId: String): List<CardView> {
        return jdbcTemplate.query(
            """SELECT card_id, tenant_id, party_id, account_id, card_number_masked,
                      expiry_date, card_type, status, created_at
               FROM rm_cards
               WHERE tenant_id = ? AND party_id = ?
               ORDER BY created_at""",
            { rs, _ ->
                CardView(
                    cardId = rs.getString("card_id"),
                    tenantId = rs.getString("tenant_id"),
                    partyId = rs.getString("party_id"),
                    accountId = rs.getString("account_id"),
                    cardNumberMasked = rs.getString("card_number_masked"),
                    expiryDate = rs.getString("expiry_date"),
                    cardType = rs.getString("card_type"),
                    status = rs.getString("status"),
                    createdAt = rs.getTimestamp("created_at").toInstant()
                )
            },
            tenantId, partyId
        )
    }

    fun getPaymentsForParty(tenantId: String, partyId: String): List<PaymentView> {
        return jdbcTemplate.query(
            """SELECT p.payment_id, p.tenant_id, p.debtor_account_id, p.debtor_iban,
                      p.creditor_iban, p.creditor_name, p.amount, p.currency,
                      p.payment_type, p.status, p.reference, p.created_at, p.completed_at
               FROM rm_payments p
               JOIN rm_accounts a ON a.account_id = p.debtor_account_id
               WHERE p.tenant_id = ? AND a.party_id = ?
               ORDER BY p.created_at DESC""",
            { rs, _ ->
                PaymentView(
                    paymentId = rs.getString("payment_id"),
                    tenantId = rs.getString("tenant_id"),
                    debtorAccountId = rs.getString("debtor_account_id"),
                    debtorIban = rs.getString("debtor_iban"),
                    creditorIban = rs.getString("creditor_iban"),
                    creditorName = rs.getString("creditor_name"),
                    amount = rs.getBigDecimal("amount"),
                    currency = rs.getString("currency"),
                    paymentType = rs.getString("payment_type"),
                    status = rs.getString("status"),
                    reference = rs.getString("reference"),
                    createdAt = rs.getTimestamp("created_at").toInstant(),
                    completedAt = rs.getTimestamp("completed_at")?.toInstant()
                )
            },
            tenantId, partyId
        )
    }

    fun getDashboard(tenantId: String, partyId: String): DashboardView {
        val accounts = getAccountsForParty(tenantId, partyId)
        val recentTransactions = if (accounts.isNotEmpty()) {
            val accountIds = accounts.map { it.accountId }
            val placeholders = accountIds.joinToString(",") { "?" }
            jdbcTemplate.query(
                """SELECT transaction_id, tenant_id, account_id, event_type, amount,
                          balance_after, currency, counterparty_name, counterparty_iban,
                          reference, remittance_info, created_at
                   FROM rm_transactions
                   WHERE account_id IN ($placeholders)
                   ORDER BY created_at DESC
                   LIMIT 10""",
                { rs, _ ->
                    TransactionView(
                        transactionId = rs.getString("transaction_id"),
                        tenantId = rs.getString("tenant_id"),
                        accountId = rs.getString("account_id"),
                        eventType = rs.getString("event_type"),
                        amount = rs.getBigDecimal("amount"),
                        balanceAfter = rs.getBigDecimal("balance_after"),
                        currency = rs.getString("currency"),
                        counterpartyName = rs.getString("counterparty_name"),
                        counterpartyIban = rs.getString("counterparty_iban"),
                        reference = rs.getString("reference"),
                        remittanceInfo = rs.getString("remittance_info"),
                        createdAt = rs.getTimestamp("created_at").toInstant()
                    )
                },
                *accountIds.toTypedArray()
            )
        } else {
            emptyList()
        }
        val cards = getCardsForParty(tenantId, partyId)
        return DashboardView(
            accounts = accounts,
            recentTransactions = recentTransactions,
            cards = cards
        )
    }

    fun getTenantBranding(tenantId: String): TenantBrandingView? {
        val results = jdbcTemplate.query(
            """SELECT tenant_id, bank_name, bank_code, iban_prefix, country, currency,
                      primary_color, secondary_color, logo_url, tagline, status
               FROM rm_tenants
               WHERE tenant_id = ?""",
            { rs, _ ->
                TenantBrandingView(
                    tenantId = rs.getString("tenant_id"),
                    bankName = rs.getString("bank_name"),
                    bankCode = rs.getString("bank_code"),
                    ibanPrefix = rs.getString("iban_prefix"),
                    country = rs.getString("country"),
                    currency = rs.getString("currency"),
                    primaryColor = rs.getString("primary_color"),
                    secondaryColor = rs.getString("secondary_color"),
                    logoUrl = rs.getString("logo_url"),
                    tagline = rs.getString("tagline"),
                    status = rs.getString("status")
                )
            },
            tenantId
        )
        return results.firstOrNull()
    }

    fun getTenantAdminSettings(tenantId: String): TenantAdminView? {
        return jdbcTemplate.query(
            """SELECT tenant_id, bank_name, bank_code, currency, primary_color, tagline, logo_url,
                      status, owner_user_id, url_alias, access_policy_type, transfer_policy_type
               FROM rm_tenants WHERE tenant_id = ?""",
            { rs, _ ->
                TenantAdminView(
                    tenantId = rs.getString("tenant_id"),
                    bankName = rs.getString("bank_name"),
                    bankCode = rs.getString("bank_code"),
                    currency = rs.getString("currency"),
                    primaryColor = rs.getString("primary_color"),
                    tagline = rs.getString("tagline"),
                    logoUrl = rs.getString("logo_url"),
                    status = rs.getString("status"),
                    ownerUserId = rs.getString("owner_user_id"),
                    urlAlias = rs.getString("url_alias"),
                    accessPolicyType = rs.getString("access_policy_type"),
                    transferPolicyType = rs.getString("transfer_policy_type")
                )
            },
            tenantId
        ).firstOrNull()
    }

    fun getTenantByAlias(alias: String): TenantBrandingView? {
        return jdbcTemplate.query(
            """SELECT tenant_id, bank_name, bank_code, iban_prefix, country, currency,
                      primary_color, secondary_color, logo_url, tagline, status
               FROM rm_tenants WHERE url_alias = ?""",
            { rs, _ ->
                TenantBrandingView(
                    tenantId = rs.getString("tenant_id"),
                    bankName = rs.getString("bank_name"),
                    bankCode = rs.getString("bank_code"),
                    ibanPrefix = rs.getString("iban_prefix"),
                    country = rs.getString("country"),
                    currency = rs.getString("currency"),
                    primaryColor = rs.getString("primary_color"),
                    secondaryColor = rs.getString("secondary_color"),
                    logoUrl = rs.getString("logo_url"),
                    tagline = rs.getString("tagline"),
                    status = rs.getString("status")
                )
            },
            alias
        ).firstOrNull()
    }

    fun listTenants(): List<TenantBrandingView> {
        return jdbcTemplate.query(
            """SELECT tenant_id, bank_name, bank_code, iban_prefix, country, currency,
                      primary_color, secondary_color, logo_url, tagline, status
               FROM rm_tenants
               WHERE status = 'ACTIVE'
               ORDER BY bank_name"""
        ) { rs, _ ->
            TenantBrandingView(
                tenantId = rs.getString("tenant_id"),
                bankName = rs.getString("bank_name"),
                bankCode = rs.getString("bank_code"),
                ibanPrefix = rs.getString("iban_prefix"),
                country = rs.getString("country"),
                currency = rs.getString("currency"),
                primaryColor = rs.getString("primary_color"),
                secondaryColor = rs.getString("secondary_color"),
                logoUrl = rs.getString("logo_url"),
                tagline = rs.getString("tagline"),
                status = rs.getString("status")
            )
        }
    }
}
