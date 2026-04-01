package no.kodabank.bff.routing

import jakarta.servlet.http.HttpServletRequest
import no.kodabank.bff.auth.SessionData
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.Instant

@RestController
@RequestMapping("/api/v1/{tenant}")
class AccountRoutes(
    private val readModelQueries: ReadModelQueries,
    private val coreServiceClient: CoreServiceClient
) {

    @GetMapping("/dashboard")
    fun getDashboard(
        @PathVariable tenant: String,
        request: HttpServletRequest
    ): ResponseEntity<*> {
        val session = validateSession(request, tenant) ?: return unauthorizedResponse()

        val dashboard = readModelQueries.getDashboard(session.tenantId, session.partyId)
        val accountResponses = dashboard.accounts.map { it.toResponse() }
        val totalBalance = accountResponses.sumOf { it.balance }
        return ResponseEntity.ok(
            DashboardResponse(
                accounts = accountResponses,
                recentTransactions = dashboard.recentTransactions.map { it.toResponse() },
                cards = dashboard.cards.map { it.toResponse() },
                totalBalance = totalBalance,
                currency = accountResponses.firstOrNull()?.currency ?: "NOK"
            )
        )
    }

    @GetMapping("/accounts")
    fun listAccounts(
        @PathVariable tenant: String,
        request: HttpServletRequest
    ): ResponseEntity<*> {
        val session = validateSession(request, tenant) ?: return unauthorizedResponse()

        val accounts = readModelQueries.getAccountsForParty(session.tenantId, session.partyId)
        return ResponseEntity.ok(accounts.map { it.toResponse() })
    }

    @GetMapping("/accounts/{id}")
    fun getAccount(
        @PathVariable tenant: String,
        @PathVariable id: String,
        request: HttpServletRequest
    ): ResponseEntity<*> {
        val session = validateSession(request, tenant) ?: return unauthorizedResponse()

        // Verify the account belongs to this party by checking the read model
        val accounts = readModelQueries.getAccountsForParty(session.tenantId, session.partyId)
        val account = accounts.find { it.accountId == id }
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(mapOf("error" to "not_found", "message" to "Account not found"))

        return ResponseEntity.ok(account.toResponse())
    }

    @GetMapping("/accounts/{id}/transactions")
    fun getTransactions(
        @PathVariable tenant: String,
        @PathVariable id: String,
        @RequestParam(defaultValue = "50") limit: Int,
        request: HttpServletRequest
    ): ResponseEntity<*> {
        val session = validateSession(request, tenant) ?: return unauthorizedResponse()

        // Verify the account belongs to this party
        val accounts = readModelQueries.getAccountsForParty(session.tenantId, session.partyId)
        if (accounts.none { it.accountId == id }) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(mapOf("error" to "not_found", "message" to "Account not found"))
        }

        val transactions = readModelQueries.getTransactions(id, limit.coerceIn(1, 200))
        return ResponseEntity.ok(transactions.map { it.toResponse() })
    }

    private fun validateSession(request: HttpServletRequest, tenant: String): SessionData? {
        val session = request.getAttribute("kodabank.session") as? SessionData ?: return null
        if (session.tenantId != tenant) return null
        return session
    }

    private fun unauthorizedResponse(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(mapOf("error" to "unauthorized", "message" to "Valid session required"))
    }
}

data class AccountResponse(
    val id: String,
    val name: String?,
    val iban: String,
    val type: String,
    val balance: BigDecimal,
    val currency: String,
    val status: String,
    val createdAt: Instant
)

data class TransactionResponse(
    val id: String,
    val date: String,
    val description: String,
    val amount: BigDecimal,
    val currency: String,
    val balanceAfter: BigDecimal?,
    val category: String?,
    val counterparty: String?
)

data class CardResponse(
    val cardId: String,
    val accountId: String,
    val cardNumberMasked: String,
    val expiryDate: String?,
    val cardType: String,
    val status: String,
    val createdAt: Instant
)

data class DashboardResponse(
    val accounts: List<AccountResponse>,
    val recentTransactions: List<TransactionResponse>,
    val cards: List<CardResponse>,
    val totalBalance: BigDecimal,
    val currency: String
)

internal fun AccountView.toResponse() = AccountResponse(
    id = accountId,
    name = accountName,
    iban = iban,
    type = accountType,
    balance = balance,
    currency = currency,
    status = status,
    createdAt = createdAt
)

internal fun TransactionView.toResponse() = TransactionResponse(
    id = transactionId,
    date = createdAt.toString(),
    description = remittanceInfo ?: reference ?: eventType,
    amount = if (eventType.contains("Withdrawn") || eventType.contains("Debit")) amount.negate() else amount,
    currency = currency,
    balanceAfter = balanceAfter,
    category = reference,
    counterparty = counterpartyName
)

internal fun CardView.toResponse() = CardResponse(
    cardId = cardId,
    accountId = accountId,
    cardNumberMasked = cardNumberMasked,
    expiryDate = expiryDate,
    cardType = cardType,
    status = status,
    createdAt = createdAt
)
