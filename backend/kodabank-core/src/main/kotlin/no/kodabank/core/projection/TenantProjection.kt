package no.kodabank.core.projection

import no.kodabank.shared.client.KodaStoreClient
import no.kodabank.shared.client.RecordedEvent
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class TenantProjection(
    jdbc: JdbcTemplate,
    store: KodaStoreClient
) : ProjectionWorker(jdbc, store) {

    private val log = LoggerFactory.getLogger(javaClass)

    override val projectionName = "tenant-projection"
    override val categories = listOf("BankTenant")

    @Scheduled(fixedDelay = 500)
    fun run() = poll()

    override fun handleEvent(event: RecordedEvent) {
        val p = event.payload
        when (event.eventType) {
            "BankTenantCreated" -> {
                val tenantId = p["tenantId"] as String
                val branding = p["branding"] as? Map<*, *>
                jdbc.update(
                    """INSERT INTO rm_tenants
                       (tenant_id, bank_name, bank_code, iban_prefix, country, currency,
                        primary_color, secondary_color, logo_url, tagline, status, created_at)
                       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?)
                       ON CONFLICT (tenant_id) DO NOTHING""",
                    tenantId,
                    p["bankName"] as String,
                    p["bankCode"] as String,
                    p["ibanPrefix"] as String,
                    p["country"] as? String ?: "NO",
                    p["currency"] as? String ?: "NOK",
                    branding?.get("primaryColor") as? String,
                    branding?.get("secondaryColor") as? String,
                    branding?.get("logo") as? String,
                    branding?.get("tagline") as? String,
                    toTimestamp(event.createdAt)
                )
                log.info("Projected BankTenantCreated for tenant {}", tenantId)
            }

            "BankTenantRegistered" -> {
                val tenantId = p["tenantId"] as String
                @Suppress("UNCHECKED_CAST")
                val accessPolicy = p["accessPolicy"] as? Map<String, Any?>
                @Suppress("UNCHECKED_CAST")
                val transferPolicy = p["transferPolicy"] as? Map<String, Any?>
                @Suppress("UNCHECKED_CAST")
                val whitelist = transferPolicy?.get("whitelist") as? List<String>
                val whitelistJson = if (whitelist != null && whitelist.isNotEmpty()) {
                    "[${whitelist.joinToString(",") { "\"$it\"" }}]"
                } else {
                    "[]"
                }
                jdbc.update(
                    """INSERT INTO rm_tenants
                       (tenant_id, bank_name, bank_code, iban_prefix, country, currency,
                        owner_user_id, access_policy_type, access_policy_webhook_url,
                        transfer_policy_type, transfer_policy_whitelist, transfer_policy_domain_code,
                        status, created_at)
                       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, 'PENDING', ?)
                       ON CONFLICT (tenant_id) DO NOTHING""",
                    tenantId,
                    p["bankName"] as String,
                    p["bankCode"] as String,
                    p["ibanPrefix"] as String,
                    p["country"] as? String ?: "NO",
                    p["currency"] as? String ?: "NOK",
                    p["ownerUserId"] as String,
                    accessPolicy?.get("type") as? String,
                    accessPolicy?.get("webhookUrl") as? String,
                    transferPolicy?.get("type") as? String,
                    whitelistJson,
                    transferPolicy?.get("domainCode") as? String,
                    toTimestamp(event.createdAt)
                )
                log.info("Projected BankTenantRegistered for tenant {}", tenantId)
            }

            "TransferPolicyUpdated" -> {
                val tenantId = p["tenantId"] as String
                @Suppress("UNCHECKED_CAST")
                val whitelist = p["whitelist"] as? List<String>
                val whitelistJson = if (whitelist != null && whitelist.isNotEmpty()) {
                    "[${whitelist.joinToString(",") { "\"$it\"" }}]"
                } else {
                    "[]"
                }
                jdbc.update(
                    """UPDATE rm_tenants SET
                       transfer_policy_type = ?,
                       transfer_policy_whitelist = ?::jsonb,
                       transfer_policy_domain_code = ?
                       WHERE tenant_id = ?""",
                    p["policyType"] as String,
                    whitelistJson,
                    p["domainCode"] as? String,
                    tenantId
                )
                log.info("Projected TransferPolicyUpdated for tenant {}", tenantId)
            }

            "AccessPolicyUpdated" -> {
                val tenantId = p["tenantId"] as String
                jdbc.update(
                    """UPDATE rm_tenants SET
                       access_policy_type = ?,
                       access_policy_webhook_url = ?
                       WHERE tenant_id = ?""",
                    p["policyType"] as String,
                    p["webhookUrl"] as? String,
                    tenantId
                )
                log.info("Projected AccessPolicyUpdated for tenant {}", tenantId)
            }

            "BankBrandingUpdated" -> {
                val tenantId = p["tenantId"] as String
                jdbc.update(
                    """UPDATE rm_tenants SET
                       primary_color = COALESCE(?, primary_color),
                       secondary_color = COALESCE(?, secondary_color),
                       logo_url = COALESCE(?, logo_url),
                       tagline = COALESCE(?, tagline)
                       WHERE tenant_id = ?""",
                    p["primaryColor"] as? String,
                    p["secondaryColor"] as? String,
                    p["logo"] as? String,
                    p["tagline"] as? String,
                    tenantId
                )
                log.info("Projected BankBrandingUpdated for tenant {}", tenantId)
            }

            else -> log.debug("Ignoring event type {} in TenantProjection", event.eventType)
        }
    }
}
