package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.data.SoundSource
import com.pafoid.skate.engine.audio.AudioEngine
import com.pafoid.skate.engine.audio.ClipPicker
import com.pafoid.skate.engine.audio.ReverbZoneResolver
import com.pafoid.skate.engine.audio.StbVorbisDecoder
import com.pafoid.skate.engine.audio.ZoneShape
import com.pafoid.skate.engine.audio.StreamPolicy
import com.pafoid.skate.engine.audio.StreamingPlayer
import com.pafoid.skate.engine.audio.VoiceManager
import com.pafoid.skate.engine.audio.SpatialAudio
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import com.pafoid.skate.engine.ecs.components.AudioComponent
import com.pafoid.skate.engine.ecs.components.AudioListener
import com.pafoid.skate.engine.ecs.components.ReverbZone
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.ecs.GameObject
import org.joml.Matrix4f
import org.joml.Vector3f
import java.io.File
import org.lwjgl.openal.ALC
import org.lwjgl.openal.ALC10

class AudioSystem(
    val audioEngine: AudioEngine,
    private val logger: LoggerService,
    private val assetsManager: AssetsManager,
    private val cameraManager: CameraManager
) : System(priority = ExecutionPriority.LATE) {
    override val runsAfter = listOf(CameraManager::class)

    private var isInitialized = false
    var masterVolume = 1.0f
        set(volume) {
            val clamped = volume.coerceIn(0f, 1f)
            field = clamped
            if (clamped > 0.001f) {
                lastNonMutedVolume = clamped
            }
            audioEngine.setMasterVolume(clamped)
        }
    var lastNonMutedVolume = 1.0f

    private var frameDt = 0f
    private var spatialConfigured = false
    private val listenerPos = Vector3f()
    private val listenerVel = Vector3f()
    private val previousPositions = mutableMapOf<Int, Vector3f>()

    private val playbacks = mutableMapOf<Int, Playback>()
    private val clipPicker = ClipPicker()
    private val failedClips = mutableSetOf<String>()

    override fun update(dt: Float) {
        if (!isInitialized) {
            if (audioEngine.init()) {
                isInitialized = true
                logger.log("AudioSystem: Audio initialized", LogLevel.INFO)
            } else {
                logger.log("AudioSystem: Failed to initialize - audio disabled", LogLevel.WARN)
                return
            }
        }

        frameDt = dt
        if (!spatialConfigured) {
            audioEngine.configureSpatial()
            spatialConfigured = true
        }
        updateListener()
        updateReverbZone()
        updateAudioSources()
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

        val listenerObject = scene.children.firstOrNull { it.getComponent<AudioListener>()?.enabled == true }
        val pos: Vector3f
        val forward: Vector3f
        val up: Vector3f
        if (listenerObject != null) {
            val m = listenerObject.getComponent<Transform>()?.worldMatrix ?: Matrix4f()
            pos = m.getTranslation(Vector3f())
            forward = m.transformDirection(Vector3f(0f, 0f, -1f)).normalize()
            up = m.transformDirection(Vector3f(0f, 1f, 0f)).normalize()
        } else {
            val camera = cameraManager.camera
            pos = cameraManager.transform.worldMatrix.getTranslation(Vector3f())
            forward = Vector3f(camera.camForward)
            up = Vector3f(camera.camRight).cross(forward).normalize()
        }
        val velocity = motionVelocity(listenerObject?.let { -it.uId - 1 } ?: CAMERA_KEY, pos, listenerObject?.getComponent<RigidBody3D>())

        listenerPos.set(pos)
        listenerVel.set(velocity)
        audioEngine.setListenerPosition(pos.x, pos.y, pos.z)
        audioEngine.setListenerOrientation(
            floatArrayOf(forward.x, forward.y, forward.z),
            floatArrayOf(up.x, up.y, up.z)
        )
        audioEngine.setListenerVelocity(velocity.x, velocity.y, velocity.z)
    }

    /** Physics velocity when the entity has a body, otherwise finite difference of its position. */
    private fun motionVelocity(key: Int, pos: Vector3f, body: RigidBody3D?): Vector3f {
        val previous = previousPositions.put(key, Vector3f(pos))
        if (body != null && body.rawBody != null) return Vector3f(body.linearVelocity)
        return if (previous == null) Vector3f() else SpatialAudio.velocityFromMotion(previous, pos, frameDt)
    }

    private var zoneSend = 0f
    private var zoneLowPass = 1f

    private fun updateReverbZone() {
        val zones = scene.children.mapNotNull { go ->
            val z = go.getComponent<ReverbZone>()?.takeIf { it.enabled } ?: return@mapNotNull null
            val pos = go.getComponent<Transform>()?.worldMatrix?.getTranslation(Vector3f()) ?: return@mapNotNull null
            ZoneShape(go.uId, pos, z.radius, z.priority, z.preset.params, z.sendLevel, z.lowPassGainHF)
        }
        val active = ReverbZoneResolver.resolve(listenerPos, zones)
        zoneSend = active?.sendLevel ?: 0f
        zoneLowPass = active?.lowPassGainHF ?: 1f
        audioEngine.effects.setReverb(active?.params)
    }

    private class Playback(val source: SoundSource, val stream: StreamingPlayer?) : AutoCloseable {
        override fun close() {
            source.stop()
            stream?.close()
            source.close()
        }
    }

    private class SpatialState(val gain: Float, val pitch: Float, val position: Vector3f, val velocity: Vector3f)

    private fun computeSpatial(gameObject: GameObject, ac: AudioComponent): SpatialState {
        val base = audioEngine.mixer.sourceGain(ac.bus, ac.volume)
        val transform = gameObject.getComponent<Transform>()
        if (transform == null || !ac.is3D) return SpatialState(base, 1f, Vector3f(), Vector3f())
        val worldPos = transform.worldMatrix.getTranslation(Vector3f())
        val vel = motionVelocity(gameObject.uId, worldPos, gameObject.getComponent<RigidBody3D>())
        val attenuation = SpatialAudio.attenuation(
            ac.attenuationModel, worldPos.distance(listenerPos), ac.minDistance, ac.maxDistance, ac.rolloff
        )
        val pitch = SpatialAudio.dopplerPitch(listenerPos, listenerVel, worldPos, vel, ac.dopplerFactor)
        return SpatialState(base * attenuation, pitch, worldPos, vel)
    }

    private fun applyParams(pb: Playback, ac: AudioComponent, sp: SpatialState) {
        // Streamed sources loop by re-queueing, never through AL_LOOPING.
        pb.stream?.looping = ac.loops
        if (pb.stream == null) pb.source.setLooping(ac.loops)
        pb.source.setRelative(!ac.is3D)
        pb.source.setPosition(sp.position.x, sp.position.y, sp.position.z)
        pb.source.setVelocity(sp.velocity.x, sp.velocity.y, sp.velocity.z)
        pb.source.setVolume(sp.gain)
        pb.source.setPitch(sp.pitch)
        val bus = audioEngine.mixer.bus(ac.bus)
        audioEngine.effects.applySource(
            pb.source.sourceId,
            bus.effectiveLowPassGainHF * zoneLowPass,
            bus.reverbSend * zoneSend
        )
    }

    private fun updateAudioSources() {
        val currentEntities = mutableSetOf<Int>()

        scene.children.forEach { gameObject ->
            val ac = gameObject.getComponent<AudioComponent>() ?: return@forEach
            val id = gameObject.uId
            currentEntities.add(id)
            val spatial = computeSpatial(gameObject, ac)

            if (ac.playOnAwake && !ac.awoken) {
                ac.awoken = true
                ac.play()
            }
            if (ac.stopRequested) {
                ac.stopRequested = false
                releasePlayback(id)
            }
            if (ac.pauseRequested) {
                ac.pauseRequested = false
                playbacks[id]?.let {
                    it.source.pause()
                    it.stream?.paused = true
                    ac.isPaused = true
                }
            }
            if (ac.playRequested) {
                ac.playRequested = false
                startPlayback(id, ac, spatial)
            }

            val pb = playbacks[id]
            if (pb == null) {
                ac.isPlaying = false
                ac.isPaused = false
                return@forEach
            }
            applyParams(pb, ac, spatial)
            pb.stream?.update()
            audioEngine.voices.updateAudibility(id, spatial.gain)
            val finished = !ac.isPaused && (pb.stream?.isFinished ?: !pb.source.isPlaying())
            if (finished) {
                releasePlayback(id)
                ac.isPlaying = false
            } else {
                ac.isPlaying = !ac.isPaused
            }
        }

        // Cleanup voices for removed components/entities
        (playbacks.keys - currentEntities).forEach { id ->
            releasePlayback(id)
            previousPositions.remove(id)
            clipPicker.forget(id)
        }
    }

    private fun releasePlayback(id: Int) {
        playbacks.remove(id)?.let {
            audioEngine.effects.release(it.source.sourceId)
            it.close()
        }
        audioEngine.voices.release(id)
    }

    private fun startPlayback(id: Int, ac: AudioComponent, spatial: SpatialState) {
        val existing = playbacks[id]
        if (existing != null && ac.isPaused) {
            existing.source.play()
            existing.stream?.paused = false
            ac.isPaused = false
            return
        }

        val clip = clipPicker.pick(id, ac.soundFilePath, ac.clips)
        if (clip.isBlank() || clip in failedClips) return

        releasePlayback(id)
        val result = audioEngine.voices.request(id, ac.priority, spatial.gain)
        if (result is VoiceManager.Result.Denied) return
        (result as VoiceManager.Result.Granted<*>).stolen?.let { releasePlayback(it as Int) }

        try {
            val playback = createPlayback(clip, ac)
            playbacks[id] = playback
            applyParams(playback, ac, spatial)
            ac.isPaused = false
            if (playback.stream != null) playback.stream.start() else playback.source.play()
        } catch (e: Exception) {
            audioEngine.voices.release(id)
            failedClips.add(clip)
            logger.log("AudioSystem: Cannot play '$clip' - ${e.message}", LogLevel.ERROR)
        }
    }

    private fun createPlayback(clip: String, ac: AudioComponent): Playback {
        val stream = StreamPolicy.shouldStream(clip, ac.bus, ac.streamMode, File(clip).length())
        val source = SoundSource(!stream && ac.loops, !ac.is3D)
        try {
            if (stream) {
                val player = StreamingPlayer(StbVorbisDecoder(clip), source.asStreamSink(), ac.loops)
                return Playback(source, player)
            }
            val buffer = assetsManager.getSound(clip)
            if (buffer.bufferId == -1) throw IllegalStateException("sound buffer not loaded")
            source.setBuffer(buffer.bufferId)
            return Playback(source, null)
        } catch (e: Exception) {
            source.close()
            throw e
        }
    }

    override fun destroy() {
        isInitialized = false
        playbacks.values.forEach { it.close() }
        playbacks.clear()
        audioEngine.voices.clear()
    }

    private companion object {
        const val CAMERA_KEY = Int.MIN_VALUE
    }
}
