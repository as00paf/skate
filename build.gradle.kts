plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
    jacoco
}

application {
    mainClass.set("com.pafoid.skate.MainKt")
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    //LWJGL
    implementation(platform(libs.lwjgl.bom))

    implementation(libs.lwjgl)
    implementation(libs.lwjgl.assimp)
    implementation(libs.lwjgl.glfw)
    implementation(libs.lwjgl.openal)
    implementation(libs.lwjgl.opengl)
    implementation(libs.lwjgl.stb)
    implementation(libs.lwjgl.tinyfd)
    
    val lwjglNatives = "natives-windows"
    implementation(libs.lwjgl) { artifact { classifier = lwjglNatives } }
    implementation(libs.lwjgl.assimp) { artifact { classifier = lwjglNatives } }
    implementation(libs.lwjgl.glfw) { artifact { classifier = lwjglNatives } }
    implementation(libs.lwjgl.openal) { artifact { classifier = lwjglNatives } }
    implementation(libs.lwjgl.opengl) { artifact { classifier = lwjglNatives } }
    implementation(libs.lwjgl.stb) { artifact { classifier = lwjglNatives } }
    implementation(libs.lwjgl.tinyfd) { artifact { classifier = lwjglNatives } }
    
    // Bullet Physics
    implementation(libs.libbulletjme)
    runtimeOnly(libs.libbulletjme) { artifact { classifier = "SpDebug" } }

    //Jsnap loader for loading native libraries
    implementation(libs.snaploader)

    implementation(libs.joml)
    implementation(libs.joml.primitives)

    // IM GUI
    implementation(libs.imgui.binding)
    implementation(libs.imgui.lwjgl3)
    implementation(libs.imgui.natives.windows)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Kotlin Script (.kts) host
    val kotlinVersion = libs.versions.kotlin.get()
    implementation("org.jetbrains.kotlin:kotlin-scripting-common:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm-host:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-scripting-compiler-embeddable:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-scripting-dependencies:$kotlinVersion")

    // TypeScript host: GraalJS runs the scripts, the TypeScript compiler (webjar) transpiles them
    implementation("org.graalvm.polyglot:polyglot:24.1.1")
    implementation("org.graalvm.polyglot:js-community:24.1.1")
    implementation("org.webjars.npm:typescript:5.6.3")

    // Reflection
    implementation(kotlin("reflect"))

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "2G"
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
    finalizedBy(tasks.jacocoTestReport)
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.jacocoTestReport {
    executionData(tasks.test.get())
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<Test> {
    jvmArgs("-Xverify:none")
}

// To produce game jar
tasks.register<Jar>("gameJar") {
    archiveClassifier.set("game")

    // Include compiled output classes
    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    }) {
        // Exclude signature files from merged JARs to avoid security exceptions
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = "com.pafoid.skate.game.GameMainKt"
    }
}