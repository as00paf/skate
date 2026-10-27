package com.pafoid.skate.engine.audio

import kotlinx.serialization.Serializable
import org.joml.Vector3f

/** Subset of the OpenAL EFX standard reverb parameters. */
data class ReverbParams(
    val density: Float = 1f,
    val diffusion: Float = 1f,
    val gain: Float = 0.32f,
    val gainHF: Float = 0.89f,
    val decayTime: Float = 1.49f,
    val reflectionsGain: Float = 0.05f,
    val lateReverbGain: Float = 1.26f
)

@Serializable
enum class ReverbPreset(val params: ReverbParams) {
    GENERIC(ReverbParams()),
    ROOM(ReverbParams(decayTime = 0.4f, gain = 0.32f, gainHF = 0.59f, reflectionsGain = 0.15f, lateReverbGain = 0.77f)),
    HALL(ReverbParams(decayTime = 3.9f, gain = 0.32f, gainHF = 0.7f, reflectionsGain = 0.24f, lateReverbGain = 0.71f)),
    CAVE(ReverbParams(decayTime = 2.9f, diffusion = 1f, gain = 0.32f, gainHF = 1f, reflectionsGain = 0.5f, lateReverbGain = 1.7f)),
    BATHROOM(ReverbParams(decayTime = 1.5f, gain = 0.32f, gainHF = 0.25f, reflectionsGain = 0.17f, lateReverbGain = 2.0f)),
    OUTDOORS(ReverbParams(decayTime = 1.2f, gain = 0.1f, gainHF = 0.4f, reflectionsGain = 0.02f, lateReverbGain = 0.3f))
}

/** A spherical reverb zone as seen by the resolver (decoupled from the ECS component). */
data class ZoneShape(
    val id: Int,
    val center: Vector3f,
    val radius: Float,
    val priority: Int,
    val params: ReverbParams,
    val sendLevel: Float,
    val lowPassGainHF: Float
)

object ReverbZoneResolver {
    /** The zone containing [listener]: highest priority wins, then the nearest centre. Null outside all zones. */
    fun resolve(listener: Vector3f, zones: List<ZoneShape>): ZoneShape? =
        zones.filter { it.center.distance(listener) <= it.radius }
            .minWithOrNull(compareBy<ZoneShape>({ -it.priority }, { it.center.distanceSquared(listener) }))
}

/** What the effects backend needs, hiding OpenAL EFX so [EffectsManager] can be tested. */
interface EffectsApi {
    fun isSupported(): Boolean
    fun createSlot(): Int
    fun createReverbEffect(): Int
    fun setReverbParams(effect: Int, params: ReverbParams)
    fun attachEffect(slot: Int, effect: Int)
    fun createFilter(): Int
    fun setLowPass(filter: Int, gain: Float, gainHF: Float)
    fun setDirectFilter(source: Int, filter: Int)
    fun setSend(source: Int, slot: Int, filter: Int)
    fun deleteSlot(slot: Int)
    fun deleteEffect(effect: Int)
    fun deleteFilter(filter: Int)
}

/**
 * One shared reverb slot plus per-source low-pass/send filters. Values are only pushed to
 * OpenAL when they change.
 */
class EffectsManager(private val api: EffectsApi) : AutoCloseable {

    private class SourceFx(var directFilter: Int = 0, var sendFilter: Int = 0) {
        var lastHF = 1f
        var lastSend = -1f
        var applied = false
    }

    var isSupported = false
        private set
    private var slot = 0
    private var effect = 0
    private var reverb: ReverbParams? = null
    private val sources = HashMap<Int, SourceFx>()

    fun init(): Boolean {
        if (isSupported) return true
        if (!api.isSupported()) return false
        slot = api.createSlot()
        effect = api.createReverbEffect()
        isSupported = true
        return true
    }

    /** Sets the active reverb, or disables it with null. */
    fun setReverb(params: ReverbParams?) {
        if (!isSupported || params == reverb) return
        reverb = params
        if (params == null) {
            api.attachEffect(slot, 0)
        } else {
            api.setReverbParams(effect, params)
            api.attachEffect(slot, effect)
        }
        // Sends depend on whether reverb is active, so re-evaluate every source.
        sources.values.forEach { it.applied = false }
    }

    /** [directGainHF] 1 = no low-pass; [sendGain] 0 = no reverb for this source. */
    fun applySource(sourceId: Int, directGainHF: Float, sendGain: Float) {
        if (!isSupported || sourceId < 0) return
        val hf = directGainHF.coerceIn(0f, 1f)
        val send = if (reverb == null) 0f else sendGain.coerceIn(0f, 1f)
        val fx = sources.getOrPut(sourceId) { SourceFx() }
        if (fx.applied && fx.lastHF == hf && fx.lastSend == send) return
        fx.applied = true

        if (hf != fx.lastHF || fx.lastSend < 0f) {
            if (hf >= 0.999f) {
                api.setDirectFilter(sourceId, 0)
            } else {
                if (fx.directFilter == 0) fx.directFilter = api.createFilter()
                api.setLowPass(fx.directFilter, 1f, hf)
                api.setDirectFilter(sourceId, fx.directFilter)
            }
        }
        if (send != fx.lastSend) {
            if (send <= 0.001f) {
                api.setSend(sourceId, 0, 0)
            } else {
                if (fx.sendFilter == 0) fx.sendFilter = api.createFilter()
                api.setLowPass(fx.sendFilter, send, 1f)
                api.setSend(sourceId, slot, fx.sendFilter)
            }
        }
        fx.lastHF = hf
        fx.lastSend = send
    }

    /** Frees the filters of a source that is going away. */
    fun release(sourceId: Int) {
        val fx = sources.remove(sourceId) ?: return
        if (fx.directFilter != 0) api.deleteFilter(fx.directFilter)
        if (fx.sendFilter != 0) api.deleteFilter(fx.sendFilter)
    }

    override fun close() {
        if (!isSupported) return
        isSupported = false
        sources.values.forEach {
            if (it.directFilter != 0) api.deleteFilter(it.directFilter)
            if (it.sendFilter != 0) api.deleteFilter(it.sendFilter)
        }
        sources.clear()
        api.attachEffect(slot, 0)
        api.deleteEffect(effect)
        api.deleteSlot(slot)
        slot = 0
        effect = 0
        reverb = null
    }
}
