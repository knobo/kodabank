package no.kodabank.core.account.web

import no.kodabank.core.account.application.AccountNotFoundException
import no.kodabank.core.account.application.CurrentAccountService
import no.kodabank.core.account.application.SavingsAccountService
import no.kodabank.core.account.domain.CurrentAccountState
import no.kodabank.core.account.domain.SavingsAccountState
import no.kodabank.shared.domain.TenantId
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

@RestController
@RequestMapping("/api/internal")
class AccountController(
    private val currentAccountService: CurrentAccountService,
    private val savingsAccountService: SavingsAccountService
) {

    // -- Current Account --

    @PostMapping("/accounts/current")
    fun openCurrentAccount(@RequestBody req: OpenCurrentAccountRequest): ResponseEntity<CurrentAccountState> {
        val state = currentAccountService.openAccount(
            tenantId = TenantId(req.tenantId),
            partyId = req.partyId,
            iban = req.iban,
            currency = req.currency,
            productId = req.productId,
            accountName = req.accountName,
            correlationId = req.correlationId
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(state)
    }

    @PostMapping("/accounts/{accountId}/deposit")
    fun deposit(
        @PathVariable accountId: String,
        @RequestBody req: DepositRequest
    ): CurrentAccountState {
        return currentAccountService.deposit(
            tenantId = TenantId(req.tenantId),
            accountId = accountId,
            amount = req.amount,
            reference = req.reference,
            counterpartyName = req.counterpartyName,
            counterpartyIban = req.counterpartyIban,
            remittanceInfo = req.remittanceInfo,
            correlationId = req.correlationId
        )
    }

    @PostMapping("/accounts/{accountId}/withdraw")
    fun withdraw(
        @PathVariable accountId: String,
        @RequestBody req: WithdrawRequest
    ): CurrentAccountState {
        return currentAccountService.withdraw(
            tenantId = TenantId(req.tenantId),
            accountId = accountId,
            amount = req.amount,
            reference = req.reference,
            counterpartyName = req.counterpartyName,
            counterpartyIban = req.counterpartyIban,
            remittanceInfo = req.remittanceInfo,
            correlationId = req.correlationId
        )
    }

    @GetMapping("/accounts/{accountId}")
    fun getAccount(
        @PathVariable accountId: String,
        @RequestParam tenantId: String
    ): CurrentAccountState {
        return currentAccountService.getAccount(TenantId(tenantId), accountId)
    }

    // -- Savings Account --

    @PostMapping("/accounts/savings")
    fun openSavingsAccount(@RequestBody req: OpenSavingsAccountRequest): ResponseEntity<SavingsAccountState> {
        val state = savingsAccountService.openAccount(
            tenantId = TenantId(req.tenantId),
            partyId = req.partyId,
            iban = req.iban,
            currency = req.currency,
            productId = req.productId,
            accountName = req.accountName,
            interestRate = req.interestRate,
            correlationId = req.correlationId
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(state)
    }

    // -- Error Handling --

    @ExceptionHandler(AccountNotFoundException::class)
    fun handleNotFound(e: AccountNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse("ACCOUNT_NOT_FOUND", e.message ?: ""))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(e: IllegalArgumentException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse("BAD_REQUEST", e.message ?: ""))
}

// -- Request/Response DTOs --

data class OpenCurrentAccountRequest(
    val tenantId: String,
    val partyId: String,
    val iban: String,
    val currency: String = "NOK",
    val productId: String,
    val accountName: String,
    val correlationId: String? = null
)

data class OpenSavingsAccountRequest(
    val tenantId: String,
    val partyId: String,
    val iban: String,
    val currency: String = "NOK",
    val productId: String,
    val accountName: String,
    val interestRate: BigDecimal,
    val correlationId: String? = null
)

data class DepositRequest(
    val tenantId: String,
    val amount: BigDecimal,
    val reference: String,
    val counterpartyName: String? = null,
    val counterpartyIban: String? = null,
    val remittanceInfo: String? = null,
    val correlationId: String? = null
)

data class WithdrawRequest(
    val tenantId: String,
    val amount: BigDecimal,
    val reference: String,
    val counterpartyName: String? = null,
    val counterpartyIban: String? = null,
    val remittanceInfo: String? = null,
    val correlationId: String? = null
)

data class ErrorResponse(val code: String, val message: String)
