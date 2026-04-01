plugins {
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":shared:kodastore-client"))
    implementation(project(":shared:domain-primitives"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation(libs.jackson.kotlin)
    implementation(libs.jackson.jsr310)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
