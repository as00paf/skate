package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.imgui.Tooltips
import com.pafoid.skate.editor.imgui.EditorWindow
import com.pafoid.skate.editor.imgui.EmptyState
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.render.EngineStats
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.type.ImBoolean
import java.lang.management.ManagementFactory
import java.lang.management.ThreadMXBean

class ProfilerWindow(private val stringManager: StringManager) : EditorWindow("window.profiler", true) {
    private val threadBean: ThreadMXBean = ManagementFactory.getThreadMXBean()
    private val threadIds = mutableMapOf<String, Long>()
    private val threadCpuUsage = mutableMapOf<String, Float>()
    private val lastCpuTime = mutableMapOf<String, Long>()
    private var lastSampleTime = System.nanoTime()

    private val showMemoryStats = ImBoolean(true)
    private val showGraphs = ImBoolean(true)
    private val isFrozen = ImBoolean(false)
    private val history = PerformanceHistory(200)

    override val icon = Icons.CHART_LINE
    override val relatedWindows = listOf("window.console", "window.render_graph")

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

        fun reset() {
            frameTimes.fill(0f)
            fpsValues.fill(0f)
            physicsStepTimes.fill(0f)
            memoryUsage.fill(0f)
            currentIndex = 0
            currentSize = 0
        }
    }

    init {
        if (threadBean.isThreadCpuTimeSupported) {
            threadBean.isThreadCpuTimeEnabled = true
        }
    }

    override fun imgui() {
        if (!beginWindow(stringManager)) {
            ImGui.end()
            return
        }

        val io = ImGui.getIO()
        val frameTime = 1000.0f / io.framerate
        val fps = io.framerate
        val physicsTime = EngineStats.physicsStepTime.get().toFloat() / 1_000_000f

        val runtime = Runtime.getRuntime()
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
        val totalMemory = runtime.totalMemory() / 1024 / 1024

        // Only update history when not frozen
        if (!isFrozen.get()) {
            history.addFrame(frameTime, fps, physicsTime, usedMemory.toFloat())
        }

        // Freeze / Unfreeze + Reset Stats toolbar
        header(stringManager) { renderDiagnosticsToolbar() }

        // Frozen indicator
        if (isFrozen.get()) {
            ImGui.pushStyleColor(ImGuiCol.Text, 1f, 0.8f, 0.2f, 1f)
            MImGui.wrappedText(stringManager.getString("lbl.profiler.frozen"))
            ImGui.popStyleColor()
            ImGui.separator()
        }

        MImGui.wrappedText(stringManager.getString("lbl.profiler.avg_ms", frameTime, fps))
        MImGui.wrappedText(stringManager.getString("lbl.profiler.draw_calls", EngineStats.drawCalls.get()))
        MImGui.wrappedText(stringManager.getString("lbl.profiler.physics_step", physicsTime))
        MImGui.wrappedText(stringManager.getString("lbl.profiler.ram_usage", usedMemory, totalMemory))

        ImGui.separator()
        MImGui.row(stringManager.getString("lbl.profiler.show_mem_stats"), "show_mem_stats") { ImGui.checkbox("##value", showMemoryStats) }
        if (showMemoryStats.get()) {
            val memRatio = usedMemory.toFloat() / totalMemory.toFloat()
            ImGui.progressBar(memRatio, -1f, 0f, "%d%%".format((memRatio * 100).toInt()))
        }

        ImGui.separator()
        MImGui.wrappedText("${Icons.CHART_LINE} ${stringManager.getString("window.profiler.graphs")}")
        MImGui.row(stringManager.getString("lbl.profiler.show_graphs"), "show_graphs") { ImGui.checkbox("##value", showGraphs) }
        if (showGraphs.get() && history.currentSize > 0) {
            val lastIdx = if (history.currentIndex == 0) history.maxSize - 1 else history.currentIndex - 1

            // Frame Time Graph
            MImGui.row("${Icons.CLOCK} ${stringManager.getString("lbl.profiler.frame_time_graph")}", "lbl.profiler.frame_time_graph") { ImGui.plotLines(
                "##graph",
                history.frameTimes,
                history.currentSize,
                history.currentIndex,
                String.format("%.2f ms", history.frameTimes[lastIdx]),
                0f, 100f, 0f, 80f
            ) }

            // FPS Graph
            MImGui.row("${Icons.CHART_LINE} ${stringManager.getString("lbl.profiler.fps_graph")}", "lbl.profiler.fps_graph") { ImGui.plotLines(
                "##graph",
                history.fpsValues,
                history.currentSize,
                history.currentIndex,
                String.format("%.0f FPS", history.fpsValues[lastIdx]),
                0f, 240f, 0f, 80f
            ) }

            // Physics Step Time Graph
            MImGui.row("${Icons.MICROCHIP} ${stringManager.getString("lbl.profiler.physics_graph")}", "lbl.profiler.physics_graph") { ImGui.plotLines(
                "##graph",
                history.physicsStepTimes,
                history.currentSize,
                history.currentIndex,
                String.format("%.2f ms", history.physicsStepTimes[lastIdx]),
                0f, 33f, 0f, 80f
            ) }

            // Memory Usage Graph
            MImGui.row("${Icons.MEMORY} ${stringManager.getString("lbl.profiler.memory_graph")}", "lbl.profiler.memory_graph") { ImGui.plotLines(
                "##graph",
                history.memoryUsage,
                history.currentSize,
                history.currentIndex,
                String.format("%.0f MB", history.memoryUsage[lastIdx]),
                0f, totalMemory.toFloat(), 0f, 80f
            ) }
        }

        ImGui.separator()

        // System timing breakdown note
        ImGui.pushTextWrapPos(0f)
        ImGui.textColored(0.5f, 0.5f, 0.5f, 1f, stringManager.getString("lbl.profiler.no_system_timings"))
        ImGui.popTextWrapPos()

        ImGui.separator()

        if (!threadBean.isThreadCpuTimeSupported) {
            EmptyState.render(stringManager, Icons.MICROCHIP, "lbl.profiler.thread_cpu_unsupported")
            ImGui.end()
            return
        }

        val currentTime = System.nanoTime()
        val deltaTime = (currentTime - lastSampleTime).toFloat() / 1_000_000_000f

        if (deltaTime > 0.5f) { // Sample every 500ms
            updateStats(currentTime)
            lastSampleTime = currentTime
        }

        if (threadCpuUsage.isEmpty()) {
            EmptyState.nothingYet(stringManager, Icons.MICROCHIP, "empty.profiler.threads")
            ImGui.end()
            return
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

    private fun renderDiagnosticsToolbar() {
        // Freeze / Unfreeze toggle
        if (isFrozen.get()) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.6f, 0.4f, 0.1f, 1f)
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.7f, 0.5f, 0.2f, 1f)
            if (MImGui.fitButton("${Icons.PLAY} ${stringManager.getString("btn.profiler.unfreeze")}")) {
                isFrozen.set(false)
            }
            ImGui.popStyleColor(2)
            Tooltips.text(stringManager.getString("tooltip.profiler.unfreeze"))
        } else {
            if (MImGui.fitButton("${Icons.PAUSE} ${stringManager.getString("btn.profiler.freeze")}")) {
                isFrozen.set(true)
            }
            Tooltips.text(stringManager.getString("tooltip.profiler.freeze"))
        }

        val reset = "${Icons.ARROW_ROTATE} ${stringManager.getString("btn.profiler.reset_stats")}"
        MImGui.sameLineIfFits(MImGui.buttonWidth(reset)) // EDT-100: wraps in a narrow window

        // Reset Stats button
        if (MImGui.fitButton(reset)) {
            history.reset()
        }
        Tooltips.text(stringManager.getString("tooltip.profiler.reset_stats"))
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
                name.contains("IO", true)
            ) {
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
