package com.pafoid.skate.engine.editor

import imgui.ImGui
import java.lang.management.ManagementFactory
import java.lang.management.ThreadMXBean

class ProfilerWindow {
    private val threadBean: ThreadMXBean = ManagementFactory.getThreadMXBean()
    private val threadIds = mutableMapOf<String, Long>()
    private val threadCpuUsage = mutableMapOf<String, Float>()
    private val lastCpuTime = mutableMapOf<String, Long>()
    private var lastSampleTime = System.nanoTime()

    init {
        if (threadBean.isThreadCpuTimeSupported) {
            threadBean.isThreadCpuTimeEnabled = true
        }
    }

    fun imgui() {
        ImGui.begin("Profiler")

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
