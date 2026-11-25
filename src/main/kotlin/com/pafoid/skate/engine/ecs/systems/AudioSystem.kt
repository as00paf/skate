package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.data.SoundSource
import com.pafoid.skate.engine.audio.AttenuationModel
import com.pafoid.skate.engine.audio.AudioEngine
import com.pafoid.skate.engine.audio.BusId
import com.pafoid.skate.engine.audio.ClipPicker
import com.pafoid.skate.engine.audio.ReverbZoneResolver
import com.pafoid.skate.engine.audio.PcmDecoders
import com.pafoid.skate.engine.audio.ZoneShape
import com.pafoid.skate.engine.audio.StreamPolicy
import com.pafoid.skate.engine.audio.StreamingPlayer
import com.pafoid.skate.engine.audio.VoiceManager
import com.pafoid.skate.engine.audio.SpatialAudio
import com.pafoid.skate.engine.audio.SplashCooldown
import com.pafoid.skate.engine.audio.WaterAudio
import com.pafoid.skate.engine.audio.WaterAudioConfig
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import com.pafoid.skate.engine.ecs.components.AudioComponent
import com.pafoid.skate.engine.ecs.components.AudioListener
import com.pafoid.skate.engine.ecs.components.ReverbZone
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.components.WaterComponent
import com.pafoid.skate.engine.events.WaterEntered
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import org.joml.Matrix4f
import org.joml.Vector3f
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.atan2

/**
 * Plays [AudioComponent]s. Sources with `playOnAwake` start when the scene starts running (Play) and every voice is
 * released when it stops, which also re-arms `playOnAwake` for the next Play. Explicit `play()` works in both modes
 * (the inspector previews sounds while editing). All OpenAL access goes through [audioEngine]'s backend.
 *
 * In Play it also plays the water sounds (AUD-01, [waterAudio]): a splash on every [WaterEntered] from [eventSystem]
 * and a looping ambience from the nearest edge of each `WaterComponent` within reach of the listener.
 */
class AudioSystem(
    val audioEngine: AudioEngine,
    private val logger: LoggerService,
    private val assetsManager: AssetsManager,
    private val cameraManager: CameraManager,
    /** Maps a saved sound reference to a file to play: project-relative or packed sounds (AST-09). */
    private val localPath: (String) -> String = { it },
    /** Source of PHY-16's [WaterEntered] for splashes; null leaves splashes off (ambience still plays). */
    eventSystem: EventSystem? = null,
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
    private val missingClips = mutableSetOf<String>()
    private var wasRunning = false

    /** Voices held by the runtime pause ([Scene.isPaused]); see [pauseAll]. */
    private var wasPaused = false
    private val heldByPause = mutableSetOf<Int>()

    /** Water splash and ambience settings (AUD-01); applied from the next frame. */
    var waterAudio = WaterAudioConfig()

    /** Engine-owned water voices by voice key (never an entity id: see [ambienceKey], [nextSplashKey]). */
    private val waterSounds = mutableMapOf<Int, WaterSound>()
    private val pendingSplashes = ConcurrentLinkedQueue<WaterEntered>()
    private val splashCooldown = SplashCooldown()
    private val splashPicker = ClipPicker()
    private var nextSplashKey = -1
    private var clock = 0f
    private val heldWater = mutableSetOf<Int>()

    init {
        // Never unsubscribed: EventSystem.unsubscribe drops every listener of the type, gameplay's included.
        eventSystem?.subscribe<WaterEntered> { pendingSplashes.add(it) }
    }

    /** Runs while paused to hold and later continue the playing voices. */
    override val runsWhilePaused get() = true

    /**
     * Another scene (switch, Restart): the old scene's voices are released, including ones held by the pause, so
     * they neither keep playing as orphans nor resume in the new scene.
     */
    override fun init(scene: Scene) {
        if (scene !== this.scene) {
            heldByPause.clear()
            wasPaused = false
            wasRunning = false
            playbacks.keys.toList().forEach { releasePlayback(it) }
            releaseWater()
        }
        super.init(scene)
    }

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
        val running = scene.isRunning
        if (wasRunning && !running) stopAll()
        wasRunning = running

        val paused = running && scene.isPaused
        if (paused != wasPaused) if (paused) pauseAll() else resumeAll()
        wasPaused = paused
        if (paused) return

        updateListener()
        updateReverbZone()
        updateAudioSources(running)
        clock += dt
        updateWater(running)
    }

    /** Pause menu opened: holds every playing voice where it is (not released, so Resume continues it). */
    private fun pauseAll() {
        heldByPause.clear()
        scene.children.forEach { go ->
            val ac = go.getComponent<AudioComponent>() ?: return@forEach
            val pb = playbacks[go.uId] ?: return@forEach
            if (ac.isPaused) return@forEach
            pb.source.pause()
            pb.stream?.paused = true
            heldByPause.add(go.uId)
        }
        heldWater.clear()
        waterSounds.forEach { (key, ws) ->
            if (!ws.playback.source.isPlaying()) return@forEach
            ws.playback.source.pause()
            heldWater.add(key)
        }
    }

    /** Resume: continues only the voices [pauseAll] held; sounds paused by gameplay stay paused. */
    private fun resumeAll() {
        heldByPause.forEach { id ->
            playbacks[id]?.let {
                it.source.play()
                it.stream?.paused = false
            }
        }
        heldByPause.clear()
        heldWater.forEach { waterSounds[it]?.playback?.source?.play() }
        heldWater.clear()
    }

    /** End of Play: silence every source and re-arm playOnAwake. */
    private fun stopAll() {
        heldByPause.clear()
        wasPaused = false
        playbacks.keys.toList().forEach { releasePlayback(it) }
        releaseWater()
        scene.children.forEach { go ->
            go.getComponent<AudioComponent>()?.apply {
                awoken = false
                isPlaying = false
                isPaused = false
                playRequested = false
            }
        }
    }

    private fun updateListener() {
        if (!audioEngine.isInitialized) return
        audioEngine.ensureContextCurrent()

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
        // Only a physics body gives the listener a velocity: camera glides, cuts and free-fly are not physical motion
        // and must not Doppler-shift what you hear (AUD-11).
        val body = listenerObject?.getComponent<RigidBody3D>()?.takeIf { it.rawBody != null }
        val velocity = body?.let { Vector3f(it.linearVelocity) } ?: Vector3f()

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

    private class Playback(val clip: String, val source: SoundSource, val stream: StreamingPlayer?) : AutoCloseable {
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
        val pitch = SpatialAudio.dopplerPitch(listenerPos, listenerVel, worldPos, vel, effectiveDoppler(ac))
        return SpatialState(base * attenuation, pitch, worldPos, vel)
    }

    /** Music is never Doppler-shifted, whatever its own factor (AUD-11). */
    private fun effectiveDoppler(ac: AudioComponent) = if (ac.bus == BusId.MUSIC) 0f else ac.dopplerFactor

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

    private fun updateAudioSources(running: Boolean) {
        val currentEntities = mutableSetOf<Int>()

        scene.children.forEach { gameObject ->
            val ac = gameObject.getComponent<AudioComponent>() ?: return@forEach
            val id = gameObject.uId
            currentEntities.add(id)
            val spatial = computeSpatial(gameObject, ac)

            if (running && ac.playOnAwake && !ac.awoken) {
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
            if (!updateStream(id, pb)) {
                ac.isPlaying = false
                return@forEach
            }
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

        // Project-relative or packed sounds become a local file (AST-09).
        val clip = localPath(clipPicker.pick(id, ac.soundFilePath, ac.clips))
        if (clip.isBlank() || clip in failedClips) return
        if (!File(clip).isFile) {
            // Checked on every start so a file added later plays; warned once so a looping source doesn't spam.
            if (missingClips.add(clip)) logger.log("AudioSystem: Sound file not found '$clip' - not played", LogLevel.WARN)
            return
        }

        releasePlayback(id)
        val result = audioEngine.voices.request(id, ac.priority, spatial.gain)
        if (result is VoiceManager.Result.Denied) return
        (result as VoiceManager.Result.Granted<*>).stolen?.let { releaseVoice(it as Int) }

        try {
            val playback = createPlayback(clip, ac)
            playbacks[id] = playback
            applyParams(playback, ac, spatial)
            ac.isPaused = false
            if (playback.stream != null) playback.stream.start() else playback.source.play()
        } catch (e: Exception) {
            releasePlayback(id)
            failedClips.add(clip)
            logger.log("AudioSystem: Cannot play '$clip' - ${e.message}", LogLevel.ERROR)
        }
    }

    /** Refills a streamed source; a decode error mid-stream stops it (logged once per clip). False when stopped. */
    private fun updateStream(id: Int, pb: Playback): Boolean {
        val stream = pb.stream ?: return true
        return try {
            stream.update()
            true
        } catch (e: Exception) {
            releasePlayback(id)
            if (failedClips.add(pb.clip)) logger.log("AudioSystem: Stopped '${pb.clip}' - ${e.message}", LogLevel.ERROR)
            false
        }
    }

    private fun createPlayback(clip: String, ac: AudioComponent): Playback {
        val stream = StreamPolicy.shouldStream(clip, ac.bus, ac.streamMode, File(clip).length())
        val source = audioEngine.createSource(!stream && ac.loops, !ac.is3D)
        try {
            if (stream) {
                val player = StreamingPlayer(PcmDecoders.open(clip, mono = ac.is3D), source.asStreamSink(), ac.loops)
                return Playback(clip, source, player)
            }
            val buffer = assetsManager.getSound(clip)
            if (buffer.bufferId == -1) throw IllegalStateException("sound buffer not loaded")
            source.setBuffer(buffer.bufferId)
            return Playback(clip, source, null)
        } catch (e: Exception) {
            source.close()
            throw e
        }
    }

    /** A stolen voice: an entity's ([AudioComponent]) or an engine water sound's (negative key). */
    private fun releaseVoice(key: Int) = if (key >= 0) releasePlayback(key) else releaseWaterSound(key)

    // --- Water sounds (AUD-01) ---

    /**
     * An engine-owned water voice: a splash at a fixed point, or an ambience that follows the nearest edge. [clip] is
     * the reference it was started with, so an ambience restarts when its water's clip is edited (AUD-15).
     */
    private class WaterSound(
        val playback: Playback,
        val position: Vector3f,
        val volume: Float,
        val ambience: Boolean,
        val clip: String,
        val minDistance: Float,
        val maxDistance: Float,
    )

    private val waterCenter = Vector3f()

    private fun updateWater(running: Boolean) {
        val config = waterAudio
        if (!running || !config.enabled) {
            pendingSplashes.clear()
            if (waterSounds.isNotEmpty()) releaseWater()
            return
        }
        startSplashes(config)
        updateAmbience(config)
        // Splashes stay where they started; the listener moves, so their gain is refreshed every frame.
        waterSounds.entries.filter { !it.value.ambience }.forEach { (key, ws) ->
            if (!ws.playback.source.isPlaying()) {
                releaseWaterSound(key)
                return@forEach
            }
            applyWater(key, ws, splashGain(ws.position, ws.volume, ws.minDistance, ws.maxDistance))
        }
    }

    private fun splashGain(position: Vector3f, volume: Float, minDistance: Float, maxDistance: Float): Float =
        audioEngine.mixer.sourceGain(BusId.SFX, volume) * SpatialAudio.attenuation(
            AttenuationModel.INVERSE_CLAMPED, position.distance(listenerPos), minDistance, maxDistance, 1f,
        )

    private fun startSplashes(defaults: WaterAudioConfig) {
        var started = 0
        while (true) {
            val event = pendingSplashes.poll() ?: break
            // The rest of a burst is dropped, not delayed: a late splash sounds wrong.
            if (started >= defaults.maxSplashesPerFrame) continue
            if (!splashCooldown.tryStart(event.body.uId, clock, defaults.splashCooldown)) continue
            val position = event.body.getComponent<Transform>()?.worldMatrix?.getTranslation(Vector3f()) ?: continue
            val config = defaults.forWater(event.water.getComponent<WaterComponent>())
            val body = event.body.getComponent<RigidBody3D>()?.takeIf { it.rawBody != null }
            val volume = config.splashVolume * WaterAudio.splashGain(body?.linearVelocity ?: Vector3f())
            val clip = splashPicker.pick(SPLASH_PICKER_OWNER, "", config.splashClips)
            val key = nextSplashKey
            nextSplashKey = if (nextSplashKey <= Int.MIN_VALUE + 2) -1 else nextSplashKey - 2
            val gain = splashGain(position, volume, config.splashMinDistance, config.splashMaxDistance)
            val sound = startWaterSound(key, clip, looping = false, config.splashPriority, gain) ?: continue
            WaterSound(
                sound, position, volume, ambience = false, clip, config.splashMinDistance, config.splashMaxDistance,
            ).also {
                waterSounds[key] = it
                applyWater(key, it, gain)
            }
            sound.source.play()
            started++
        }
    }

    /**
     * One ambience per enabled water within reach. Its settings are read every frame (AUD-15), so an inspector edit
     * of the volume or distances is heard at once, and an edited clip restarts the loop.
     */
    private fun updateAmbience(defaults: WaterAudioConfig) {
        val seen = mutableSetOf<Int>()
        scene.children.forEach { go ->
            val water = go.getComponent<WaterComponent>()?.takeIf { it.enabled } ?: return@forEach
            val config = defaults.forWater(water)
            if (config.ambienceClip.isBlank()) return@forEach
            val m = go.getComponent<Transform>()?.takeIf { it.enabled }?.worldMatrix ?: return@forEach
            waterCenter.set(m.m30(), m.m31(), m.m32())
            val key = ambienceKey(go.uId)
            var existing = waterSounds[key]
            if (existing != null && existing.clip != config.ambienceClip) {
                releaseWaterSound(key)
                existing = null
            }
            val point = WaterAudio.nearestSurfacePoint(
                waterCenter, atan2(m.m20(), m.m22()), water.sizeX, water.sizeZ, listenerPos, existing?.position ?: Vector3f()
            )
            val distance = point.distance(listenerPos)
            // A little hysteresis so walking along the limit does not restart the loop every frame.
            val reach = if (existing == null) config.ambienceMaxDistance else config.ambienceMaxDistance * 1.1f
            if (distance > reach) return@forEach
            val gain = audioEngine.mixer.sourceGain(BusId.SFX, config.ambienceVolume) * SpatialAudio.attenuation(
                AttenuationModel.LINEAR_CLAMPED, distance, config.ambienceMinDistance, config.ambienceMaxDistance, 1f,
            )
            val ws = existing ?: run {
                val sound = startWaterSound(key, config.ambienceClip, looping = true, config.ambiencePriority, gain)
                    ?: return@forEach
                WaterSound(
                    sound, point, config.ambienceVolume, ambience = true, config.ambienceClip,
                    config.ambienceMinDistance, config.ambienceMaxDistance,
                ).also {
                    waterSounds[key] = it
                    applyWater(key, it, gain)
                    sound.source.play()
                }
            }
            seen += key
            applyWater(key, ws, gain)
        }
        waterSounds.filter { it.value.ambience && it.key !in seen }.keys.forEach { releaseWaterSound(it) }
    }

    /** The defaults with [water]'s own audio fields over them (AUD-15); the defaults alone without a water. */
    private fun WaterAudioConfig.forWater(water: WaterComponent?): WaterAudioConfig = if (water == null) this else withOverrides(
        ambienceClip = water.ambienceClip,
        ambienceVolume = water.ambienceVolume,
        ambienceMinDistance = water.ambienceMinDistance,
        ambienceMaxDistance = water.ambienceMaxDistance,
        splashClips = water.splashClips,
        splashVolume = water.splashVolume,
        splashMinDistance = water.splashMinDistance,
        splashMaxDistance = water.splashMaxDistance,
    )

    private fun applyWater(key: Int, ws: WaterSound, gain: Float) {
        val source = ws.playback.source
        source.setPosition(ws.position.x, ws.position.y, ws.position.z)
        source.setVolume(gain)
        val bus = audioEngine.mixer.bus(BusId.SFX)
        audioEngine.effects.applySource(source.sourceId, bus.effectiveLowPassGainHF * zoneLowPass, bus.reverbSend * zoneSend)
        audioEngine.voices.updateAudibility(key, gain)
    }

    /** A loaded, positioned (not yet playing) source for an engine water clip, or null (missing, failed, no voice). */
    private fun startWaterSound(key: Int, reference: String, looping: Boolean, priority: Int, gain: Float): Playback? {
        val clip = localPath(reference)
        if (clip.isBlank() || clip in failedClips) return null
        if (!File(clip).isFile) {
            if (missingClips.add(clip)) logger.log("AudioSystem: Sound file not found '$clip' - not played", LogLevel.WARN)
            return null
        }
        val result = audioEngine.voices.request(key, priority, gain)
        if (result is VoiceManager.Result.Denied) return null
        (result as VoiceManager.Result.Granted<*>).stolen?.let { releaseVoice(it as Int) }
        val source = audioEngine.createSource(looping, false)
        return try {
            val buffer = assetsManager.getSound(clip)
            if (buffer.bufferId == -1) throw IllegalStateException("sound buffer not loaded")
            source.setBuffer(buffer.bufferId)
            source.setLooping(looping)
            source.setRelative(false)
            Playback(clip, source, null)
        } catch (e: Exception) {
            source.close()
            audioEngine.voices.release(key)
            failedClips.add(clip)
            logger.log("AudioSystem: Cannot play '$clip' - ${e.message}", LogLevel.ERROR)
            null
        }
    }

    private fun releaseWaterSound(key: Int) {
        waterSounds.remove(key)?.let {
            audioEngine.effects.release(it.playback.source.sourceId)
            it.playback.close()
        }
        heldWater.remove(key)
        audioEngine.voices.release(key)
    }

    private fun releaseWater() {
        waterSounds.keys.toList().forEach { releaseWaterSound(it) }
        heldWater.clear()
        pendingSplashes.clear()
        splashCooldown.clear()
    }

    override fun destroy() {
        isInitialized = false
        releaseWater()
        playbacks.values.forEach { it.close() }
        playbacks.clear()
        audioEngine.voices.clear()
    }

    private companion object {
        const val SPLASH_PICKER_OWNER = -1

        /** Even negative voice keys for ambiences; splashes use odd negative ones, entities their (positive) ids. */
        fun ambienceKey(waterId: Int) = -2 * (waterId + 1)
    }
}
