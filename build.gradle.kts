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
    implementation("org.luaj:luaj-jse:3.0.1")

    // Networking (UDP transport)
    implementation(libs.netty.transport)
    implementation(libs.netty.buffer)

    // Steam (optional at runtime: PlatformServices falls back to a no-op when absent)
    implementation(libs.steamworks4j)
    // MP3 decoding (MIT, pure Java, MPEG-1 Layer III), see engine/audio/Mp3Decoder.kt
    implementation(libs.javamp3)

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
// Runs the standalone game (GameMain) on an exported pack (GPL-18). From classes there is no jar folder to find
// game.bin in, so the pack is explicit: gradlew runGame -PskateBin=<project>/builds/game.bin (or SKATE_BIN=<path>).
tasks.register<JavaExec>("runGame") {
    group = "skate"
    description = "Runs the exported game: -PskateBin=<path to game.bin> (File > Export Project writes it to <project>/builds)."
    mainClass.set("com.pafoid.skate.game.GameMainKt")
    classpath = sourceSets.main.get().runtimeClasspath
    val bin = providers.gradleProperty("skateBin").orElse(providers.environmentVariable("SKATE_BIN"))
    val root = layout.projectDirectory
    doFirst {
        val path = bin.orNull ?: throw GradleException(
            "runGame needs an exported pack. Export the project (File > Export Project), then run " +
                "gradlew runGame -PskateBin=<project>/builds/game.bin, or set SKATE_BIN."
        )
        val pack = File(path).let { if (it.isAbsolute) it else root.file(path).asFile }
        if (!pack.isFile) throw GradleException("runGame: no game.bin at ${pack.absolutePath}. Export the project first.")
        systemProperty("skate.bin", pack.absolutePath)
        // game.settings.json is written next to the pack, like in an exported build.
        workingDir = pack.parentFile
    }
}

tasks.register<JavaExec>("completeScript") {
    group = "skate"
    description = "Prints script completions: --args=<file>@<line>:<col>"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.pafoid.skate.engine.scripting.completion.CompleteScriptCliKt")
}
