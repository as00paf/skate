plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
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

    // Reflection
    implementation(kotlin("reflect"))

    // Koin
    implementation(libs.koin.core)
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit5)

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
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<Test> {
    jvmArgs("-Xverify:none")
}