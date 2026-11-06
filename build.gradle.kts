import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile

buildscript {
    repositories { mavenCentral() }
    // gameJar copies the cached dependency entries without recompressing them (addRawArchiveEntry)
    dependencies { classpath("org.apache.commons:commons-compress:1.26.2") }
}

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
    // REN-13 Vulkan spike: Vulkan needs no native jar on Windows (it loads the system vulkan-1.dll)
    implementation(libs.lwjgl.vulkan)
    implementation(libs.lwjgl.shaderc)
    implementation(libs.lwjgl.shaderc) { artifact { classifier = lwjglNatives } }
    
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
    // TLG-10 end-to-end check (tools/scripts/verify-game-jar.sh): token of the probe class built into the game jar
    providers.gradleProperty("skate.gameJarToken").orNull?.let { systemProperty("skate.gameJarToken", it) }
    jvmArgs("-Xverify:none")
}

// Standalone game jar (build/libs/skate-game.jar), copied by the editor's export (ProjectExporter).
// Built with the editor (run, assemble/build, and IntelliJ's PAFSK8 run configuration) so exports never ship stale
// game code (TLG-10). Two steps keep it cheap: the dependencies are packed once into gameLibsJar (redone only when
// the runtime classpath changes), then gameJar compresses just the engine/game classes and copies the dependency
// entries raw. Same single fat jar as before, with the main classes first (they win over duplicate dependency entries).
val gameLibsJar = tasks.register<Jar>("gameLibsJar") {
    description = "Runtime dependencies of the standalone game, merged once (input of gameJar)."
    archiveBaseName.set("skate-game-libs")
    destinationDirectory.set(layout.buildDirectory.dir("tmp/gameLibs"))
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
}

abstract class GameJarTask : DefaultTask() {
    /** Compiled main classes and resources (directories). */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classes: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val libsJar: RegularFileProperty

    @get:Input
    abstract val mainClass: Property<String>

    @get:OutputFile
    abstract val archiveFile: RegularFileProperty

    @TaskAction
    fun pack() {
        val out = archiveFile.get().asFile
        out.parentFile.mkdirs()
        out.delete()
        val seen = HashSet<String>()
        ZipArchiveOutputStream(out).use { zip ->
            fun put(name: String, bytes: ByteArray?) {
                if (!seen.add(name)) return
                zip.putArchiveEntry(ZipArchiveEntry(name))
                bytes?.let { zip.write(it) }
                zip.closeArchiveEntry()
            }
            put("META-INF/", null)
            put("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\nMain-Class: ${mainClass.get()}\r\n\r\n".toByteArray())
            classes.files.filter { it.isDirectory }.forEach { root ->
                root.walkTopDown().filter { it != root }.forEach { f ->
                    val rel = f.relativeTo(root).invariantSeparatorsPath
                    if (f.isDirectory) put("$rel/", null) else put(rel, f.readBytes())
                }
            }
            @Suppress("DEPRECATION")
            ZipFile(libsJar.get().asFile).use { libs ->
                libs.entriesInPhysicalOrder.asSequence()
                    .filter { seen.add(it.name) }
                    .forEach { zip.addRawArchiveEntry(it, libs.getRawInputStream(it)) }
            }
        }
    }
}

val gameJar = tasks.register<GameJarTask>("gameJar") {
    group = "skate"
    description = "Builds build/libs/skate-game.jar, the standalone game the editor exports."
    classes.from(sourceSets.main.get().output)
    libsJar.set(gameLibsJar.flatMap { it.archiveFile })
    mainClass.set("com.pafoid.skate.game.GameMainKt")
    archiveFile.set(layout.buildDirectory.file("libs/skate-game.jar"))
}

// Build the game jar whenever the editor is built or run from Gradle (IntelliJ: see .idea/runConfigurations/PAFSK8.xml).
tasks.named("run") { dependsOn(gameJar) }
tasks.named("assemble") { dependsOn(gameJar) }

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
