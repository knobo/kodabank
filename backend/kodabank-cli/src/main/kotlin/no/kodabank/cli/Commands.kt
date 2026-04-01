package no.kodabank.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import java.io.File

// -- Import commands --

class ImportGroup : CliktCommand(name = "import") {
    override fun help(context: com.github.ajalt.clikt.core.Context) = "Import bank definitions"
    override fun run() = Unit
}

class ImportBankCommand : CliktCommand(name = "bank") {
    override fun help(context: com.github.ajalt.clikt.core.Context) = "Import bank definition from YAML file or directory"
    private val path by argument()
    private val client by requireObject<AdminApiClient>()

    override fun run() {
        val file = File(path)
        if (!file.exists()) {
            echo("Error: Path does not exist: $path", err = true)
            throw SystemExitException(1)
        }

        val yamlFiles = if (file.isDirectory) {
            file.listFiles { f -> f.extension in listOf("yaml", "yml") }?.toList() ?: emptyList()
        } else {
            listOf(file)
        }

        if (yamlFiles.isEmpty()) {
            echo("No YAML files found at: $path", err = true)
            throw SystemExitException(1)
        }

        for (yamlFile in yamlFiles.sortedBy { it.name }) {
            echo("Importing ${yamlFile.name}...")
            val yamlContent = yamlFile.readText()
            val response = client.importYaml(yamlContent)
            if (response.success) {
                echo("  OK: Imported successfully (HTTP ${response.statusCode})")
            } else {
                echo("  FAILED: HTTP ${response.statusCode} - ${response.body}", err = true)
            }
        }
    }
}

// -- Tenants commands --

class TenantsGroup : CliktCommand(name = "tenants") {
    override fun help(context: com.github.ajalt.clikt.core.Context) = "Manage bank tenants"
    override fun run() = Unit
}

class TenantsListCommand : CliktCommand(name = "list") {
    override fun help(context: com.github.ajalt.clikt.core.Context) = "List all bank tenants"
    private val client by requireObject<AdminApiClient>()

    override fun run() {
        val response = client.listTenants()
        if (!response.success) {
            echo("Error: HTTP ${response.statusCode} - ${response.body}", err = true)
            throw SystemExitException(1)
        }

        val tenants = client.objectMapper.readValue(
            response.body,
            client.objectMapper.typeFactory.constructCollectionType(List::class.java, Map::class.java)
        ) as List<Map<String, Any?>>

        if (tenants.isEmpty()) {
            echo("No tenants found.")
            return
        }

        echo("%-15s  %-25s  %-6s  %-5s  %-5s".format("TENANT ID", "BANK NAME", "CODE", "CTRY", "CCY"))
        echo("-".repeat(65))
        for (tenant in tenants) {
            echo("%-15s  %-25s  %-6s  %-5s  %-5s".format(
                tenant["tenantId"] ?: "",
                tenant["bankName"] ?: "",
                tenant["bankCode"] ?: "",
                tenant["country"] ?: "",
                tenant["currency"] ?: ""
            ))
        }
    }
}

class TenantsStatusCommand : CliktCommand(name = "status") {
    override fun help(context: com.github.ajalt.clikt.core.Context) = "Show tenant status and details"
    private val tenantId by argument()
    private val client by requireObject<AdminApiClient>()

    override fun run() {
        val response = client.getTenant(tenantId)
        if (!response.success) {
            if (response.statusCode == 404) {
                echo("Tenant not found: $tenantId", err = true)
            } else {
                echo("Error: HTTP ${response.statusCode} - ${response.body}", err = true)
            }
            throw SystemExitException(1)
        }

        @Suppress("UNCHECKED_CAST")
        val tenant = client.objectMapper.readValue(response.body, Map::class.java) as Map<String, Any?>
        val branding = tenant["branding"] as? Map<String, Any?> ?: emptyMap()

        echo("Tenant: ${tenant["tenantId"]}")
        echo("  Bank Name:     ${tenant["bankName"]}")
        echo("  Bank Code:     ${tenant["bankCode"]}")
        echo("  IBAN Prefix:   ${tenant["ibanPrefix"]}")
        echo("  Country:       ${tenant["country"]}")
        echo("  Currency:      ${tenant["currency"]}")
        echo("  Version:       ${tenant["version"]}")
        echo("  Branding:")
        echo("    Primary:     ${branding["primaryColor"]}")
        echo("    Secondary:   ${branding["secondaryColor"]}")
        echo("    Logo:        ${branding["logo"]}")
        echo("    Tagline:     ${branding["tagline"]}")

        val nostro = tenant["nostroAccountId"]
        if (nostro != null) {
            echo("  Nostro Account: $nostro")
            echo("  Nostro Balance: ${tenant["nostroBalance"]}")
        }
    }
}

// -- Setup command --

class SetupCommand : CliktCommand(name = "setup") {
    override fun help(context: com.github.ajalt.clikt.core.Context) = "Set up the kodabank environment from config directory"
    private val configDir by option("--config").default("banks")
    private val demoData by option("--demo-data").flag()
    private val client by requireObject<AdminApiClient>()

    override fun run() {
        val dir = File(configDir)
        if (!dir.isDirectory) {
            echo("Error: Config directory does not exist: $configDir", err = true)
            throw SystemExitException(1)
        }

        val yamlFiles = dir.listFiles { f -> f.extension in listOf("yaml", "yml") }?.toList() ?: emptyList()
        if (yamlFiles.isEmpty()) {
            echo("No YAML files found in: $configDir", err = true)
            throw SystemExitException(1)
        }

        echo("Setting up kodabank from ${yamlFiles.size} bank definition(s)...")
        echo()

        for (yamlFile in yamlFiles.sortedBy { it.name }) {
            echo("Importing ${yamlFile.name}...")
            val yamlContent = yamlFile.readText()
            val response = client.importYaml(yamlContent)
            if (response.success) {
                echo("  OK")
            } else {
                echo("  FAILED: HTTP ${response.statusCode} - ${response.body}", err = true)
            }
        }

        if (demoData) {
            echo()
            echo("Generating demo data...")
            for (yamlFile in yamlFiles.sortedBy { it.name }) {
                val content = yamlFile.readText()
                val tenantIdMatch = Regex("^\\s*id:\\s*(\\S+)", RegexOption.MULTILINE).find(content)
                val tenantId = tenantIdMatch?.groupValues?.get(1) ?: continue

                echo("  Generating demo data for $tenantId...")
                val response = client.generateDemoData(tenantId)
                if (response.success) {
                    echo("    OK")
                } else {
                    echo("    FAILED: HTTP ${response.statusCode}", err = true)
                }
            }
        }

        echo()
        echo("Setup complete.")
    }
}

// -- Demo commands --

class DemoGroup : CliktCommand(name = "demo") {
    override fun help(context: com.github.ajalt.clikt.core.Context) = "Demo data management"
    override fun run() = Unit
}

class DemoGenerateCommand : CliktCommand(name = "generate") {
    override fun help(context: com.github.ajalt.clikt.core.Context) = "Generate demo data for a tenant"
    private val tenantId by option("--tenant").default("")
    private val customers by option("--customers").int().default(5)
    private val client by requireObject<AdminApiClient>()

    override fun run() {
        if (tenantId.isBlank()) {
            echo("Error: --tenant is required", err = true)
            throw SystemExitException(1)
        }

        echo("Generating demo data for tenant '$tenantId' ($customers customers)...")
        val response = client.generateDemoData(tenantId)
        if (response.success) {
            echo("Request accepted.")
            @Suppress("UNCHECKED_CAST")
            val result = client.objectMapper.readValue(response.body, Map::class.java) as Map<String, Any?>
            echo("Status: ${result["status"]}")
            echo("Message: ${result["message"]}")
        } else {
            echo("Error: HTTP ${response.statusCode} - ${response.body}", err = true)
            throw SystemExitException(1)
        }
    }
}

class SystemExitException(val code: Int) : RuntimeException()
