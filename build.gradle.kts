plugins {
    kotlin("jvm") version "2.3.0"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    //LWJGL
    implementation(platform("org.lwjgl:lwjgl-bom:3.4.0"))

    implementation("org.lwjgl", "lwjgl")
    implementation("org.lwjgl", "lwjgl-assimp")
    implementation("org.lwjgl", "lwjgl-glfw")
    implementation("org.lwjgl", "lwjgl-openal")
    implementation("org.lwjgl", "lwjgl-opengl")
    implementation("org.lwjgl", "lwjgl-stb")
    implementation ("org.lwjgl", "lwjgl", classifier = "natives-windows")
    implementation ("org.lwjgl", "lwjgl-assimp", classifier = "natives-windows")
    implementation ("org.lwjgl", "lwjgl-glfw", classifier = "natives-windows")
    implementation ("org.lwjgl", "lwjgl-openal", classifier = "natives-windows")
    implementation ("org.lwjgl", "lwjgl-opengl", classifier = "natives-windows")
    implementation ("org.lwjgl", "lwjgl-stb", classifier = "natives-windows")
    implementation("org.joml", "joml", "1.10.8")
    implementation("org.joml", "joml-primitives", "1.10.0")
    //implementation("org.lwjglx", "lwjgl3-awt", "0.2.3")

    // IM GUI
    implementation("io.github.spair:imgui-java-binding:1.90.0")
    implementation("io.github.spair:imgui-java-lwjgl3:1.90.0")
    implementation("io.github.spair:imgui-java-natives-windows:1.90.0")

    // GSON
    implementation("com.google.code.gson:gson:2.8.9")

    // JBox2D
    implementation("org.jbox2d:jbox2d-library:2.2.1.1")
}

kotlin {
    jvmToolchain(17)
}
