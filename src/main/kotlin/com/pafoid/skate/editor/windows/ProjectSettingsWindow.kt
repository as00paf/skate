package com.pafoid.skate.editor.windows

import com.pafoid.skate.editor.imgui.IWindow
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.settings.RecentProjectInfo
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiWindowFlags
import imgui.internal.ImGui.begin
import imgui.internal.ImGui.button
import imgui.internal.ImGui.collapsingHeader
import imgui.internal.ImGui.dragFloat
import imgui.internal.ImGui.dragInt
import imgui.internal.ImGui.end
import imgui.internal.ImGui.inputText
import imgui.internal.ImGui.sameLine
import imgui.internal.ImGui.separator
import imgui.internal.ImGui.setNextWindowPos
import imgui.internal.ImGui.setNextWindowSize
import imgui.type.ImBoolean
import imgui.type.ImString
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ProjectSettingsWindow(
    private val settingsManager: SettingsManager,
    private val stringManager: StringManager,
    private val projectSwitcher: ProjectSwitcherDialog,
    private val logger: LoggerService
) : IWindow {

    private val searchBuffer = ImString("", 128)
    private var hasPendingChanges = false
    private var tempPhysicsFPS = 60
    private var tempGravity = -9.81f
    private var tempTimeScale = 1.0f

    override fun imgui(pOpen: ImBoolean?) {
        if (pOpen?.get() == false) return
        if (!hasPendingChanges) syncTempSettings()

        val viewport = ImGui.getMainViewport()
        val defaultWidth = viewport.workSizeX * 0.5f
        setNextWindowPos(viewport.centerX, viewport.centerY, ImGuiCond.FirstUseEver, 0.5f, 0.5f)
        setNextWindowSize(defaultWidth, 500f, ImGuiCond.FirstUseEver)

        if (begin(stringManager.getString("window.project_settings"), pOpen, ImGuiWindowFlags.NoDocking)) {
            ImGui.pushItemWidth(-1f)
            inputText(stringManager.getString("settings.search.placeholder"), searchBuffer)
            ImGui.popItemWidth()
            separator()

            if (!settingsManager.hasProject()) {
                renderNoProjectState()
            } else {
                renderSettingsContent()
            }
            separator()

            // OK / Cancel / Apply buttons
            if (settingsManager.hasProject()) {
                ImGui.spacing()
                val btnW = 100f

                if (button("OK", btnW, 0f)) {
                    saveSettings()
                    pOpen?.set(false)
                }
                ImGui.sameLine()
                if (button("Cancel", btnW, 0f)) {
                    syncTempSettings()
                    hasPendingChanges = false
                    pOpen?.set(false)
                }
                ImGui.sameLine()
                ImGui.beginDisabled(!hasPendingChanges)
                if (button("Apply", btnW, 0f)) {
                    saveSettings()
                }
                ImGui.endDisabled()
            } else {
                if (button(stringManager.getString("btn.close"))) {
                    pOpen?.set(false)
                }
            }
        }
        end()
    }

    private fun renderNoProjectState() {
        ImGui.text(stringManager.getString("settings.project.no_project"))
        MImGui.textDisabled(stringManager.getString("settings.project.no_project_description"))
        ImGui.spacing()
        if (button(stringManager.getString("settings.project.btn.open_project"))) {
            projectSwitcher.open()
        }
    }

    private fun renderSettingsContent() {
        val query = searchBuffer.get().lowercase()
        if (matchesSearch(query, "general", "metadata", "project", "name", "version", "engine", "path", "description")) {
            if (collapsingHeader(stringManager.getString("settings.project.metadata"))) {
                renderMetadataSection(); separator()
            }
        }
        if (matchesSearch(query, "recent", "project")) {
            if (collapsingHeader(stringManager.getString("settings.project.recent_projects"))) {
                renderRecentProjectsSection(); separator()
            }
        }
        if (matchesSearch(query, "gameplay", "physics", "fps", "gravity", "time", "scale")) {
            if (collapsingHeader(stringManager.getString("settings.project.gameplay"))) {
                renderGameplaySection(); separator()
            }
        }
    }

    private fun renderMetadataSection() {
        val project = settingsManager.project ?: return
        val meta = project.metadata
        MImGui.propertyRowReadOnly(stringManager.getString("settings.project.name"), meta.name)
        MImGui.propertyRowReadOnly(stringManager.getString("settings.project.version"), meta.version)
        MImGui.propertyRowReadOnly(stringManager.getString("settings.project.engine_version"), meta.engineVersion)
        MImGui.propertyRowReadOnly(stringManager.getString("settings.project.path"), truncatePath(meta.projectPath))
        val desc = meta.description.ifBlank { stringManager.getString("settings.project.description.none") }
        MImGui.propertyRowReadOnly(stringManager.getString("settings.project.description"), desc)
    }

    private fun renderRecentProjectsSection() {
        val recent = getRecentProjectsDisplayInfo()
        if (recent.isEmpty()) { MImGui.textDisabled(stringManager.getString("settings.project.recent_projects.none")); return }
        if (ImGui.beginTable("##recent_projects_table", 3)) {
            ImGui.tableSetupColumn(stringManager.getString("settings.project.recent_projects.table.name"))
            ImGui.tableSetupColumn(stringManager.getString("settings.project.recent_projects.table.path"))
            ImGui.tableSetupColumn(stringManager.getString("settings.project.recent_projects.table.last_opened"))
            ImGui.tableHeadersRow()
            for (p in recent) {
                ImGui.tableNextRow()
                ImGui.tableSetColumnIndex(0); ImGui.text(p.name)
                ImGui.tableSetColumnIndex(1); ImGui.text(truncatePath(p.path))
                ImGui.tableSetColumnIndex(2)
                if (p.exists) { ImGui.text(p.getLastOpenedString()) }
                else { ImGui.pushStyleColor(ImGuiCol.Text, 0.83f, 0.13f, 0.17f, 1f); ImGui.text(stringManager.getString("settings.project.recent_projects.table.not_found")); ImGui.popStyleColor() }
            }
            ImGui.endTable()
        }
    }

    private fun renderGameplaySection() {
        val fps = intArrayOf(tempPhysicsFPS)
        MImGui.propertyRow(label = stringManager.getString("settings.project.gameplay.physics_fps"),
            helpTooltip = stringManager.getString("settings.project.gameplay.physics_fps.desc"),
            onReset = { tempPhysicsFPS = 60; hasPendingChanges = true }) {
            if (dragInt("##physics_fps", fps, 1f, 30, 240)) { tempPhysicsFPS = fps[0]; hasPendingChanges = true }
        }
        val grav = floatArrayOf(tempGravity)
        MImGui.propertyRow(label = stringManager.getString("settings.project.gameplay.gravity"),
            helpTooltip = stringManager.getString("settings.project.gameplay.gravity.desc"),
            onReset = { tempGravity = -9.81f; hasPendingChanges = true }) {
            if (dragFloat("##gravity", grav, 0.01f, -50f, 0f)) { tempGravity = grav[0]; hasPendingChanges = true }
        }
        val ts = floatArrayOf(tempTimeScale)
        MImGui.propertyRow(label = stringManager.getString("settings.project.gameplay.time_scale"),
            helpTooltip = stringManager.getString("settings.project.gameplay.time_scale.desc"),
            onReset = { tempTimeScale = 1.0f; hasPendingChanges = true }) {
            if (dragFloat("##time_scale", ts, 0.01f, 0.0f, 5f)) { tempTimeScale = ts[0]; hasPendingChanges = true }
        }
        MImGui.textDisabled(stringManager.getString("settings.stored_not_applied"))
    }

    private fun syncTempSettings() {
        val project = settingsManager.project
        tempPhysicsFPS = project?.gameplaySettings?.physicsFPS ?: 60
        tempGravity = project?.gameplaySettings?.gravity ?: -9.81f
        tempTimeScale = project?.gameplaySettings?.timeScale ?: 1.0f
    }

    private fun saveSettings() { hasPendingChanges = false }
    private fun resetToDefaults() { tempPhysicsFPS = 60; tempGravity = -9.81f; tempTimeScale = 1.0f; hasPendingChanges = true }

    private fun getRecentProjectsDisplayInfo(): List<RecentProjectDisplayInfo> {
        return settingsManager.recentProjects.map { r ->
            RecentProjectDisplayInfo(name = r.name, path = r.path, lastOpened = r.lastOpened, exists = File(r.path).exists())
        }
    }

    private fun truncatePath(path: String): String = if (path.length > 40) "\u2026${path.takeLast(37)}" else path

    companion object {
        private fun matchesSearch(query: String, vararg terms: String): Boolean {
            if (query.isBlank()) return true
            return terms.any { it.contains(query, ignoreCase = true) }
        }
    }
}

data class RecentProjectDisplayInfo(val name: String, val path: String, val lastOpened: Long, val exists: Boolean) {
    fun getLastOpenedString(): String {
        val instant = Instant.ofEpochMilli(lastOpened)
        val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy").withZone(ZoneId.systemDefault())
        return formatter.format(instant)
    }
}
