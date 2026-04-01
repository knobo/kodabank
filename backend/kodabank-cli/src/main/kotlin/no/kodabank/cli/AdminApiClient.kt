package no.kodabank.cli

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * HTTP client that calls kodabank-admin REST API.
 * Base URL configurable via KODABANK_ADMIN_URL env var or default http://localhost:8084
 */
class AdminApiClient(
    baseUrl: String = System.getenv("KODABANK_ADMIN_URL") ?: "http://localhost:8084"
) {
    private val baseUrl = baseUrl.trimEnd('/')

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val objectMapper: ObjectMapper = ObjectMapper()
        .registerKotlinModule()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val yamlMediaType = "text/yaml; charset=utf-8".toMediaType()

    fun createTenant(jsonBody: String): ApiResponse {
        val request = Request.Builder()
            .url("$baseUrl/api/tenants")
            .post(jsonBody.toRequestBody(jsonMediaType))
            .build()
        return execute(request)
    }

    fun importYaml(yamlContent: String): ApiResponse {
        val request = Request.Builder()
            .url("$baseUrl/api/tenants/import")
            .post(yamlContent.toRequestBody(yamlMediaType))
            .build()
        return execute(request)
    }

    fun listTenants(): ApiResponse {
        val request = Request.Builder()
            .url("$baseUrl/api/tenants")
            .get()
            .build()
        return execute(request)
    }

    fun getTenant(tenantId: String): ApiResponse {
        val request = Request.Builder()
            .url("$baseUrl/api/tenants/$tenantId")
            .get()
            .build()
        return execute(request)
    }

    fun updateBranding(tenantId: String, jsonBody: String): ApiResponse {
        val request = Request.Builder()
            .url("$baseUrl/api/tenants/$tenantId/branding")
            .put(jsonBody.toRequestBody(jsonMediaType))
            .build()
        return execute(request)
    }

    fun addProduct(tenantId: String, jsonBody: String): ApiResponse {
        val request = Request.Builder()
            .url("$baseUrl/api/tenants/$tenantId/products")
            .post(jsonBody.toRequestBody(jsonMediaType))
            .build()
        return execute(request)
    }

    fun generateDemoData(tenantId: String): ApiResponse {
        val request = Request.Builder()
            .url("$baseUrl/api/tenants/$tenantId/demo-data")
            .post("{}".toRequestBody(jsonMediaType))
            .build()
        return execute(request)
    }

    private fun execute(request: Request): ApiResponse {
        return try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                ApiResponse(
                    statusCode = response.code,
                    body = body,
                    success = response.isSuccessful
                )
            }
        } catch (e: Exception) {
            ApiResponse(
                statusCode = 0,
                body = "Connection failed: ${e.message}",
                success = false
            )
        }
    }
}

data class ApiResponse(
    val statusCode: Int,
    val body: String,
    val success: Boolean
)
