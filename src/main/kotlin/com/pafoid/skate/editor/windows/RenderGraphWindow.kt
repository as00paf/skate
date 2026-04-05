package com.pafoid.skate.editor.windows

import com.pafoid.skate.editor.imgui.IWindow
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.render.renderer.Renderer
import com.pafoid.skate.engine.render.renderer.passes.RenderPass
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.type.ImBoolean
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Render Graph visualization window showing the rendering pipeline structure.
 *
 * This dockable window displays:
 * - All render passes in execution order
 * - Pass inputs and outputs (resource dependencies)
 * - Per-pass performance metrics (execution time)
 * - Enable/disable toggles for each pass
 * - Total frame time and statistics
 *
 * Usage:
 * - Open from View menu
 * - Click pass to see details
 * - Toggle enable/disable to debug rendering issues
 * - Use auto-update for real-time metrics
 */
class RenderGraphWindow : IWindow, KoinComponent {

    private val stringManager: StringManager by inject()
    private val renderer: Renderer by inject()

    private var autoUpdate = true
    private var showPerformance = true
    private var selectedPass: RenderPass? = null
    private var zoomLevel = 1.0f

    override fun imgui(pOpen: ImBoolean?) {
        ImGui.begin(stringManager.getString("window.render_graph"), pOpen)

        renderToolbar()
        ImGui.separator()
        ImGui.beginChild("GraphView", 0f, 400f)

        if (autoUpdate) {
            renderGraphNodes()
        } else {
            ImGui.text("Auto-update disabled. Click Refresh to update.")
        }

        ImGui.endChild()
        ImGui.separator()
        renderStatusBar()

        ImGui.end()
    }

    private fun renderToolbar() {
        if (ImGui.button("${Icons.ARROW_ROTATE} Refresh")) {
            // Force refresh (metrics already update automatically)
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(stringManager.getString("tooltip.render_graph.refresh"))
        }

        ImGui.sameLine()
        val autoUpdateBool = ImBoolean(autoUpdate)
        if (ImGui.checkbox(stringManager.getString("lbl.render_graph.auto_update"), autoUpdateBool)) {
            autoUpdate = autoUpdateBool.get()
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(stringManager.getString("tooltip.render_graph.auto_update"))
        }

        ImGui.sameLine()
        val showPerfBool = ImBoolean(showPerformance)
        if (ImGui.checkbox(stringManager.getString("lbl.render_graph.show_perf"), showPerfBool)) {
            showPerformance = showPerfBool.get()
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(stringManager.getString("tooltip.render_graph.show_performance"))
        }

        ImGui.sameLine()
        ImGui.text("Zoom:")
        ImGui.sameLine()
        val zoomArr = floatArrayOf(zoomLevel)
        if (ImGui.sliderFloat("##Zoom", zoomArr, 0.5f, 2.0f, "%.1f")) {
            zoomLevel = zoomArr[0]
        }
    }

    private fun renderGraphNodes() {
        val renderGraph = renderer.renderGraph
        val passes: List<RenderPass> = renderGraph.getAllPasses()

        if (passes.isEmpty()) {
            MImGui.warningText("No render passes found")
            return
        }

        // Simple vertical layout
        passes.forEachIndexed { index: Int, pass: RenderPass ->
            // Set position with zoom scaling
            val yPos = index * 160f * zoomLevel
            ImGui.setCursorPos(20f * zoomLevel, yPos)

            // Render node
            val clicked = renderPassNode(pass)

            if (clicked) {
                selectedPass = if (selectedPass == pass) null else pass
            }

            // Draw connection arrow to next pass
            if (index < passes.size - 1) {
                drawConnectionArrow(yPos + 160f * zoomLevel)
            }
        }
    }

    private fun renderPassNode(pass: RenderPass): Boolean {
        var clicked = false

        // Node styling based on enabled state
        if (!pass.isEnabled) {
            ImGui.pushStyleColor(ImGuiCol.Border, 0.5f, 0.5f, 0.5f, 1f)
            ImGui.pushStyleColor(ImGuiCol.Header, 0.3f, 0.3f, 0.3f, 1f)
        } else {
            ImGui.pushStyleColor(ImGuiCol.Border, 0.2f, 0.6f, 0.2f, 1f)
            ImGui.pushStyleColor(ImGuiCol.Header, 0.15f, 0.4f, 0.15f, 1f)
        }

        // Node header
        val headerFlags = if (selectedPass == pass) imgui.flag.ImGuiTreeNodeFlags.Selected else 0
        if (ImGui.collapsingHeader("${getPassIcon(pass)} ${pass.displayName}##${pass.name}", headerFlags)) {
            // Show description
            if (pass.description.isNotEmpty()) {
                MImGui.textDisabled(pass.description)
            }

            // Show inputs
            if (pass.inputs.isNotEmpty()) {
                ImGui.text("${stringManager.getString("lbl.render_graph.inputs")}:")
                pass.inputs.forEach { input ->
                    ImGui.bulletText("  $input")
                }
            }

            // Show outputs
            if (pass.outputs.isNotEmpty()) {
                ImGui.text("${stringManager.getString("lbl.render_graph.outputs")}:")
                pass.outputs.forEach { output ->
                    ImGui.bulletText("  $output")
                }
            }

            // Show execution time
            if (showPerformance) {
                val timeMs = pass.executionTimeNs / 1_000_000f
                val (r, g, b) = if (timeMs > 2.0f) {
                    Triple(1f, 0.3f, 0.3f)  // Red for slow
                } else if (timeMs > 1.0f) {
                    Triple(1f, 1f, 0.3f)  // Yellow for moderate
                } else {
                    Triple(0.3f, 1f, 0.3f)  // Green for fast
                }
                MImGui.coloredText(
                    "${stringManager.getString("lbl.render_graph.execution_time")}: %.2fms".format(timeMs),
                    r, g, b
                )
            }

            // Enable/disable toggle
            if (pass.canDisable) {
                val enabled = ImBoolean(pass.isEnabled)
                if (ImGui.checkbox("${stringManager.getString("lbl.render_graph.enabled")}##${pass.name}", enabled)) {
                    pass.toggleEnable()
                    clicked = true
                }
            } else {
                MImGui.textDisabled("Required pass (cannot disable)")
            }
        }

        ImGui.popStyleColor(2)

        // Check if header was clicked
        if (ImGui.isItemClicked()) {
            clicked = true
        }

        return clicked
    }

    private fun drawConnectionArrow(yPos: Float) {
        // Simple text arrow for now
        ImGui.setCursorPos(100f * zoomLevel, yPos)
        MImGui.textDisabled("↓")
    }

    private fun getPassIcon(pass: RenderPass): String {
        return when (pass.name) {
            "PickingPass" -> Icons.MOUSE_POINTER
            "GeometryPass" -> Icons.CUBE
            "ShadowPass" -> Icons.SUN
            "DebugPass" -> Icons.ATOM
            else -> Icons.GEAR
        }
    }

    private fun renderStatusBar() {
        val renderGraph = renderer.renderGraph
        val passes: List<RenderPass> = renderGraph.getAllPasses()

        val totalTime: Float = passes.sumOf { pass -> pass.executionTimeNs } / 1_000_000f
        val enabledCount: Int = passes.count { pass -> pass.isEnabled }
        val disabledCount = passes.size - enabledCount

        ImGui.text("${stringManager.getString("lbl.render_graph.passes")}: $enabledCount/${passes.size}")
        ImGui.sameLine()
        ImGui.text("| ${stringManager.getString("lbl.render_graph.total_time")}: %.2fms".format(totalTime))

        if (disabledCount > 0) {
            ImGui.sameLine()
            MImGui.warningText("($disabledCount disabled)")
        }

        // Help text
        MImGui.textDisabled(
            "  ${stringManager.getString("lbl.render_graph.click_to_toggle")}")
    }
}
