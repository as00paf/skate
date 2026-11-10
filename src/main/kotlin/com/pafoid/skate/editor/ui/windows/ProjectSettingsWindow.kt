package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.imgui.GraphicsBackendField
import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.editor.imgui.label
import com.pafoid.skate.editor.settings.GraphicsQuality
import com.pafoid.skate.editor.settings.ShadowQuality
import com.pafoid.skate.editor.settings.TextureQuality
import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.imgui.EditorDialog
import com.pafoid.skate.editor.imgui.EmptyState
import com.pafoid.skate.editor.imgui.FooterButton
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.systems.EditorSettingsManager
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui
import imgui.ImVec2
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiSelectableFlags
import imgui.internal.ImGui.beginChild
import imgui.internal.ImGui.endChild
import imgui.internal.ImGui.inputText
import imgui.internal.ImGui.sameLine
import imgui.internal.ImGui.setNextWindowPos
import imgui.internal.ImGui.setNextWindowSize
import imgui.internal.ImGui.combo
import imgui.type.ImInt
import imgui.type.ImString
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*
import com.pafoid.skate.editor.ui.windows.input.DeadZonePanel

class ProjectSettingsWindow(
    engine: Engine,
    private val settingsManager: EditorSettingsManager,
    private val projectManager: ProjectManager,
) : EditorDialog("window.project_settings", engine.stringManager, 760f, 550f) {
    private val eventSystem = engine.eventSystem
    private val stringManager: StringManager = engine.stringManager

    /** Dead-zone tuning, moved here from Input Testing (EDT-52I). Edits the open project's settings. */
    private val deadZonePanel = DeadZonePanel(stringManager, eventSystem, engine.inputProvider) {
        projectManager.currentProject?.gameplaySettings?.inputMappings?.deadZones
    }

    override val icon: String = Icons.CUBE
    override val relatedWindows = listOf("window.editor_settings", "window.export_wizard")

    // TODO: extract
    private data class Category(
        val id: String,
        val labelKey: String,
        val searchTerms: Array<String>,
        val render: () -> Unit
    )

    private val searchBuffer = ImString("", 128)
    private var selectedCategoryId = "general"

    private val categories: List<Category> by lazy {
        listOf(
            Category("general", "settings.project.metadata", arrayOf("general", "metadata", "project", "name", "version", "engine", "path", "description", "recent")) { renderGeneral() },
            Category("graphics", "settings.project.graphics", arrayOf("graphics", "backend", "vulkan", "opengl", "render", "export")) { renderGraphics() },
            Category("input", "settings.project.input", arrayOf("input", "gamepad", "dead", "zone", "deadzone", "stick", "trigger", "threshold")) { renderInput() },
        )
    }

    /**
     * Standard footer (EDT-51 §2.6): [Cancel] [Apply] [OK]. Each change is already an undoable command; OK and Apply
     * save the project. Without a project there is nothing to save, so only Close.
     */
    override fun footerButtons(): List<FooterButton> =
        if (!projectManager.hasProject()) {
            listOf(FooterButton("btn.close", FooterButton.Role.CANCEL))
        } else {
            listOf(
                FooterButton("btn.cancel", FooterButton.Role.CANCEL),
                FooterButton("btn.apply", FooterButton.Role.APPLY, closes = false) { eventSystem.publish(ProjectEvent.SaveRequested) },
                FooterButton("btn.ok", FooterButton.Role.PRIMARY) { eventSystem.publish(ProjectEvent.SaveRequested) },
            )
        }

    override fun content() {
        if (!projectManager.hasProject()) {
            // The shared empty state (EDT-51 §2.5), sized to end above the footer.
            ImGui.beginChild("##no_project", 0f, ImGui.getContentRegionAvailY() - dialogFooterHeight())
            EmptyState.noProject(stringManager, eventSystem)
            ImGui.endChild()
            return
        }
        val avail = ImVec2()
        ImGui.getContentRegionAvail(avail)
        val windowW = avail.x
        val leftW = windowW * 0.3f
        val rightW = (windowW - leftW) - ImGui.getStyle().itemSpacingX
        val contentH = avail.y - dialogFooterHeight()

        beginChild("leftPane", leftW, contentH)
        ImGui.pushItemWidth(-1f)
        inputText(stringManager.getString("settings.search.placeholder"), searchBuffer)
        ImGui.popItemWidth()
        ImGui.spacing()
        val query = searchBuffer.get().lowercase()
        val filtered = categories.filter { cat -> matchesSearch(query, *cat.searchTerms) }
        for (cat in filtered) {
            val isSelected = cat.id == selectedCategoryId
            if (isSelected) ImGui.pushStyleColor(ImGuiCol.Header, 0.25f, 0.45f, 0.75f, 1f)
            val label = stringManager.getString(cat.labelKey)
            if (ImGui.selectable(label, isSelected, ImGuiSelectableFlags.SpanAllColumns)) {
                selectedCategoryId = cat.id
            }
            if (isSelected) ImGui.popStyleColor()
        }
        endChild()
        sameLine()

        beginChild("rightPane", rightW, contentH)
        categories.find { it.id == selectedCategoryId }?.render()
        endChild()
    }

    /**
     * Project Settings › Input (EDT-51 §3.1): gamepad dead zones and trigger threshold with the live stick view.
     * Each slider gesture is one undoable command, and the project is saved after it.
     */
    private fun renderInput() = deadZonePanel.render()

    /** The backend exported games start with (EDT-43); each change is an undoable command, saved with OK/Apply. */
    private fun renderGraphics() {
        val project = projectManager.currentProject ?: return
        MImGui.textDisabled(stringManager.getString("settings.project.graphics.description"))
        ImGui.spacing()
        GraphicsBackendField.render(stringManager, project.graphicsBackend)?.let {
            eventSystem.publish(ProjectEvent.SetGraphicsBackendRequested(it))
        }

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // Overall quality preset
        val presetLabels = GraphicsQuality.entries.map {
            stringManager.getString("settings.graphics.quality.${it.name.lowercase()}")
        }.toTypedArray()
        val presetIdx = GraphicsQuality.entries.indexOf(project.graphicsQuality).coerceAtLeast(0)
        val presetSel = ImInt(presetIdx)
        MImGui.propertyRow(
            label = stringManager.getString("settings.project.graphics.quality_preset"),
            helpTooltip = stringManager.getString("settings.project.graphics.quality_preset.desc"),
            onReset = { eventSystem.publish(ProjectEvent.SetGraphicsQualityRequested(GraphicsQuality.HIGH)) }
        ) {
            if (combo("##quality_preset", presetSel, presetLabels, presetLabels.size)) {
                eventSystem.publish(ProjectEvent.SetGraphicsQualityRequested(GraphicsQuality.entries[presetSel.get()]))
            }
        }

        // MSAA samples
        val msaaOptions = intArrayOf(1, 2, 4, 8)
        val msaaLabels = msaaOptions.map { if (it == 1) stringManager.getString("settings.graphics.msaa.off") else "$it×" }.toTypedArray()
        val msaaIdx = msaaOptions.indexOf(project.msaaSamples).coerceAtLeast(0)
        val msaaSel = ImInt(msaaIdx)
        MImGui.propertyRow(
            label = stringManager.getString("settings.project.graphics.msaa_samples"),
            helpTooltip = stringManager.getString("settings.project.graphics.msaa_samples.desc"),
            onReset = { eventSystem.publish(ProjectEvent.SetMsaaSamplesRequested(4)) }
        ) {
            if (combo("##msaa_samples", msaaSel, msaaLabels, msaaLabels.size)) {
                eventSystem.publish(ProjectEvent.SetMsaaSamplesRequested(msaaOptions[msaaSel.get()]))
            }
        }

        // Texture quality
        val texLabels = TextureQuality.entries.map {
            stringManager.getString("settings.graphics.texture_quality.${it.name.lowercase()}")
        }.toTypedArray()
        val texIdx = TextureQuality.entries.indexOf(project.textureQuality).coerceAtLeast(0)
        val texSel = ImInt(texIdx)
        MImGui.propertyRow(
            label = stringManager.getString("settings.project.graphics.texture_quality"),
            helpTooltip = stringManager.getString("settings.project.graphics.texture_quality.desc"),
            onReset = { eventSystem.publish(ProjectEvent.SetTextureQualityRequested(TextureQuality.HIGH)) }
        ) {
            if (combo("##texture_quality", texSel, texLabels, texLabels.size)) {
                eventSystem.publish(ProjectEvent.SetTextureQualityRequested(TextureQuality.entries[texSel.get()]))
            }
        }

        // Shadow quality
        val shadowLabels = ShadowQuality.entries.map { stringManager.getString("settings.graphics.shadow_quality.${it.name.lowercase()}") }.toTypedArray()
        val shadowSel = ImInt(project.shadowQuality.ordinal)
        MImGui.propertyRow(
            label = stringManager.getString("settings.project.graphics.shadow_quality"),
            helpTooltip = stringManager.getString("settings.project.graphics.shadow_quality.desc"),
            onReset = { eventSystem.publish(ProjectEvent.SetShadowQualityRequested(ShadowQuality.MEDIUM)) }
        ) {
            if (combo("##shadow_quality", shadowSel, shadowLabels, shadowLabels.size)) {
                eventSystem.publish(ProjectEvent.SetShadowQualityRequested(ShadowQuality.entries[shadowSel.get()]))
            }
        }

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

                // Fullscreen
        val fullscreenLabels = arrayOf("Off", "On")
        val fullscreenIdx = if (project.fullscreen) 1 else 0
        val fullscreenSel = ImInt(fullscreenIdx)
        MImGui.propertyRow(
            label = stringManager.getString("settings.project.display.fullscreen"),
            helpTooltip = stringManager.getString("settings.project.display.fullscreen.desc")
        ) {
            if (combo("##fullscreen", fullscreenSel, fullscreenLabels, fullscreenLabels.size)) {
                eventSystem.publish(ProjectEvent.SetFullscreenRequested(fullscreenSel.get() == 1))
            }
        }

        ImGui.spacing()

        // VSync
        val vsyncLabels = arrayOf("Off", "On")
        val vsyncIdx = if (project.vsync) 1 else 0
        val vsyncSel = ImInt(vsyncIdx)
        MImGui.propertyRow(
            label = stringManager.getString("settings.project.display.vsync"),
            helpTooltip = stringManager.getString("settings.project.display.vsync.desc")
        ) {
            if (combo("##vsync", vsyncSel, vsyncLabels, vsyncLabels.size)) {
                eventSystem.publish(ProjectEvent.SetVsyncRequested(vsyncSel.get() == 1))
            }
        }
    }

    private fun renderGeneral() {
        val project = projectManager.currentProject ?: return
        MImGui.propertyRowReadOnly(stringManager.getString("settings.project.name"), project.name)
        MImGui.propertyRowReadOnly(stringManager.getString("settings.project.version"), project.version)
        MImGui.propertyRowReadOnly(stringManager.getString("settings.project.path"), truncatePath(project.projectPath))
        val desc = project.description.ifBlank { stringManager.getString("settings.project.description.none") }
        MImGui.propertyRowReadOnly(stringManager.getString("settings.project.description"), desc)

        ImGui.spacing()
        ImGui.text(stringManager.getString("settings.project.recent_projects"))
        ImGui.separator()
        val recent = settingsManager.recentProjects
        if (recent.isEmpty()) {
            MImGui.textDisabled(stringManager.getString("settings.project.recent_projects.none"))
            return
        }
        if (ImGui.beginTable("##recent_projects_table", 3)) {
            ImGui.tableSetupColumn(stringManager.getString("settings.project.recent_projects.table.name"))
            ImGui.tableSetupColumn(stringManager.getString("settings.project.recent_projects.table.path"))
            ImGui.tableSetupColumn(stringManager.getString("settings.project.recent_projects.table.last_opened"))
            ImGui.tableHeadersRow()
            for (p in recent) {
                ImGui.tableNextRow()
                ImGui.tableSetColumnIndex(0)
                ImGui.text(p.name)
                ImGui.tableSetColumnIndex(1)
                ImGui.text(truncatePath(p.projectPath))
                ImGui.tableSetColumnIndex(2)
                if (File(p.projectPath).exists()) {
                    ImGui.text(getLastOpenedString(p.lastOpenedDate))
                } else {
                    ImGui.pushStyleColor(ImGuiCol.Text, 0.83f, 0.13f, 0.17f, 1f)
                    ImGui.text(stringManager.getString("settings.project.recent_projects.table.not_found"))
                    ImGui.popStyleColor()
                }
            }
            ImGui.endTable()
        }
    }

    fun getLastOpenedString(lastOpened: Long): String {
        val instant = Instant.ofEpochMilli(lastOpened)
        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(Locale.getDefault())
            .withZone(ZoneId.systemDefault())
        return formatter.format(instant)
    }

    private fun truncatePath(path: String) = if (path.length > 40) "\u2026${path.takeLast(37)}" else path

    private fun matchesSearch(query: String, vararg terms: String) =
        if (query.isBlank()) true else terms.any { it.contains(query, ignoreCase = true) }

}
