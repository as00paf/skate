package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.editor.systems.LogLevel
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.engine.audio.AudioEngine
import com.pafoid.skate.engine.ecs.components.AudioComponent
import com.pafoid.skate.engine.ecs.components.Transform
import imgui.ImGui
import org.joml.Vector3f
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
) : System(priority = ExecutionPriority.LATE) {

    private var isInitialized = false

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

    override fun editorUpdate(dt: Float) {
        update(dt)
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
        scene.gameObjectManager.gameObjects.forEach { gameObject ->
            val audioComponent = gameObject.getComponent<AudioComponent>()
            val transform = gameObject.getComponent<Transform>()

            if (audioComponent != null && transform != null && audioComponent.is3D) {
                audioComponent.updatePosition(transform.translation)
            }
        }
    }

    override fun destroy() {
        isInitialized = false
    }
}
