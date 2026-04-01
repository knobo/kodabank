package no.kodabank.shared.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency

data class Money(
    val amount: BigDecimal,
    val currency: Currency = Currency.getInstance("NOK")
) : Comparable<Money> {

    init {
        require(amount.scale() <= 2) { "Money amount must have at most 2 decimal places: $amount" }
    }

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return copy(amount = amount + other.amount)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return copy(amount = amount - other.amount)
    }

    operator fun unaryMinus(): Money = copy(amount = -amount)

    fun isPositive(): Boolean = amount > BigDecimal.ZERO
    fun isNegative(): Boolean = amount < BigDecimal.ZERO
    fun isZero(): Boolean = amount.compareTo(BigDecimal.ZERO) == 0

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return amount.compareTo(other.amount)
    }

    private fun requireSameCurrency(other: Money) {
        require(currency == other.currency) {
            "Cannot operate on different currencies: $currency vs ${other.currency}"
        }
    }

    companion object {
        fun nok(amount: String): Money = Money(BigDecimal(amount).setScale(2, RoundingMode.HALF_UP))
        fun nok(amount: Long): Money = Money(BigDecimal.valueOf(amount, 2).setScale(2, RoundingMode.HALF_UP))
        fun zero(currency: Currency = Currency.getInstance("NOK")): Money = Money(BigDecimal.ZERO.setScale(2), currency)
    }
}
