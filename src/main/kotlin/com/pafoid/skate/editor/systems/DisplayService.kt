package com.pafoid.skate.editor.systems

import com.pafoid.skate.editor.data.MonitorInfo
import com.pafoid.skate.editor.data.VideoModeInfo
import org.lwjgl.glfw.GLFW.glfwGetMonitorName
import org.lwjgl.glfw.GLFW.glfwGetMonitors
import org.lwjgl.glfw.GLFW.glfwGetVideoModes

class DisplayService {

    /**
     * Returns a list of all available monitors.
     */
    fun getAvailableMonitors(): List<MonitorInfo> {
        val monitors = glfwGetMonitors() ?: return emptyList()
        val result = mutableListOf<MonitorInfo>()
        for (i in 0 until monitors.capacity()) {
            val handle = monitors.get(i)
            val name = glfwGetMonitorName(handle) ?: "Monitor $i"
            result.add(MonitorInfo(i, name, handle))
        }
        return result
    }

    /**
     * Returns all available video modes for a specific monitor.
     */
    fun getAvailableVideoModes(monitorIndex: Int): List<VideoModeInfo> {
        val monitors = glfwGetMonitors() ?: return emptyList()
        if (monitorIndex < 0 || monitorIndex >= monitors.capacity()) return emptyList()

        val monitor = monitors.get(monitorIndex)
        val modes = glfwGetVideoModes(monitor) ?: return emptyList()

        val result = mutableListOf<VideoModeInfo>()
        for (i in 0 until modes.capacity()) {
            val mode = modes.get(i)
            result.add(VideoModeInfo(mode.width(), mode.height(), mode.refreshRate()))
        }

        // Sort by width, then height, then refresh rate descending
        return result.distinct().sortedWith(compareByDescending<VideoModeInfo> { it.width }
            .thenByDescending { it.height }
            .thenByDescending { it.refreshRate })
    }
}
