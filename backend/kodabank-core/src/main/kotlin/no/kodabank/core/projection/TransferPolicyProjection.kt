package no.kodabank.core.projection

import no.kodabank.shared.client.KodaStoreClient
import no.kodabank.shared.client.RecordedEvent
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class TransferPolicyProjection(
    jdbc: JdbcTemplate,
    store: KodaStoreClient
) : ProjectionWorker(jdbc, store) {

    private val log = LoggerFactory.getLogger(javaClass)

    override val projectionName = "transfer-policy-projection"
    override val categories = listOf("BankTenant")

    @Scheduled(fixedDelay = 500)
    fun run() = poll()

    override fun handleEvent(event: RecordedEvent) {
        val p = event.payload
        when (event.eventType) {
            "BankTenantCreated" -> {
                val tenantId = p["tenantId"] as String
                jdbc.update(
                    """INSERT INTO rm_transfer_policies (tenant_id, policy_type, whitelist, domain_code)
                       VALUES (?, 'OPEN', '[]', NULL)
                       ON CONFLICT (tenant_id) DO NOTHING""",
                    tenantId
                )
                log.info("Projected BankTenantCreated into rm_transfer_policies for tenant {}", tenantId)
            }

            "BankTenantRegistered" -> {
                val tenantId = p["tenantId"] as String
                @Suppress("UNCHECKED_CAST")
                val transferPolicy = p["transferPolicy"] as? Map<String, Any?>
                val policyType = transferPolicy?.get("type") as? String ?: "OPEN"
                @Suppress("UNCHECKED_CAST")
                val whitelist = transferPolicy?.get("whitelist") as? List<String>
                val whitelistJson = if (whitelist != null && whitelist.isNotEmpty()) {
                    "[${whitelist.joinToString(",") { "\"$it\"" }}]"
                } else {
                    "[]"
                }
                val domainCode = transferPolicy?.get("domainCode") as? String
                jdbc.update(
                    """INSERT INTO rm_transfer_policies (tenant_id, policy_type, whitelist, domain_code)
                       VALUES (?, ?, ?::jsonb, ?)
                       ON CONFLICT (tenant_id) DO UPDATE SET
                           policy_type = EXCLUDED.policy_type,
                           whitelist = EXCLUDED.whitelist,
                           domain_code = EXCLUDED.domain_code""",
                    tenantId, policyType, whitelistJson, domainCode
                )
                log.info("Projected BankTenantRegistered into rm_transfer_policies for tenant {}", tenantId)
            }

            "TransferPolicyUpdated" -> {
                val tenantId = p["tenantId"] as String
                val policyType = p["policyType"] as String
                @Suppress("UNCHECKED_CAST")
                val whitelist = p["whitelist"] as? List<String>
                val whitelistJson = if (whitelist != null && whitelist.isNotEmpty()) {
                    "[${whitelist.joinToString(",") { "\"$it\"" }}]"
                } else {
                    "[]"
                }
                val domainCode = p["domainCode"] as? String
                jdbc.update(
                    """INSERT INTO rm_transfer_policies (tenant_id, policy_type, whitelist, domain_code)
                       VALUES (?, ?, ?::jsonb, ?)
                       ON CONFLICT (tenant_id) DO UPDATE SET
                           policy_type = EXCLUDED.policy_type,
                           whitelist = EXCLUDED.whitelist,
                           domain_code = EXCLUDED.domain_code""",
                    tenantId, policyType, whitelistJson, domainCode
                )
                log.info("Projected TransferPolicyUpdated into rm_transfer_policies for tenant {}", tenantId)
            }

            else -> { /* ignore other events */ }
        }
    }
}
