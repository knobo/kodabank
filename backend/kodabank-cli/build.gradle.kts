plugins {
    application
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
