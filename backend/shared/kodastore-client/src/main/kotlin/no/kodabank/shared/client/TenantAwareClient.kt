package no.kodabank.shared.client

import no.kodabank.shared.domain.StreamIds
import no.kodabank.shared.domain.TenantId
import org.springframework.stereotype.Component

/**
 * Tenant-aware wrapper around KodaStoreClient.
 * Provides convenience methods for multi-tenant stream operations.
 */
@Component
class TenantAwareClient(
    private val client: KodaStoreClient
) {

    fun append(
        category: String,
        tenantId: TenantId,
        entityId: String,
        expectedVersion: Int?,
        events: List<NewEventRequest>
    ): List<RecordedEvent> {
        val streamId = StreamIds.streamId(category, tenantId, entityId)
        return client.append(streamId, expectedVersion, events)
    }

    fun appendToTenantStream(
        category: String,
        tenantId: TenantId,
        expectedVersion: Int?,
        events: List<NewEventRequest>
    ): List<RecordedEvent> {
        val streamId = StreamIds.tenantStreamId(category, tenantId)
        return client.append(streamId, expectedVersion, events)
    }

    fun appendToSharedStream(
        category: String,
        entityId: String,
        expectedVersion: Int?,
        events: List<NewEventRequest>
    ): List<RecordedEvent> {
        val streamId = StreamIds.sharedStreamId(category, entityId)
        return client.append(streamId, expectedVersion, events)
    }

    fun readStream(
        category: String,
        tenantId: TenantId,
        entityId: String,
        fromVersion: Int = 0
    ): StreamState {
        val streamId = StreamIds.streamId(category, tenantId, entityId)
        return client.readStream(streamId, fromVersion)
    }

    fun readTenantStream(
        category: String,
        tenantId: TenantId,
        fromVersion: Int = 0
    ): StreamState {
        val streamId = StreamIds.tenantStreamId(category, tenantId)
        return client.readStream(streamId, fromVersion)
    }

    fun readSharedStream(
        category: String,
        entityId: String,
        fromVersion: Int = 0
    ): StreamState {
        val streamId = StreamIds.sharedStreamId(category, entityId)
        return client.readStream(streamId, fromVersion)
    }

    /**
     * Read all events in a category for a specific tenant.
     * Fetches from kodastore and filters client-side by tenant prefix.
     */
    fun readCategoryForTenant(
        category: String,
        tenantId: TenantId,
        fromOffset: Long = 0,
        limit: Int = 1000
    ): List<RecordedEvent> {
        return client.readCategory(category, fromOffset, limit)
            .filter { StreamIds.belongsToTenant(it.streamId, tenantId) }
    }

    /**
     * Read all events in a category across all tenants.
     */
    fun readCategory(
        category: String,
        fromOffset: Long = 0,
        limit: Int = 1000
    ): List<RecordedEvent> {
        return client.readCategory(category, fromOffset, limit)
    }
}
