package com.pafoid.skate.editor.data

/**
 * Categorized file types for icon display and filtering.
 */
enum class FileType(val extensions: List<String>) {
    FOLDER(listOf()),
    PROJECT_FILE(listOf("skateproject")),
    SCENE(listOf("scene")),
    SCRIPT_KOTLIN(listOf("kt")),
    SCRIPT_JAVA(listOf("java")),
    TEXTURE(listOf("jpeg", "png", "tga", "bmp", "psd", "gif", "hdr", "pic", "pnm")),
    MODEL_3D(listOf("obj", "glb", "dae", "gltf")),
    ANIMATION(listOf("fbx")),
    SOUND(listOf("wav", "ogg", "mp3")),
    PREFAB(listOf()),
    JSON(listOf("json")),
    CONFIG(listOf("config")),
    SHADER(listOf("glsl")),
    MATERIAL(listOf("mat", "material")),
    TEXT(listOf("txt", "properties")),
    FONT(listOf("ttf", "otf")),
    UNKNOWN(listOf());
}

val FileType.SUPPORTED by lazy { FileType.entries.flatMap { it.extensions } }