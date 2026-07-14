plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor.plugin)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.exposed.gradle)
}

group = "org.bscm"
version = "0.0.1"

application {
    mainClass.set("io.ktor.server.netty.EngineMain")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://packages.confluent.io/maven")
        name = "confluence"
    }
    maven { url = uri("https://jitpack.io") }
}

ktor {
    openApi {
        enabled = true
        codeInferenceEnabled = false
        onlyCommented = false
    }
}

val pgUrl = System.getenv("POSTGRES_URL")
val pgUser = System.getenv("POSTGRES_USER")
val pgPassword = System.getenv("POSTGRES_PASSWORD")

exposed {
    migrations {
        tablesPackage.set("org.bscm.models.tables")
        if (pgUrl != null && pgUser != null && pgPassword != null) {
            databaseUrl.set("jdbc:$pgUrl")
            databaseUser.set(pgUser)
            databasePassword.set(pgPassword)
        } else {
            testContainersImageName.set("postgres:latest")
        }
    }
}

dependencies {
    // Ktor (Client)
    implementation(libs.ktor.client.core.jvm)
    implementation(libs.ktor.client.apache.jvm)
    implementation(libs.ktor.serialization.kotlinx.json.jvm)

    // Koin (Dependency Injection)
    implementation(libs.koin.ktor)
    implementation(libs.koin.logger.slf4j)

    // Ktor (Server)
    implementation(libs.ktor.server.core.jvm)
    implementation(libs.ktor.server.netty.jvm)
    implementation(libs.ktor.server.host.common)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt.jvm)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.rate.limit)
    implementation(libs.ktor.server.config.yaml)
    implementation(libs.ktor.server.openapi)
    implementation(libs.ktor.server.swagger)
    implementation(libs.ktor.server.routing.openapi)

    testImplementation(libs.ktor.server.test.host.jvm)
    testImplementation(libs.kotlin.test.junit)

    // HTTP Client for OAuth
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)

    // Database
    implementation(libs.h2)
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.json)
    implementation(libs.exposed.java.time)
    implementation(libs.exposed.migration.core)
    implementation(libs.exposed.migration.jdbc)
    implementation(libs.postgresql)

    // Database Migration
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.database.postgresql)

    // Logging
    implementation(libs.logback.classic)

    // Kotlin
    implementation(libs.kotlinx.datetime)

    // Utils
    implementation(libs.bouncycastle) // For cryptographic operations (e.g., NanoId)

    // Decoding
    implementation(libs.unitykt)
    implementation(libs.compress)

    // Caching
    implementation(libs.lettuce.core)
}