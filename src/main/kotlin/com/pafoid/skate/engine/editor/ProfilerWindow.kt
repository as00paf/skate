package com.pafoid.skate.engine.editor

import com.pafoid.skate.engine.utils.EngineStats
import imgui.ImGui
import java.lang.management.ManagementFactory
import java.lang.management.ThreadMXBean

class ProfilerWindow {
    private val threadBean: ThreadMXBean = ManagementFactory.getThreadMXBean()
    private val threadIds = mutableMapOf<String, Long>()
    private val threadCpuUsage = mutableMapOf<String, Float>()
    private val lastCpuTime = mutableMapOf<String, Long>()
    private var lastSampleTime = System.nanoTime()

    private val showMemoryStats = imgui.type.ImBoolean(true)

    init {
        if (threadBean.isThreadCpuTimeSupported) {
            threadBean.isThreadCpuTimeEnabled = true
        }
    }

    fun imgui(pOpen: imgui.type.ImBoolean? = null) {
        if (pOpen != null) {
            if (!ImGui.begin("Profiler", pOpen)) {
                ImGui.end()
                return
            }
        } else {
            ImGui.begin("Profiler")
        }

        val io = ImGui.getIO()
        ImGui.text("Application average %.3f ms/frame (%.1f FPS)".format(1000.0f / io.framerate, io.framerate))

        val runtime = Runtime.getRuntime()
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
        val totalMemory = runtime.totalMemory() / 1024 / 1024
        ImGui.text("Draw Calls: %d".format(EngineStats.drawCalls.get()))
        ImGui.text("Physics Step: %.3f ms".format(EngineStats.physicsStepTime.get().toFloat() / 1_000_000f))
        ImGui.text("RAM Usage: %d MB / %d MB".format(usedMemory, totalMemory))

        ImGui.separator()
        ImGui.checkbox("Show Memory Stats", showMemoryStats)
        if (showMemoryStats.get()) {
            ImGui.progressBar(usedMemory.toFloat() / totalMemory.toFloat(), -1f, 0f, "%d%%".format((usedMemory.toFloat() / totalMemory.toFloat() * 100).toInt()))
        }

        ImGui.separator()

        if (!threadBean.isThreadCpuTimeSupported) {
            ImGui.text("Thread CPU time not supported on this JVM")
            ImGui.end()
            return
        }

        val currentTime = System.nanoTime()
        val deltaTime = (currentTime - lastSampleTime).toFloat() / 1_000_000_000f

        if (deltaTime > 0.5f) { // Sample every 500ms
            updateStats(currentTime)
            lastSampleTime = currentTime
        }

        ImGui.columns(3, "ThreadColumns")
        ImGui.text("Thread Name")
        ImGui.nextColumn()
        ImGui.text("CPU Usage")
        ImGui.nextColumn()
        ImGui.text("State")
        ImGui.nextColumn()
        ImGui.separator()

        threadCpuUsage.forEach { (name, usage) ->
            ImGui.text(name)
            ImGui.nextColumn()
            ImGui.progressBar(usage / 100f, -1f, 0f, String.format("%.2f%%", usage))
            ImGui.nextColumn()
            
            val id = threadIds[name]
            if (id != null) {
                val info = threadBean.getThreadInfo(id)
                ImGui.text(info?.threadState?.toString() ?: "UNKNOWN")
            } else {
                ImGui.text("N/A")
            }
            ImGui.nextColumn()
        }

        ImGui.columns(1)
        ImGui.end()
    }

    private fun updateStats(currentTime: Long) {
        val allThreads = threadBean.allThreadIds
        val infos = threadBean.getThreadInfo(allThreads)
        
        infos.forEach { info ->
            if (info == null) return@forEach
            val name = info.threadName
            val id = info.threadId
            
            // Filter for interesting threads
            if (name.contains("Main", true) || 
                name.contains("Physics", true) || 
                name.contains("DefaultDispatcher", true) ||
                name.contains("IO", true)) {
                
                threadIds[name] = id
                val cpuTime = threadBean.getThreadCpuTime(id)
                val prevCpuTime = lastCpuTime[name] ?: cpuTime
                
                val cpuDelta = cpuTime - prevCpuTime
                val timeDelta = currentTime - lastSampleTime
                
                if (timeDelta > 0) {
                    val usage = (cpuDelta.toFloat() / timeDelta.toFloat()) * 100f
                    threadCpuUsage[name] = usage
                }
                
                lastCpuTime[name] = cpuTime
            }
        }
    }
}
