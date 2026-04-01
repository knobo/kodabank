package no.kodabank.shared.domain

/**
 * Norwegian IBAN: NO{checkDigits:2}{bankCode:4}{accountNumber:6}{checkDigit:1}
 * Total: 15 characters
 */
@JvmInline
value class Iban(val value: String) {
    init {
        require(value.length == 15) { "Norwegian IBAN must be 15 characters: $value" }
        require(value.startsWith("NO")) { "Norwegian IBAN must start with NO: $value" }
        require(value.substring(2).all { it.isDigit() }) { "IBAN digits must be numeric: $value" }
    }

    val bankCode: String get() = value.substring(4, 8)
    val accountNumber: String get() = value.substring(8, 14)
    val checkDigit: String get() = value.substring(14, 15)
    val ibanCheckDigits: String get() = value.substring(2, 4)

    /** Returns the Norwegian account number format: bankCode.accountNumber.checkDigit */
    val displayAccountNumber: String
        get() = "${bankCode}.${value.substring(8, 10)}.${value.substring(10, 15)}"

    companion object {
        /**
         * Generate a Norwegian IBAN from bank code and account sequence.
         * Bank code: 4 digits, account sequence: 6 digits, check digit computed with MOD 11.
         */
        fun generate(bankCode: String, accountSequence: Int): Iban {
            require(bankCode.length == 4 && bankCode.all { it.isDigit() }) {
                "Bank code must be 4 digits: $bankCode"
            }
            val accountNum = accountSequence.toString().padStart(6, '0')
            val checkDigit = computeMod11CheckDigit(bankCode + accountNum)
            val accountPart = "$bankCode$accountNum$checkDigit"
            val ibanCheckDigits = computeIbanCheckDigits("NO", accountPart)
            return Iban("NO$ibanCheckDigits$accountPart")
        }

        private fun computeMod11CheckDigit(digits: String): String {
            val weights = intArrayOf(5, 4, 3, 2, 7, 6, 5, 4, 3, 2)
            val sum = digits.mapIndexed { index, c ->
                c.digitToInt() * weights[index]
            }.sum()
            val remainder = 11 - (sum % 11)
            return when (remainder) {
                11 -> "0"
                10 -> "0" // Invalid in real banking, but acceptable for demo
                else -> remainder.toString()
            }
        }

        private fun computeIbanCheckDigits(countryCode: String, bban: String): String {
            // Move country code to end with 00 check digits, convert letters to numbers
            val rearranged = bban + countryCode.map { it.code - 'A'.code + 10 }.joinToString("") + "00"
            val number = rearranged.toBigInteger()
            val checkDigits = 98.toBigInteger() - (number % 97.toBigInteger())
            return checkDigits.toString().padStart(2, '0')
        }
    }
}
