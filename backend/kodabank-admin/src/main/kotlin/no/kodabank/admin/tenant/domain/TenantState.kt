package no.kodabank.admin.tenant.domain

import java.math.BigDecimal

data class TenantState(
    val tenantId: String,
    val bankName: String,
    val bankCode: String,
    val ibanPrefix: String,
    val country: String,
    val currency: String,
    val branding: BrandingInfo,
    val nostroAccountId: String?,
    val nostroBalance: BigDecimal?,
    val ownerUserId: String? = null,
    val accessPolicy: AccessPolicy? = null,
    val transferPolicy: TransferPolicy? = null,
    val status: TenantStatus = TenantStatus.ACTIVE,
    val version: Int
) {
    companion object {
        val EMPTY = TenantState(
            tenantId = "",
            bankName = "",
            bankCode = "",
            ibanPrefix = "",
            country = "",
            currency = "",
            branding = BrandingInfo(
                primaryColor = "",
                secondaryColor = "",
                logo = "",
                tagline = ""
            ),
            nostroAccountId = null,
            nostroBalance = null,
            ownerUserId = null,
            accessPolicy = null,
            transferPolicy = null,
            status = TenantStatus.ACTIVE,
            version = 0
        )

        fun evolve(state: TenantState, event: TenantEvent, version: Int): TenantState =
            when (event) {
                is BankTenantCreated -> state.copy(
                    tenantId = event.tenantId,
                    bankName = event.bankName,
                    bankCode = event.bankCode,
                    ibanPrefix = event.ibanPrefix,
                    country = event.country,
                    currency = event.currency,
                    branding = event.branding,
                    status = TenantStatus.ACTIVE,
                    version = version
                )
                is BankTenantRegistered -> state.copy(
                    tenantId = event.tenantId,
                    ownerUserId = event.ownerUserId,
                    bankName = event.bankName,
                    bankCode = event.bankCode,
                    ibanPrefix = event.ibanPrefix,
                    country = event.country,
                    currency = event.currency,
                    accessPolicy = event.accessPolicy,
                    transferPolicy = event.transferPolicy,
                    status = TenantStatus.PENDING,
                    version = version
                )
                is TransferPolicyUpdated -> state.copy(
                    transferPolicy = TransferPolicy(
                        type = event.policyType,
                        whitelist = event.whitelist,
                        domainCode = event.domainCode
                    ),
                    version = version
                )
                is AccessPolicyUpdated -> state.copy(
                    accessPolicy = AccessPolicy(
                        type = event.policyType,
                        webhookUrl = event.webhookUrl
                    ),
                    version = version
                )
                is BankBrandingUpdated -> state.copy(
                    branding = BrandingInfo(
                        primaryColor = event.primaryColor ?: state.branding.primaryColor,
                        secondaryColor = event.secondaryColor ?: state.branding.secondaryColor,
                        logo = event.logo ?: state.branding.logo,
                        tagline = event.tagline ?: state.branding.tagline
                    ),
                    version = version
                )
                is NostroAccountConfigured -> state.copy(
                    nostroAccountId = event.nostroAccountId,
                    nostroBalance = event.initialBalance,
                    version = version
                )
            }

        fun rebuild(events: List<Pair<TenantEvent, Int>>): TenantState =
            events.fold(EMPTY) { state, (event, version) -> evolve(state, event, version) }
    }
}
