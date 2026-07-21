package com.pafoid.skate.editor.ui.windows.assetBrowser

import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.engine.assets.data.SoundBuffer
import com.pafoid.skate.engine.assets.data.SoundSource
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui
import imgui.flag.ImGuiTableColumnFlags
import imgui.flag.ImGuiTableFlags
import imgui.type.ImString
import java.awt.Desktop
import java.io.File

/**
 * Asset browser tab for sound files.
 *
 * Displays audio files in a list (table) with playback controls.
 * Supports WAV and OGG formats.
 */
class SoundsTab(
    private val engine: Engine,
    stringManager: StringManager,
) : AssetBrowserTab(engine.assetsManager, stringManager) {

    private var playingSource: SoundSource? = null
    private var currentPlayingFile: File? = null

    override fun imgui(label: String, searchText: ImString) {
        renderHeader(label, searchText)

        val files = items.filter { it.name.contains(searchText.get(), ignoreCase = true) }

        val tableFlags = ImGuiTableFlags.RowBg or 
                         ImGuiTableFlags.BordersInnerV or 
                         ImGuiTableFlags.ScrollY or 
                         ImGuiTableFlags.Resizable
        
        if (ImGui.beginTable("##${label}Table", 4, tableFlags)) {
            ImGui.tableSetupColumn("", ImGuiTableColumnFlags.WidthFixed, 24f)
            ImGui.tableSetupColumn(stringManager.getString("lbl.name"), ImGuiTableColumnFlags.WidthStretch)
            ImGui.tableSetupColumn(stringManager.getString("lbl.duration"), ImGuiTableColumnFlags.WidthFixed, 60f)
            ImGui.tableSetupColumn("", ImGuiTableColumnFlags.WidthFixed, 40f)
            
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

        val buffer = assetsManager.getSound(file.absolutePath)
        val duration = buffer.durationInSeconds
        val isPlaying = currentPlayingFile == file && playingSource?.isPlaying() == true

        ImGui.tableNextColumn()
        ImGui.text(Icons.MUSIC)

        ImGui.tableNextColumn()
        ImGui.selectable(file.name, false)

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
                Desktop.getDesktop().open(file)
            }
            if (ImGui.menuItem("${Icons.FOLDER} ${stringManager.getString("context.asset_browser.show_in_folder")}")) {
                Desktop.getDesktop().open(file.parentFile)
            }
            ImGui.separator()
            if (ImGui.menuItem("${Icons.INFO} ${stringManager.getString("context.asset_browser.properties")}")) {
                engine.logger.logEditor("Sound: ${file.name}, Duration: ${duration}s, Path: ${file.absolutePath}")
            }
            ImGui.endPopup()
        }
        
        if (ImGui.beginDragDropSource()) {
            ImGui.setDragDropPayload("SOUND", file.path)
            ImGui.text("${Icons.MUSIC} ${file.name}")
            ImGui.textColored(0.7f, 0.7f, 0.7f, 1f, "Duration: ${duration}s")
            ImGui.textColored(0.5f, 0.5f, 0.5f, 1f, "Drop on object to add AudioComponent")
            ImGui.endDragDropSource()
        }

        ImGui.tableNextColumn()
        ImGui.text("%.2fs".format(duration))

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
        // Future enhancement: Add AudioComponent to selected GameObject with this sound
        engine.logger.logEditor("Add sound to object not yet implemented: $soundPath")
    }

    private fun handlePlayback(file: File, buffer: SoundBuffer) {
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
                engine.logger.logEditor("SoundsTab: Failed to load sound '${file.name}' - ${e.message}", LogLevel.ERROR)
                currentPlayingFile = null
            }
        }
    }

    override fun refreshAssets() {
        engine.jobSystem.runIO {
            val fileExtensions = setOf("wav", "ogg", "mp3", "flac", "aiff")
            items.clear()
            val soundsDir = File("assets/sounds")
            if (soundsDir.exists()) {
                items.addAll(soundsDir.walkTopDown().filter { file ->
                    val ext = file.extension.lowercase()
                    ext in fileExtensions && assetsManager.hasSound(file.absolutePath)
                })
            }
        }
    }

    fun destroy() {
        playingSource?.delete()
        playingSource = null
        currentPlayingFile = null
    }
}
