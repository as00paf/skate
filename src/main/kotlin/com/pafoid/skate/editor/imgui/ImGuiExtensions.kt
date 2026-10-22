package com.pafoid.skate.editor.imgui

import imgui.ImFontConfig
import imgui.ImGuiIO
import imgui.ImGuiStyle
import org.joml.Vector4f

fun ImGuiIO.loadFonts(fontsFile:String) {
    val fontAtlas = fonts
    val fontConfig = ImFontConfig()

    fontAtlas.addFontDefault()

    fontConfig.mergeMode = true
    fontConfig.pixelSnapH = true
    fontConfig.glyphMinAdvanceX = 14f

    val iconRanges = shortArrayOf(0xe000.toShort(), 0xf8ff.toShort(), 0)
    fontAtlas.addFontFromFileTTF(fontsFile, 14f, fontConfig, iconRanges)

    fontAtlas.build()
    fontConfig.destroy()
}

fun ImGuiIO.loadFonts(fontsByteArray: ByteArray) {
    val fontAtlas = fonts
    val fontConfig = ImFontConfig()

    fontAtlas.addFontDefault()

    fontConfig.mergeMode = true
    fontConfig.pixelSnapH = true
    fontConfig.glyphMinAdvanceX = 14f

    val iconRanges = shortArrayOf(0xe000.toShort(), 0xf8ff.toShort(), 0)
    fontAtlas.addFontFromMemoryTTF(fontsByteArray, 14f, fontConfig, iconRanges)

    fontAtlas.build()
    fontConfig.destroy()
}

fun ImGuiStyle.setColor(imGuiColor: Int, color: Vector4f) {
    setColor(imGuiColor, color.x, color.y, color.z, color.w)
}

/**
 * Explicit, non-empty id for item popups. Passing no id makes ImGui use the last item's id, which is 0
 * (assert `id != 0`) when the last item has no id, e.g. after a separator or when a docked window is clipped.
 */
fun contextPopupId(scope: String, key: Any? = null): String =
    if (key == null) "##ctx_$scope" else "##ctx_${scope}_$key"
