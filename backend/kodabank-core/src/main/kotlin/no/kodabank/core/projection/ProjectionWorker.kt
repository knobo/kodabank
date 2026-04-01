package no.kodabank.core.projection

import no.kodabank.shared.client.KodaStoreClient
import no.kodabank.shared.client.RecordedEvent
import no.kodabank.shared.domain.StreamIds
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Instant

/**
 * Base class for projection workers that poll kodastore event categories
 * and update read model tables.
 *
 * Each subclass processes a specific category (or set of categories) and
 * tracks its own checkpoint in the projection_checkpoints table.
 */
abstract class ProjectionWorker(
    protected val jdbc: JdbcTemplate,
    protected val store: KodaStoreClient
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Unique name for this projection, used as the checkpoint key. */
    abstract val projectionName: String

    /** The kodastore categories this projection polls. */
    abstract val categories: List<String>

    /** Handle a single event. Called for each event in order. */
    abstract fun handleEvent(event: RecordedEvent)

    /** Default batch size per poll cycle. */
    open val batchSize: Int = 500

    fun poll() {
        for (category in categories) {
            try {
                pollCategory(category)
            } catch (e: Exception) {
                log.error("Projection {} failed polling category {}", projectionName, category, e)
            }
        }
    }

    private fun pollCategory(category: String) {
        val checkpointKey = if (categories.size > 1) "$projectionName:$category" else projectionName
        val lastOffset = readCheckpoint(checkpointKey)
        val events = store.readCategory(category, fromOffset = lastOffset + 1, limit = batchSize)
        if (events.isEmpty()) return

        log.debug("Projection {} processing {} events from category {} (offset {})",
            projectionName, events.size, category, lastOffset + 1)

        for (event in events) {
            try {
                handleEvent(event)
            } catch (e: Exception) {
                log.error("Projection {} failed on event {} (offset={}, type={})",
                    projectionName, event.streamId, event.globalOffset, event.eventType, e)
                throw e
            }
        }

        val maxOffset = events.maxOf { it.globalOffset }
        updateCheckpoint(checkpointKey, maxOffset)
    }

    private fun readCheckpoint(key: String): Long {
        val offsets = jdbc.queryForList(
            "SELECT last_offset FROM projection_checkpoints WHERE projection_name = ?",
            Long::class.java,
            key
        )
        return offsets.firstOrNull() ?: 0L
    }

    private fun updateCheckpoint(key: String, offset: Long) {
        jdbc.update(
            """INSERT INTO projection_checkpoints (projection_name, last_offset, updated_at)
               VALUES (?, ?, now())
               ON CONFLICT (projection_name) DO UPDATE SET last_offset = ?, updated_at = now()""",
            key, offset, offset
        )
    }

    // -- Utility helpers for subclasses --

    protected fun extractTenantId(streamId: String): String {
        return StreamIds.extractTenantId(streamId)?.value
            ?: StreamIds.extractEntityId(streamId)
    }

    protected fun toTimestamp(instant: Instant): Timestamp = Timestamp.from(instant)

    protected fun toBigDecimal(value: Any?): BigDecimal = when (value) {
        is BigDecimal -> value
        is Number -> BigDecimal(value.toString())
        is String -> BigDecimal(value)
        else -> throw IllegalArgumentException("Cannot convert $value to BigDecimal")
    }
}
