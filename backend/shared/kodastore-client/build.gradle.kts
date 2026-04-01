plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}")
    }
}

dependencies {
    implementation(project(":shared:domain-primitives"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation(libs.jackson.kotlin)
    implementation(libs.jackson.jsr310)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
}
