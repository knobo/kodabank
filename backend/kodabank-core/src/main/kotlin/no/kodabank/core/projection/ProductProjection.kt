package no.kodabank.core.projection

import com.fasterxml.jackson.databind.ObjectMapper
import no.kodabank.shared.client.KodaStoreClient
import no.kodabank.shared.client.RecordedEvent
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ProductProjection(
    jdbc: JdbcTemplate,
    store: KodaStoreClient,
    private val objectMapper: ObjectMapper
) : ProjectionWorker(jdbc, store) {

    private val log = LoggerFactory.getLogger(javaClass)

    override val projectionName = "product-projection"
    override val categories = listOf("ProductCatalog")

    @Scheduled(fixedDelay = 500)
    fun run() = poll()

    override fun handleEvent(event: RecordedEvent) {
        val p = event.payload
        when (event.eventType) {
            "ProductDefined" -> {
                val tenantId = extractTenantId(event.streamId)
                jdbc.update(
                    """INSERT INTO rm_products
                       (product_id, tenant_id, name, product_type, features, fees, interest_rate, status)
                       VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, 'ACTIVE')
                       ON CONFLICT (product_id) DO NOTHING""",
                    p["productId"] as String,
                    tenantId,
                    p["name"] as String,
                    p["type"] as String,
                    toJson(p["features"]),
                    toJson(p["fees"]),
                    p["interestRate"]?.let { toBigDecimal(it) }
                )
                log.info("Projected ProductDefined for product {}", p["productId"])
            }

            "ProductUpdated" -> {
                val productId = p["productId"] as String
                jdbc.update(
                    """UPDATE rm_products SET
                       name = COALESCE(?, name),
                       features = COALESCE(?::jsonb, features),
                       fees = COALESCE(?::jsonb, fees),
                       interest_rate = COALESCE(?, interest_rate)
                       WHERE product_id = ?""",
                    p["name"] as? String,
                    p["features"]?.let { toJson(it) },
                    p["fees"]?.let { toJson(it) },
                    p["interestRate"]?.let { toBigDecimal(it) },
                    productId
                )
                log.info("Projected ProductUpdated for product {}", productId)
            }

            "ProductRetired" -> {
                jdbc.update(
                    "UPDATE rm_products SET status = 'RETIRED' WHERE product_id = ?",
                    p["productId"] as String
                )
                log.info("Projected ProductRetired for product {}", p["productId"])
            }

            else -> log.debug("Ignoring event type {} in ProductProjection", event.eventType)
        }
    }

    private fun toJson(value: Any?): String? {
        if (value == null) return null
        return objectMapper.writeValueAsString(value)
    }
}
