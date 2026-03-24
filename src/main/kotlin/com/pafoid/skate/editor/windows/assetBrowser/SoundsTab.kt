package com.pafoid.skate.editor.windows.assetBrowser

import com.pafoid.skate.editor.systems.LogLevel
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.systems.ThumbnailCache
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.data.SoundSource
import imgui.ImGui
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

/**
 * Asset browser tab for sound files.
 * 
 * Displays audio files in a grid with playback controls.
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

    override fun renderFileItem(file: File) {
        val padding = 5f
        val iconSize = 60f

        ImGui.beginGroup()
        ImGui.pushID(file.absolutePath)

        // Music note icon placeholder
        ImGui.dummy(iconSize, iconSize)
        val isPlaying = currentPlayingFile == file && playingSource?.isPlaying() == true

        // Single toggle button with icon
        val buttonText = if (isPlaying) "⏹ Stop" else "▶ Play"
        if (ImGui.button(buttonText, iconSize * 2f, 0f)) {
            if (isPlaying) {
                playingSource?.stop()
                playingSource?.delete()
                currentPlayingFile = null
            } else {
                // Stop any currently playing sound
                playingSource?.stop()
                playingSource?.delete()

                currentPlayingFile = file
                try {
                    logger.logEngine("SoundsTab: Loading sound '${file.path}'", LogLevel.INFO)
                    val buffer = resourceManager.loadSound(file.absolutePath)
                    if (buffer.bufferId != -1) {
                        playingSource = SoundSource(isLooping = false, isRelative = true)
                        playingSource?.setBuffer(buffer.bufferId)
                        logger.logEngine("SoundsTab: Playing sound '${file.name}'", LogLevel.INFO)
                        playingSource?.play()
                    } else {
                        logger.logEngine("SoundsTab: Failed to load Sound buffer", LogLevel.ERROR)
                        currentPlayingFile = null
                    }
                } catch (e: Exception) {
                    logger.logEngine("SoundsTab: Failed to load sound '${file.name}' - ${e.message}", LogLevel.ERROR)
                    e.printStackTrace()
                    currentPlayingFile = null
                }
            }
        }

        // Drag and drop source for audio files (must be after item)
        if (ImGui.beginDragDropSource()) {
            ImGui.setDragDropPayload("SOUND", file.path)
            ImGui.text("♪ " + file.name)
            ImGui.endDragDropSource()
        }

        ImGui.textWrapped(file.name)
        ImGui.dummy(0f, padding)
        ImGui.popID()
        ImGui.endGroup()
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
