package no.kodabank.shared.domain

@JvmInline
value class TenantId(val value: String) {
    init {
        require(value.isNotBlank()) { "TenantId must not be blank" }
        require(value.matches(VALID_PATTERN)) { "TenantId must be lowercase alphanumeric: $value" }
    }

    companion object {
        private val VALID_PATTERN = Regex("^[a-z][a-z0-9]{1,49}$")
    }
}
