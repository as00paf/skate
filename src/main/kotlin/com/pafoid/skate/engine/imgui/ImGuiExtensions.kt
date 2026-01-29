package com.pafoid.skate.engine.imgui

import imgui.ImFontConfig
import imgui.ImGuiIO
import imgui.ImGuiStyle
import org.joml.Vector4f

fun ImGuiIO.loadFonts(fontsFile:String) {
    // Load Fonts
    val fontAtlas = fonts
    val fontConfig = ImFontConfig()

    // Default Font
    fontAtlas.addFontDefault()

    // Merge FontAwesome
    fontConfig.mergeMode = true
    fontConfig.pixelSnapH = true
    fontConfig.glyphMinAdvanceX = 14f // Use size of font

    val iconRanges = shortArrayOf(0xe000.toShort(), 0xf8ff.toShort(), 0)
    fontAtlas.addFontFromFileTTF(fontsFile, 14f, fontConfig, iconRanges)

    fontAtlas.build()
    fontConfig.destroy()
}

fun ImGuiStyle.setColor(imGuiColor: Int, color: Vector4f) {
    setColor(imGuiColor, color.x, color.y, color.z, color.w)
}