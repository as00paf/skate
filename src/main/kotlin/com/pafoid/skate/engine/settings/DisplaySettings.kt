package com.pafoid.skate.engine.settings

import kotlinx.serialization.Serializable

@Serializable
enum class WindowMode {
    WINDOWED,
    BORDERLESS,
    FULLSCREEN
}

@Serializable
data class DisplaySettings(
    var width: Int = 1920,
    var height: Int = 1080,
    var refreshRate: Int = 60,
    var monitorIndex: Int = 0,
    var vsync: Boolean = true,
    var windowMode: WindowMode = WindowMode.WINDOWED,
    var msaaSamples: Int = 4
) {
    fun validate() {
        if (width <= 0) width = 1920
        if (height <= 0) height = 1080
        if (refreshRate <= 0) refreshRate = 60
        if (monitorIndex < 0) monitorIndex = 0
        if (msaaSamples < 0) msaaSamples = 0
    }
}
