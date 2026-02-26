package com.pafoid.skate.editor.windows

import com.pafoid.skate.editor.imgui.IWindow
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.render.EngineStats
import imgui.ImGui
import imgui.type.ImBoolean
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.lang.management.ManagementFactory
import java.lang.management.ThreadMXBean

class ProfilerWindow : IWindow, KoinComponent {
    private val stringManager: StringManager by inject()
    private val threadBean: ThreadMXBean = ManagementFactory.getThreadMXBean()
    private val threadIds = mutableMapOf<String, Long>()
    private val threadCpuUsage = mutableMapOf<String, Float>()
    private val lastCpuTime = mutableMapOf<String, Long>()
    private var lastSampleTime = System.nanoTime()

    private val showMemoryStats = ImBoolean(true)

    init {
        if (threadBean.isThreadCpuTimeSupported) {
            threadBean.isThreadCpuTimeEnabled = true
        }
    }

    override fun imgui(pOpen: ImBoolean?) {
        if (pOpen != null) {
            if (!ImGui.begin(stringManager.getString("window.profiler"), pOpen)) {
                ImGui.end()
                return
            }
        } else {
            ImGui.begin(stringManager.getString("window.profiler"))
        }

        val io = ImGui.getIO()
        ImGui.text(stringManager.getString("lbl.profiler.avg_ms", 1000.0f / io.framerate, io.framerate))

        val runtime = Runtime.getRuntime()
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
        val totalMemory = runtime.totalMemory() / 1024 / 1024
        ImGui.text(stringManager.getString("lbl.profiler.draw_calls", EngineStats.drawCalls.get()))
        ImGui.text(stringManager.getString("lbl.profiler.physics_step", EngineStats.physicsStepTime.get().toFloat() / 1_000_000f))
        ImGui.text(stringManager.getString("lbl.profiler.ram_usage", usedMemory, totalMemory))

        ImGui.separator()
        ImGui.checkbox(stringManager.getString("lbl.profiler.show_mem_stats"), showMemoryStats)
        if (showMemoryStats.get()) {
            ImGui.progressBar(usedMemory.toFloat() / totalMemory.toFloat(), -1f, 0f, "%d%%".format((usedMemory.toFloat() / totalMemory.toFloat() * 100).toInt()))
        }

        ImGui.separator()

        if (!threadBean.isThreadCpuTimeSupported) {
            ImGui.text(stringManager.getString("lbl.profiler.thread_cpu_unsupported"))
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
        ImGui.text(stringManager.getString("lbl.profiler.thread_name"))
        ImGui.nextColumn()
        ImGui.text(stringManager.getString("lbl.profiler.cpu_usage"))
        ImGui.nextColumn()
        ImGui.text(stringManager.getString("lbl.profiler.state"))
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
                ImGui.text(stringManager.getString("lbl.na"))
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
