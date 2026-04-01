package no.kodabank.shared.domain

/**
 * Stream ID construction utilities for the kodabank multi-tenant convention.
 *
 * Convention: {Category}-{tenantId}_{entityId}
 * Example: CurrentAccount-fjordbank_acc-01JQRX
 *
 * The underscore separates tenantId from entityId within the entityId portion
 * of kodastore's Category-EntityID format.
 */
object StreamIds {

    fun streamId(category: String, tenantId: TenantId, entityId: String): String =
        "$category-${tenantId.value}_$entityId"

    fun tenantStreamId(category: String, tenantId: TenantId): String =
        "$category-${tenantId.value}"

    fun sharedStreamId(category: String, entityId: String): String =
        "$category-$entityId"

    fun extractTenantId(streamId: String): TenantId? {
        val entityPart = streamId.substringAfter('-', "")
        val tenantPart = entityPart.substringBefore('_', "")
        return if (tenantPart.isNotBlank()) TenantId(tenantPart) else null
    }

    fun extractEntityId(streamId: String): String {
        val entityPart = streamId.substringAfter('-', "")
        return entityPart.substringAfter('_', entityPart)
    }

    fun extractCategory(streamId: String): String =
        streamId.substringBefore('-')

    fun membershipStreamId(tenantId: TenantId, userId: String): String =
        streamId("BankMembership", tenantId, userId)

    fun belongsToTenant(streamId: String, tenantId: TenantId): Boolean {
        val entityPart = streamId.substringAfter('-', "")
        return entityPart.startsWith("${tenantId.value}_")
    }
}
