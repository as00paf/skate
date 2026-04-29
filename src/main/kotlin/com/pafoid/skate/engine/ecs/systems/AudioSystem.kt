package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.editor.data.LogLevel
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.data.SoundSource
import com.pafoid.skate.engine.audio.AudioEngine
import com.pafoid.skate.engine.ecs.components.AudioComponent
import com.pafoid.skate.engine.ecs.components.Transform
import imgui.ImGui
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lwjgl.openal.ALC
import org.lwjgl.openal.ALC10
import kotlin.math.cos
import kotlin.math.sin

/**
 * ECS system for audio playback and 3D spatialization.
 *
 * Updates listener position from camera and manages 3D audio source positions.
 * Runs at ExecutionPriority.LATE to ensure transforms are updated first.
 */
class AudioSystem(
    private val audioEngine: AudioEngine,
    private val logger: LoggerService
) : System(priority = ExecutionPriority.LATE), KoinComponent {

    private val resourceManager: ResourceManager by inject()
    private var isInitialized = false

    // Maps Entity ID to its active SoundSource
    private val activeSources = mutableMapOf<Int, SoundSource>()

    override fun update(dt: Float) {
        if (!isInitialized) {
            if (audioEngine.init()) {
                isInitialized = true
                logger.logEngine("AudioSystem: Audio initialized")
            } else {
                logger.logEngine("AudioSystem: Failed to initialize - audio disabled", LogLevel.WARN)
                return
            }
        }

        updateListener()
        updateAudioSources()
    }

    override fun imgui() {
        // Master Volume
        val volumeArray = floatArrayOf(1.0f)
        if (ImGui.dragFloat("Master Volume", volumeArray, 0.01f, 0f, 1f)) {
            audioEngine.setMasterVolume(volumeArray[0])
        }

        // Mute toggle
        val isMuted = volumeArray[0] <= 0.001f
        if (ImGui.button(if (isMuted) "Unmute" else "Mute")) {
            val newVolume = if (isMuted) 1.0f else 0.0f
            audioEngine.setMasterVolume(newVolume)
        }

        ImGui.separator()

        // Status
        val status = if (audioEngine.isInitialized) "Initialized" else "Not Initialized"
        val color = if (audioEngine.isInitialized) floatArrayOf(0f, 1f, 0f, 1f) else floatArrayOf(0.5f, 0.5f, 0.5f, 1f)
        ImGui.textColored(color[0], color[1], color[2], color[3], "Status: $status")

        ImGui.separator()

        // Listener info
        ImGui.text("Listener Information")
        ImGui.text("Volume: %.2f".format(volumeArray[0]))
    }

    private fun updateListener() {
        if (!audioEngine.isInitialized) return

        val currentContext = ALC10.alcGetCurrentContext()
        if (currentContext != audioEngine.getContext()) {
            audioEngine.makeContextCurrent()
        }

        if (!ALC.getCapabilities().OpenALC10) {
            ALC.createCapabilities(audioEngine.getDevice())
        }

        val camera = scene.camera
        val pos = camera.position
        val forward = calculateForwardVector(camera.yaw, camera.pitch)

        audioEngine.setListenerPosition(pos.x, pos.y, pos.z)
        audioEngine.setListenerOrientation(
            floatArrayOf(forward.x, forward.y, forward.z),
            floatArrayOf(0f, 1f, 0f)
        )
        audioEngine.setListenerVelocity(0f, 0f, 0f)
        audioEngine.setMasterVolume(1.0f)
    }

    private fun calculateForwardVector(yaw: Float, pitch: Float): Vector3f {
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())
        val cosYaw = cos(yawRad).toFloat()
        val sinYaw = sin(yawRad).toFloat()
        val cosPitch = cos(pitchRad).toFloat()
        val sinPitch = sin(pitchRad).toFloat()

        return Vector3f(
            cosYaw * cosPitch,
            sinPitch,
            sinYaw * cosPitch
        ).normalize()
    }

    private fun updateAudioSources() {
        val currentEntities = mutableSetOf<Int>()

        scene.gameObjects.forEach { gameObject ->
            val audioComponent = gameObject.getComponent<AudioComponent>()
            val transform = gameObject.getComponent<Transform>()

            if (audioComponent != null) {
                currentEntities.add(gameObject.getUid())
                var source = activeSources[gameObject.getUid()]

                if (source == null && audioComponent.soundFilePath.isNotBlank()) {
                    // Try to load the sound buffer
                    val buffer = resourceManager.loadSound(audioComponent.soundFilePath)
                    if (buffer.bufferId != -1) {
                        source = SoundSource(audioComponent.loops, !audioComponent.is3D)
                        source.setBuffer(buffer.bufferId)
                        activeSources[gameObject.getUid()] = source
                    }
                }

                if (source != null) {
                    // Update state
                    source.setVolume(audioComponent.volume)
                    source.setLooping(audioComponent.loops)
                    source.setRelative(!audioComponent.is3D)

                    if (transform != null && audioComponent.is3D) {
                        source.setPosition(transform.translation.x, transform.translation.y, transform.translation.z)
                    } else {
                        // For 2D sounds (relative), position is usually 0,0,0 relative to listener
                        source.setPosition(0f, 0f, 0f)
                    }

                    // Handle playback requests
                    if (audioComponent.playRequested) {
                        source.play()
                        audioComponent.playRequested = false
                        audioComponent.isPlaying = true
                    }
                    if (audioComponent.pauseRequested) {
                        source.pause()
                        audioComponent.pauseRequested = false
                        audioComponent.isPlaying = false
                    }
                    if (audioComponent.stopRequested) {
                        source.stop()
                        audioComponent.stopRequested = false
                        audioComponent.isPlaying = false
                    }

                    // Sync isPlaying state back to component
                    audioComponent.isPlaying = source.isPlaying()
                }
            }
        }

        // Cleanup sources for removed components/entities
        val removedEntities = activeSources.keys - currentEntities
        removedEntities.forEach { id ->
            activeSources[id]?.delete()
            activeSources.remove(id)
        }
    }

    override fun destroy() {
        isInitialized = false
        activeSources.values.forEach { it.delete() }
        activeSources.clear()
    }
}
