package no.kodabank.admin.tenant.application

import no.kodabank.shared.client.KodaStoreClient
import no.kodabank.shared.client.NewEventRequest
import no.kodabank.shared.domain.Iban
import no.kodabank.shared.domain.StreamIds
import no.kodabank.shared.domain.TenantId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.random.Random

@Service
class DemoDataGenerator(
    private val client: KodaStoreClient
) {
    private val log = LoggerFactory.getLogger(DemoDataGenerator::class.java)

    data class GenerationResult(
        val customersCreated: Int,
        val accountsCreated: Int,
        val cardsCreated: Int,
        val transactionsGenerated: Int
    )

    fun generate(definition: BankDefinition): GenerationResult {
        val demoData = definition.demoData ?: return GenerationResult(0, 0, 0, 0)
        val tenantId = TenantId(definition.tenant.id)
        val bankCode = definition.tenant.bankCode
        val currency = definition.tenant.currency

        var totalAccounts = 0
        var totalCards = 0
        var totalTransactions = 0
        var accountSequence = 1

        for (customer in demoData.customers) {
            val correlationId = UUID.randomUUID().toString()
            val partyId = UUID.randomUUID().toString()

            // 1. Register party
            writePartyRegistered(tenantId, partyId, customer, correlationId)
            writePartyIdentityVerified(tenantId, partyId, correlationId)

            // 2. Create accounts and collect their IDs for card linking
            val accountIds = mutableListOf<String>()

            for (accountDef in customer.accounts) {
                val accountId = UUID.randomUUID().toString()
                accountIds.add(accountId)
                val iban = Iban.generate(bankCode, accountSequence++)

                val product = definition.products.find { it.id == accountDef.product }

                when (accountDef.type) {
                    "CURRENT" -> {
                        writeCurrentAccountOpened(
                            tenantId, accountId, partyId, iban, currency,
                            accountDef.product, product?.name ?: "Brukskonto", correlationId
                        )

                        // Generate transactions, ending at the initialBalance
                        val txCount = if (accountDef.generateTransactions > 0) {
                            generateCurrentAccountTransactions(
                                tenantId, accountId, currency,
                                accountDef.initialBalance, accountDef.generateTransactions, correlationId
                            )
                        } else {
                            // Just deposit the initial balance
                            writeDeposit(
                                tenantId, accountId, currency,
                                accountDef.initialBalance, accountDef.initialBalance,
                                "Innskudd", "Åpningsinnskudd", correlationId
                            )
                            0
                        }
                        totalTransactions += txCount
                    }
                    "SAVINGS" -> {
                        val interestRate = product?.interestRate ?: BigDecimal("3.00")
                        writeSavingsAccountOpened(
                            tenantId, accountId, partyId, iban, currency,
                            accountDef.product, product?.name ?: "Sparekonto",
                            interestRate, correlationId
                        )

                        // Deposit the initial balance for savings accounts
                        writeSavingsDeposit(
                            tenantId, accountId,
                            accountDef.initialBalance, accountDef.initialBalance,
                            "Innskudd", correlationId
                        )
                    }
                }

                totalAccounts++
            }

            // 3. Create cards
            for (cardDef in customer.cards) {
                val cardId = UUID.randomUUID().toString()
                val linkedAccountId = accountIds.getOrNull(cardDef.linkedAccount) ?: accountIds.firstOrNull()
                if (linkedAccountId != null) {
                    writeCardIssued(tenantId, cardId, partyId, linkedAccountId, cardDef.type, correlationId)
                    writeCardActivated(tenantId, cardId, correlationId)
                    totalCards++
                }
            }

            log.info(
                "Generated demo data for customer {} {} (party={}, accounts={}, cards={})",
                customer.firstName, customer.lastName, partyId, customer.accounts.size, customer.cards.size
            )
        }

        return GenerationResult(
            customersCreated = demoData.customers.size,
            accountsCreated = totalAccounts,
            cardsCreated = totalCards,
            transactionsGenerated = totalTransactions
        )
    }

    // -- Party events --

    private fun writePartyRegistered(
        tenantId: TenantId, partyId: String, customer: CustomerDefinition, correlationId: String
    ) {
        val streamId = StreamIds.streamId("Party", tenantId, partyId)
        val payload = mapOf<String, Any?>(
            "partyId" to partyId,
            "tenantId" to tenantId.value,
            "nationalIdHash" to hashNationalId(customer.nationalId),
            "firstName" to customer.firstName,
            "lastName" to customer.lastName,
            "email" to "${customer.firstName.lowercase()}.${customer.lastName.lowercase()}@example.no",
            "phone" to null,
            "address" to null
        )
        client.append(
            streamId = streamId,
            expectedVersion = null,
            events = listOf(NewEventRequest("PartyRegistered", payload, buildMetadata(tenantId, correlationId)))
        )
    }

    private fun writePartyIdentityVerified(tenantId: TenantId, partyId: String, correlationId: String) {
        val streamId = StreamIds.streamId("Party", tenantId, partyId)
        val payload = mapOf<String, Any?>(
            "partyId" to partyId,
            "verificationMethod" to "BankID",
            "verifiedAt" to Instant.now().toString(),
            "level" to "HIGH"
        )
        client.append(
            streamId = streamId,
            expectedVersion = 1,
            events = listOf(NewEventRequest("PartyIdentityVerified", payload, buildMetadata(tenantId, correlationId)))
        )
    }

    // -- Current Account events --

    private fun writeCurrentAccountOpened(
        tenantId: TenantId, accountId: String, partyId: String, iban: Iban,
        currency: String, productId: String, accountName: String, correlationId: String
    ) {
        val streamId = StreamIds.streamId("CurrentAccount", tenantId, accountId)
        val payload = mapOf<String, Any?>(
            "accountId" to accountId,
            "tenantId" to tenantId.value,
            "partyId" to partyId,
            "iban" to iban.value,
            "currency" to currency,
            "productId" to productId,
            "accountName" to accountName
        )
        client.append(
            streamId = streamId,
            expectedVersion = null,
            events = listOf(NewEventRequest("CurrentAccountOpened", payload, buildMetadata(tenantId, correlationId)))
        )
    }

    private fun writeDeposit(
        tenantId: TenantId, accountId: String, currency: String,
        amount: BigDecimal, balanceAfter: BigDecimal,
        reference: String, remittanceInfo: String?, correlationId: String,
        counterpartyName: String? = null, counterpartyIban: String? = null,
        expectedVersion: Int? = null
    ) {
        val streamId = StreamIds.streamId("CurrentAccount", tenantId, accountId)
        val payload = mapOf<String, Any?>(
            "accountId" to accountId,
            "amount" to amount,
            "currency" to currency,
            "reference" to reference,
            "balanceAfter" to balanceAfter,
            "counterpartyName" to counterpartyName,
            "counterpartyIban" to counterpartyIban,
            "remittanceInfo" to remittanceInfo
        )
        client.append(
            streamId = streamId,
            expectedVersion = expectedVersion,
            events = listOf(NewEventRequest("FundsDeposited", payload, buildMetadata(tenantId, correlationId)))
        )
    }

    private fun writeWithdrawal(
        tenantId: TenantId, accountId: String, currency: String,
        amount: BigDecimal, balanceAfter: BigDecimal,
        reference: String, remittanceInfo: String?, correlationId: String,
        counterpartyName: String? = null, counterpartyIban: String? = null,
        expectedVersion: Int? = null
    ) {
        val streamId = StreamIds.streamId("CurrentAccount", tenantId, accountId)
        val payload = mapOf<String, Any?>(
            "accountId" to accountId,
            "amount" to amount,
            "currency" to currency,
            "reference" to reference,
            "balanceAfter" to balanceAfter,
            "counterpartyName" to counterpartyName,
            "counterpartyIban" to counterpartyIban,
            "remittanceInfo" to remittanceInfo
        )
        client.append(
            streamId = streamId,
            expectedVersion = expectedVersion,
            events = listOf(NewEventRequest("FundsWithdrawn", payload, buildMetadata(tenantId, correlationId)))
        )
    }

    // -- Savings Account events --

    private fun writeSavingsAccountOpened(
        tenantId: TenantId, accountId: String, partyId: String, iban: Iban,
        currency: String, productId: String, accountName: String,
        interestRate: BigDecimal, correlationId: String
    ) {
        val streamId = StreamIds.streamId("SavingsAccount", tenantId, accountId)
        val payload = mapOf<String, Any?>(
            "accountId" to accountId,
            "tenantId" to tenantId.value,
            "partyId" to partyId,
            "iban" to iban.value,
            "currency" to currency,
            "productId" to productId,
            "accountName" to accountName,
            "interestRate" to interestRate
        )
        client.append(
            streamId = streamId,
            expectedVersion = null,
            events = listOf(NewEventRequest("SavingsAccountOpened", payload, buildMetadata(tenantId, correlationId)))
        )
    }

    private fun writeSavingsDeposit(
        tenantId: TenantId, accountId: String,
        amount: BigDecimal, balanceAfter: BigDecimal,
        reference: String, correlationId: String
    ) {
        val streamId = StreamIds.streamId("SavingsAccount", tenantId, accountId)
        val payload = mapOf<String, Any?>(
            "accountId" to accountId,
            "amount" to amount,
            "sourceAccountId" to "initial-deposit",
            "reference" to reference,
            "balanceAfter" to balanceAfter
        )
        client.append(
            streamId = streamId,
            expectedVersion = 1,
            events = listOf(NewEventRequest("SavingsDeposited", payload, buildMetadata(tenantId, correlationId)))
        )
    }

    // -- Card events --

    private fun writeCardIssued(
        tenantId: TenantId, cardId: String, partyId: String,
        accountId: String, cardType: String, correlationId: String
    ) {
        val streamId = StreamIds.streamId("Card", tenantId, cardId)
        val lastFour = Random.nextInt(1000, 9999).toString().padStart(4, '0')
        val payload = mapOf<String, Any?>(
            "cardId" to cardId,
            "tenantId" to tenantId.value,
            "partyId" to partyId,
            "accountId" to accountId,
            "cardNumberMasked" to "**** **** **** $lastFour",
            "expiryDate" to "03/28",
            "cardType" to cardType
        )
        client.append(
            streamId = streamId,
            expectedVersion = null,
            events = listOf(NewEventRequest("CardIssued", payload, buildMetadata(tenantId, correlationId)))
        )
    }

    private fun writeCardActivated(tenantId: TenantId, cardId: String, correlationId: String) {
        val streamId = StreamIds.streamId("Card", tenantId, cardId)
        val payload = mapOf<String, Any?>(
            "cardId" to cardId,
            "activatedAt" to Instant.now().toString()
        )
        client.append(
            streamId = streamId,
            expectedVersion = 1,
            events = listOf(NewEventRequest("CardActivated", payload, buildMetadata(tenantId, correlationId)))
        )
    }

    // -- Transaction generation --

    /**
     * Generates realistic Norwegian transaction history for a current account.
     * Transactions span the last 3 months and maintain a correct running balance,
     * ending at the specified initialBalance.
     *
     * Strategy: generate transactions chronologically, then adjust the starting
     * deposit so the final balance matches initialBalance.
     */
    private fun generateCurrentAccountTransactions(
        tenantId: TenantId, accountId: String, currency: String,
        initialBalance: BigDecimal, count: Int, correlationId: String
    ): Int {
        val today = LocalDate.now()
        val threeMonthsAgo = today.minusMonths(3)
        val oslo = ZoneId.of("Europe/Oslo")

        // Build a list of transaction descriptors chronologically
        val transactions = mutableListOf<TransactionDescriptor>()

        // Generate monthly recurring transactions for 3 months
        for (monthOffset in 2 downTo 0) {
            val monthDate = today.minusMonths(monthOffset.toLong())
            val year = monthDate.year
            val month = monthDate.monthValue

            // Salary on the 25th (deposit)
            val salaryDay = safeDay(year, month, 25)
            if (!salaryDay.isAfter(today)) {
                transactions.add(
                    TransactionDescriptor(
                        date = salaryDay,
                        isDeposit = true,
                        amount = randomAmount(25000, 55000),
                        reference = "Lønnsutbetaling",
                        counterpartyName = EMPLOYERS.random(),
                        remittanceInfo = "Lønn ${norwegianMonth(month)} $year"
                    )
                )
            }

            // Rent on the 1st (withdrawal)
            val rentDay = safeDay(year, month, 1)
            if (!rentDay.isBefore(threeMonthsAgo) && !rentDay.isAfter(today)) {
                transactions.add(
                    TransactionDescriptor(
                        date = rentDay,
                        isDeposit = false,
                        amount = randomAmount(8000, 15000),
                        reference = "Husleie",
                        counterpartyName = "Utleier AS",
                        remittanceInfo = "Husleie ${norwegianMonth(month)} $year"
                    )
                )
            }

            // Subscriptions around the 5th (withdrawals)
            val subDay = safeDay(year, month, 5)
            if (!subDay.isBefore(threeMonthsAgo) && !subDay.isAfter(today)) {
                for (sub in SUBSCRIPTIONS.shuffled().take(Random.nextInt(2, 4))) {
                    transactions.add(
                        TransactionDescriptor(
                            date = subDay.plusDays(Random.nextLong(0, 3)),
                            isDeposit = false,
                            amount = randomAmount(sub.minAmount, sub.maxAmount),
                            reference = "Abonnement",
                            counterpartyName = sub.name,
                            remittanceInfo = sub.name
                        )
                    )
                }
            }

            // Utilities around the 15th
            val utilDay = safeDay(year, month, 15)
            if (!utilDay.isBefore(threeMonthsAgo) && !utilDay.isAfter(today)) {
                for (util in UTILITIES.shuffled().take(Random.nextInt(1, 3))) {
                    transactions.add(
                        TransactionDescriptor(
                            date = utilDay.plusDays(Random.nextLong(0, 5)),
                            isDeposit = false,
                            amount = randomAmount(util.minAmount, util.maxAmount),
                            reference = "Faktura",
                            counterpartyName = util.name,
                            remittanceInfo = util.name
                        )
                    )
                }
            }
        }

        // Fill remaining transaction slots with random groceries, transport, restaurants, ATM
        val remaining = count - transactions.size
        if (remaining > 0) {
            for (i in 0 until remaining) {
                val randomDate = threeMonthsAgo.plusDays(Random.nextLong(1, threeMonthsAgo.until(today, java.time.temporal.ChronoUnit.DAYS).coerceAtLeast(1)))
                val txType = RANDOM_TX_TYPES.random()
                transactions.add(
                    TransactionDescriptor(
                        date = if (randomDate.isAfter(today)) today else randomDate,
                        isDeposit = txType.isDeposit,
                        amount = randomAmount(txType.minAmount, txType.maxAmount),
                        reference = txType.reference,
                        counterpartyName = txType.names.random(),
                        remittanceInfo = txType.names.random()
                    )
                )
            }
        }

        // Sort by date, then trim to requested count
        val sortedTxs = transactions.sortedBy { it.date }.take(count)

        // Calculate what opening deposit is needed so the final balance = initialBalance
        // openingDeposit + sum(deposits) - sum(withdrawals) = initialBalance
        val depositsSum = sortedTxs.filter { it.isDeposit }.sumOf { it.amount }
        val withdrawalsSum = sortedTxs.filter { !it.isDeposit }.sumOf { it.amount }
        val openingDeposit = (initialBalance - depositsSum + withdrawalsSum).coerceAtLeast(BigDecimal("1000.00"))

        // Write the opening deposit event (version 1 = after the CurrentAccountOpened at v1)
        var runningBalance = openingDeposit
        val openingDate = threeMonthsAgo.minusDays(1)

        writeDeposit(
            tenantId, accountId, currency,
            openingDeposit, runningBalance,
            "Innskudd", "Åpningsinnskudd", correlationId,
            expectedVersion = 1
        )

        // Write each transaction event
        var version = 2
        for (tx in sortedTxs) {
            if (tx.isDeposit) {
                runningBalance = runningBalance.add(tx.amount)
                writeDeposit(
                    tenantId, accountId, currency,
                    tx.amount, runningBalance,
                    tx.reference, tx.remittanceInfo, correlationId,
                    counterpartyName = tx.counterpartyName,
                    expectedVersion = version
                )
            } else {
                // Ensure we never go below zero
                val withdrawalAmount = tx.amount.min(runningBalance.subtract(BigDecimal("10.00")).coerceAtLeast(BigDecimal.ZERO))
                if (withdrawalAmount > BigDecimal.ZERO) {
                    runningBalance = runningBalance.subtract(withdrawalAmount)
                    writeWithdrawal(
                        tenantId, accountId, currency,
                        withdrawalAmount, runningBalance,
                        tx.reference, tx.remittanceInfo, correlationId,
                        counterpartyName = tx.counterpartyName,
                        expectedVersion = version
                    )
                } else {
                    // Skip this transaction if insufficient funds
                    continue
                }
            }
            version++
        }

        return version - 1 // Number of transaction events written (excluding opening deposit)
    }

    // -- Helper types and data --

    private data class TransactionDescriptor(
        val date: LocalDate,
        val isDeposit: Boolean,
        val amount: BigDecimal,
        val reference: String,
        val counterpartyName: String,
        val remittanceInfo: String
    )

    private data class NamedAmount(
        val name: String,
        val minAmount: Int,
        val maxAmount: Int
    )

    private data class RandomTxType(
        val reference: String,
        val names: List<String>,
        val minAmount: Int,
        val maxAmount: Int,
        val isDeposit: Boolean = false
    )

    // -- Helpers --

    private fun buildMetadata(tenantId: TenantId, correlationId: String): Map<String, Any?> = mapOf(
        "tenantId" to tenantId.value,
        "sourceService" to "kodabank-admin",
        "correlationId" to correlationId,
        "generatedBy" to "DemoDataGenerator"
    )

    private fun hashNationalId(nationalId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(nationalId.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun randomAmount(min: Int, max: Int): BigDecimal {
        val whole = Random.nextInt(min, max + 1)
        val cents = Random.nextInt(0, 100)
        return BigDecimal("$whole.${"$cents".padStart(2, '0')}").setScale(2, RoundingMode.HALF_UP)
    }

    private fun BigDecimal.coerceAtLeast(other: BigDecimal): BigDecimal =
        if (this < other) other else this

    private fun safeDay(year: Int, month: Int, day: Int): LocalDate {
        val maxDay = LocalDate.of(year, month, 1).lengthOfMonth()
        return LocalDate.of(year, month, day.coerceAtMost(maxDay))
    }

    private fun norwegianMonth(month: Int): String = when (month) {
        1 -> "januar"
        2 -> "februar"
        3 -> "mars"
        4 -> "april"
        5 -> "mai"
        6 -> "juni"
        7 -> "juli"
        8 -> "august"
        9 -> "september"
        10 -> "oktober"
        11 -> "november"
        12 -> "desember"
        else -> "ukjent"
    }

    companion object {
        private val EMPLOYERS = listOf(
            "Equinor ASA",
            "Telenor ASA",
            "DNB ASA",
            "Norsk Hydro ASA",
            "Kongsberg Gruppen",
            "Aker Solutions",
            "Yara International"
        )

        private val SUBSCRIPTIONS = listOf(
            NamedAmount("Netflix", 99, 199),
            NamedAmount("Spotify", 99, 119),
            NamedAmount("Viaplay", 129, 199),
            NamedAmount("HBO Max", 99, 149),
            NamedAmount("NRK Lisens", 170, 170)
        )

        private val UTILITIES = listOf(
            NamedAmount("Hafslund Strom", 200, 1500),
            NamedAmount("Telia Norge", 299, 599),
            NamedAmount("Telenor Mobil", 299, 499),
            NamedAmount("Fjordkraft", 300, 1200)
        )

        private val RANDOM_TX_TYPES = listOf(
            RandomTxType(
                reference = "Varekjop",
                names = listOf(
                    "REMA 1000 Storgata", "Kiwi Majorstuen", "Meny Gronland",
                    "Coop Extra Torshov", "Joker Gronlandsleiret", "Bunnpris Grunerlokka"
                ),
                minAmount = 150, maxAmount = 800
            ),
            RandomTxType(
                reference = "Varekjop",
                names = listOf(
                    "REMA 1000 Storgata", "Kiwi Majorstuen", "Meny Gronland",
                    "Coop Extra Torshov"
                ),
                minAmount = 80, maxAmount = 450
            ),
            RandomTxType(
                reference = "Transport",
                names = listOf(
                    "Ruter reise", "Vy tog Oslo-Bergen", "Ruter manedskort",
                    "Vy tog Oslo-Drammen", "Oslo Taxi"
                ),
                minAmount = 39, maxAmount = 800
            ),
            RandomTxType(
                reference = "Restaurant",
                names = listOf(
                    "Peppes Pizza Aker Brygge", "Burger King Stortorget",
                    "Egon Restaurant", "TGI Fridays Oslo", "Starbucks Karl Johan"
                ),
                minAmount = 150, maxAmount = 500
            ),
            RandomTxType(
                reference = "Minibank",
                names = listOf(
                    "Minibank DNB Stortorget", "Minibank Nordea Majorstuen",
                    "Minibank SpareBank 1"
                ),
                minAmount = 200, maxAmount = 2000
            ),
            RandomTxType(
                reference = "Overforing",
                names = listOf("Vipps betaling", "Overforing", "Betaling"),
                minAmount = 100, maxAmount = 3000
            ),
            RandomTxType(
                reference = "Overforing",
                names = listOf("Vipps fra kontakt", "Tilbakebetaling"),
                minAmount = 50, maxAmount = 500,
                isDeposit = true
            )
        )
    }
}
