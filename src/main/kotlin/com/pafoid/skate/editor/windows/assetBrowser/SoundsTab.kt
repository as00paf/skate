package com.pafoid.skate.editor.windows.assetBrowser

import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.LogLevel
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.systems.ThumbnailCache
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.data.SoundSource
import imgui.ImGui
import imgui.flag.ImGuiTableColumnFlags
import imgui.flag.ImGuiTableFlags
import imgui.type.ImString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

/**
 * Asset browser tab for sound files.
 * 
 * Displays audio files in a list (table) with playback controls.
 * Supports WAV and OGG formats.
 */
class SoundsTab(
    resourceManager: ResourceManager,
    thumbnailCache: ThumbnailCache,
    stringManager: StringManager
) : AssetBrowserTab(resourceManager, thumbnailCache, stringManager), KoinComponent {

    private val logger: LoggerService by inject()
    private var playingSource: SoundSource? = null
    private var currentPlayingFile: File? = null

    override fun imgui(label: String, searchText: ImString) {
        renderHeader(label, searchText)

        val files = items.filter { it.name.contains(searchText.get(), ignoreCase = true) }

        // List View using Table
        val tableFlags = ImGuiTableFlags.RowBg or 
                         ImGuiTableFlags.BordersInnerV or 
                         ImGuiTableFlags.ScrollY or 
                         ImGuiTableFlags.Resizable
        
        if (ImGui.beginTable("##${label}Table", 4, tableFlags)) {
            // Setup columns
            ImGui.tableSetupColumn("", ImGuiTableColumnFlags.WidthFixed, 24f) // Icon
            ImGui.tableSetupColumn(stringManager.getString("lbl.name"), ImGuiTableColumnFlags.WidthStretch) // Filename
            ImGui.tableSetupColumn(stringManager.getString("lbl.duration"), ImGuiTableColumnFlags.WidthFixed, 60f) // Duration
            ImGui.tableSetupColumn("", ImGuiTableColumnFlags.WidthFixed, 40f) // Actions
            
            ImGui.tableHeadersRow()

            for (file in files) {
                ImGui.tableNextRow()
                renderSoundRow(file)
            }
            ImGui.endTable()
        }
    }

    private fun renderSoundRow(file: File) {
        ImGui.pushID(file.absolutePath)
        
        // Pre-load sound for duration info
        val buffer = resourceManager.getSound(file.absolutePath) ?: resourceManager.loadSound(file.absolutePath)
        val duration = buffer.durationInSeconds
        val isPlaying = currentPlayingFile == file && playingSource?.isPlaying() == true

        // Column 0: Icon
        ImGui.tableNextColumn()
        ImGui.text(Icons.MUSIC)

        // Column 1: Name + Drag and Drop + Context Menu
        ImGui.tableNextColumn()
        ImGui.selectable(file.name, false)
        
        // Context menu on right-click
        if (ImGui.beginPopupContextItem()) {
            val playStopLabel = if (isPlaying) 
                "${Icons.STOP} ${stringManager.getString("context.asset_browser.play_stop")}" 
            else 
                "${Icons.PLAY} ${stringManager.getString("context.asset_browser.play_stop")}"
            
            if (ImGui.menuItem(playStopLabel)) {
                handlePlayback(file, buffer)
            }
            ImGui.separator()
            if (ImGui.menuItem("${Icons.PLUS} ${stringManager.getString("context.asset_browser.add_to_gameobject")}")) {
                addSoundToSelectedObject(file.path)
            }
            ImGui.separator()
            if (ImGui.menuItem("${Icons.EXTERNAL_LINK} ${stringManager.getString("context.asset_browser.open_external")}")) {
                java.awt.Desktop.getDesktop().open(file)
            }
            if (ImGui.menuItem("${Icons.FOLDER} ${stringManager.getString("context.asset_browser.show_in_folder")}")) {
                java.awt.Desktop.getDesktop().open(file.parentFile)
            }
            ImGui.separator()
            if (ImGui.menuItem("${Icons.INFO} ${stringManager.getString("context.asset_browser.properties")}")) {
                logger.logEditor("Sound: ${file.name}, Duration: ${duration}s, Path: ${file.absolutePath}")
            }
            ImGui.endPopup()
        }
        
        if (ImGui.beginDragDropSource()) {
            ImGui.setDragDropPayload("SOUND", file.path)
            ImGui.text("${Icons.MUSIC} ${file.name}")
            ImGui.endDragDropSource()
        }

        // Column 2: Duration
        ImGui.tableNextColumn()
        ImGui.text("%.2fs".format(duration))

        // Column 3: Actions (Play/Stop)
        ImGui.tableNextColumn()
        val buttonIcon = if (isPlaying) Icons.STOP else Icons.PLAY
        val tooltipKey = if (isPlaying) "tooltip.stop_sound" else "tooltip.play_sound"
        
        if (ImGui.button(buttonIcon, -1f, 0f)) {
            handlePlayback(file, buffer)
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(stringManager.getString(tooltipKey))
        }

        ImGui.popID()
    }

    private fun addSoundToSelectedObject(soundPath: String) {
        // TODO: Implement adding AudioComponent to selected object
        logger.logEditor("Add sound to object not yet implemented: $soundPath")
    }

    private fun handlePlayback(file: File, buffer: com.pafoid.skate.engine.assets.data.SoundBuffer) {
        val isPlaying = currentPlayingFile == file && playingSource?.isPlaying() == true
        
        if (isPlaying) {
            playingSource?.stop()
            playingSource?.delete()
            playingSource = null
            currentPlayingFile = null
        } else {
            // Stop any currently playing sound
            playingSource?.stop()
            playingSource?.delete()

            currentPlayingFile = file
            try {
                if (buffer.bufferId != -1) {
                    playingSource = SoundSource(isLooping = false, isRelative = true)
                    playingSource?.setBuffer(buffer.bufferId)
                    playingSource?.play()
                } else {
                    currentPlayingFile = null
                }
            } catch (e: Exception) {
                logger.logEngine("SoundsTab: Failed to load sound '${file.name}' - ${e.message}", LogLevel.ERROR)
                currentPlayingFile = null
            }
        }
    }

    override fun refreshAssets() {
        items.clear()
        val soundsDir = File("assets/sounds")
        if (soundsDir.exists()) {
            items.addAll(soundsDir.walkTopDown().filter { file ->
                val ext = file.extension.lowercase()
                ext == "wav" || ext == "ogg"
            })
        }
    }

    fun destroy() {
        playingSource?.delete()
        playingSource = null
        currentPlayingFile = null
    }
}
