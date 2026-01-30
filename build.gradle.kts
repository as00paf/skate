plugins {
    kotlin("jvm") version "2.0.21"
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
    implementation(platform("org.lwjgl:lwjgl-bom:3.3.6"))

    implementation("org.lwjgl", "lwjgl")
    implementation("org.lwjgl", "lwjgl-assimp")
    implementation("org.lwjgl", "lwjgl-glfw")
    implementation("org.lwjgl", "lwjgl-openal")
    implementation("org.lwjgl", "lwjgl-opengl")
    implementation("org.lwjgl", "lwjgl-stb")
    implementation("org.lwjgl", "lwjgl-tinyfd")
    implementation ("org.lwjgl", "lwjgl", classifier = "natives-windows")
    implementation ("org.lwjgl", "lwjgl-assimp", classifier = "natives-windows")
    implementation ("org.lwjgl", "lwjgl-glfw", classifier = "natives-windows")
    implementation ("org.lwjgl", "lwjgl-openal", classifier = "natives-windows")
    implementation ("org.lwjgl", "lwjgl-opengl", classifier = "natives-windows")
    implementation ("org.lwjgl", "lwjgl-stb", classifier = "natives-windows")
    implementation ("org.lwjgl", "lwjgl-tinyfd", classifier = "natives-windows")
    
    // Bullet Physics
    // JVM library:
    implementation("com.github.stephengold:Libbulletjme-Windows64:22.0.3")

    // native libraries:
    runtimeOnly("com.github.stephengold:Libbulletjme-Windows64:22.0.3:SpDebug")
    // Native libraries for other platforms could be added.

    //Jsnap loader for loading native libraries
    implementation("io.github.electrostat-lab:snaploader:1.0.0-stable")

    implementation("org.joml", "joml", "1.10.8")
    implementation("org.joml", "joml-primitives", "1.10.0")

    // IM GUI
    implementation("io.github.spair:imgui-java-binding:1.90.0")
    implementation("io.github.spair:imgui-java-lwjgl3:1.90.0")
    implementation("io.github.spair:imgui-java-natives-windows:1.90.0")

    // GSON
    implementation("com.google.code.gson:gson:2.8.9")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("io.mockk:mockk:1.13.12")
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