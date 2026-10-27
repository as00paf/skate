plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

application {
    mainClass.set("com.pafoid.skate.mcp.SkateMcpKt")
    applicationName = "skate-mcp"
}

// Engine resources and bundled assets are resolved relative to the repository root
tasks.named<JavaExec>("run") { workingDir = rootProject.projectDir }

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":"))
    implementation(project(":tools:cli"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.joml)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "1G"
}

kotlin {
    jvmToolchain(17)
}
