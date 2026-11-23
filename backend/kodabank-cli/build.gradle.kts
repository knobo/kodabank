plugins {
    application
    alias(libs.plugins.spring.dependency.management)
}

// versions for the version-less jackson catalog entries
dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}")
    }
}

application {
    mainClass.set("no.kodabank.cli.MainKt")
}

dependencies {
    implementation(project(":shared:domain-primitives"))
    implementation(libs.clikt)
    implementation(libs.okhttp)
    implementation(libs.jackson.kotlin)
    implementation(libs.jackson.yaml)
    implementation(libs.jackson.jsr310)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
}
