package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.imgui.IWindow
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.render.EngineStats
import imgui.ImGui
import imgui.type.ImBoolean
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.lang.management.ManagementFactory
import java.lang.management.ThreadMXBean

class ProfilerWindow(private val stringManager: StringManager) : IWindow {
    private val threadBean: ThreadMXBean = ManagementFactory.getThreadMXBean()
    private val threadIds = mutableMapOf<String, Long>()
    private val threadCpuUsage = mutableMapOf<String, Float>()
    private val lastCpuTime = mutableMapOf<String, Long>()
    private var lastSampleTime = System.nanoTime()

    private val showMemoryStats = ImBoolean(true)
    private val showGraphs = ImBoolean(true)
    private val history = PerformanceHistory(200)

    private class PerformanceHistory(val maxSize: Int = 200) {
        val frameTimes = FloatArray(maxSize)
        val fpsValues = FloatArray(maxSize)
        val physicsStepTimes = FloatArray(maxSize)
        val memoryUsage = FloatArray(maxSize)

        var currentIndex = 0
        var currentSize = 0

        fun addFrame(frameTime: Float, fps: Float, physicsStep: Float, memory: Float) {
            frameTimes[currentIndex] = frameTime
            fpsValues[currentIndex] = fps
            physicsStepTimes[currentIndex] = physicsStep
            memoryUsage[currentIndex] = memory

            currentIndex = (currentIndex + 1) % maxSize
            if (currentSize < maxSize) {
                currentSize++
            }
        }
    }

    init {
        if (threadBean.isThreadCpuTimeSupported) {
            threadBean.isThreadCpuTimeEnabled = true
        }
    }

    override fun imgui(pOpen: ImBoolean?) {
        if (pOpen != null) {
            if (!ImGui.begin("${Icons.CHART_LINE} ${stringManager.getString("window.profiler")}", pOpen)) {
                ImGui.end()
                return
            }
        } else {
            ImGui.begin("${Icons.CHART_LINE} ${stringManager.getString("window.profiler")}")
        }

        val io = ImGui.getIO()
        val frameTime = 1000.0f / io.framerate
        val fps = io.framerate
        val physicsTime = EngineStats.physicsStepTime.get().toFloat() / 1_000_000f

        val runtime = Runtime.getRuntime()
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
        val totalMemory = runtime.totalMemory() / 1024 / 1024

        history.addFrame(frameTime, fps, physicsTime, usedMemory.toFloat())

        ImGui.text(stringManager.getString("lbl.profiler.avg_ms", frameTime, fps))

        ImGui.text(stringManager.getString("lbl.profiler.draw_calls", EngineStats.drawCalls.get()))
        ImGui.text(stringManager.getString("lbl.profiler.physics_step", physicsTime))
        ImGui.text(stringManager.getString("lbl.profiler.ram_usage", usedMemory, totalMemory))

        ImGui.separator()
        ImGui.checkbox(stringManager.getString("lbl.profiler.show_mem_stats"), showMemoryStats)
        if (showMemoryStats.get()) {
            ImGui.progressBar(usedMemory.toFloat() / totalMemory.toFloat(), -1f, 0f, "%d%%".format((usedMemory.toFloat() / totalMemory.toFloat() * 100).toInt()))
        }

        ImGui.separator()
        ImGui.text("${Icons.CHART_LINE} ${stringManager.getString("window.profiler.graphs")}")
        ImGui.checkbox(stringManager.getString("lbl.profiler.show_graphs"), showGraphs)
        if (showGraphs.get()) {
            val lastIdx = if (history.currentIndex == 0) history.maxSize - 1 else history.currentIndex - 1
            
            // Frame Time Graph
            ImGui.plotLines(
                "${Icons.CLOCK} ${stringManager.getString("lbl.profiler.frame_time_graph")}",
                history.frameTimes,
                history.currentSize,
                history.currentIndex,
                String.format("%.2f ms", history.frameTimes[lastIdx]),
                0f, 100f, 0f, 80f
            )

            // FPS Graph
            ImGui.plotLines(
                "${Icons.CHART_LINE} ${stringManager.getString("lbl.profiler.fps_graph")}",
                history.fpsValues,
                history.currentSize,
                history.currentIndex,
                String.format("%.0f FPS", history.fpsValues[lastIdx]),
                0f, 240f, 0f, 80f
            )

            // Physics Step Time Graph
            ImGui.plotLines(
                "${Icons.MICROCHIP} ${stringManager.getString("lbl.profiler.physics_graph")}",
                history.physicsStepTimes,
                history.currentSize,
                history.currentIndex,
                String.format("%.2f ms", history.physicsStepTimes[lastIdx]),
                0f, 33f, 0f, 80f
            )

            // Memory Usage Graph
            ImGui.plotLines(
                "${Icons.MEMORY} ${stringManager.getString("lbl.profiler.memory_graph")}",
                history.memoryUsage,
                history.currentSize,
                history.currentIndex,
                String.format("%.0f MB", history.memoryUsage[lastIdx]),
                0f, totalMemory.toFloat(), 0f, 80f
            )
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
