rootProject.name = "kodabank"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

include(
    ":shared:kodastore-client",
    ":shared:domain-primitives",
    ":shared:auth",
    ":kodabank-core",
    ":kodabank-clearing",
    ":kodabank-payment-gateway",
    ":kodabank-bff",
    ":kodabank-admin",
    ":kodabank-cli"
)
