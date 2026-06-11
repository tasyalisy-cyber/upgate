plugins {
    kotlin("jvm") version "2.0.21"
}

group = "qa.upgate"
version = "1.0.0"

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.11.4")
    testImplementation("org.assertj:assertj-core:3.26.3")
}

val configuredBaseUrl = providers.systemProperty("baseUrl")
    .orElse(providers.environmentVariable("BASE_URL"))

tasks.test {
    useJUnitPlatform()

    configuredBaseUrl.orNull?.let { systemProperty("baseUrl", it) }

    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
