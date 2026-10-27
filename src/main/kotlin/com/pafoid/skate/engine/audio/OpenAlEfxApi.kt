package com.pafoid.skate.engine.audio

import org.lwjgl.openal.AL10
import org.lwjgl.openal.AL11
import org.lwjgl.openal.ALC10
import org.lwjgl.openal.EXTEfx

object OpenAlEfxApi : EffectsApi {
    override fun isSupported(): Boolean {
        return runCatching {
            val device = ALC10.alcGetContextsDevice(ALC10.alcGetCurrentContext())
            device != 0L && ALC10.alcIsExtensionPresent(device, "ALC_EXT_EFX")
        }.getOrDefault(false)
    }

    override fun createSlot() = EXTEfx.alGenAuxiliaryEffectSlots()

    override fun createReverbEffect(): Int {
        val effect = EXTEfx.alGenEffects()
        EXTEfx.alEffecti(effect, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_REVERB)
        return effect
    }

    override fun setReverbParams(effect: Int, params: ReverbParams) {
        EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_DENSITY, params.density)
        EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_DIFFUSION, params.diffusion)
        EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_GAIN, params.gain)
        EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_GAINHF, params.gainHF)
        EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_DECAY_TIME, params.decayTime)
        EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_REFLECTIONS_GAIN, params.reflectionsGain)
        EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_LATE_REVERB_GAIN, params.lateReverbGain)
    }

    override fun attachEffect(slot: Int, effect: Int) =
        EXTEfx.alAuxiliaryEffectSloti(slot, EXTEfx.AL_EFFECTSLOT_EFFECT, effect)

    override fun createFilter(): Int {
        val filter = EXTEfx.alGenFilters()
        EXTEfx.alFilteri(filter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS)
        return filter
    }

    override fun setLowPass(filter: Int, gain: Float, gainHF: Float) {
        EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAIN, gain)
        EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAINHF, gainHF)
    }

    override fun setDirectFilter(source: Int, filter: Int) =
        AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, filter)

    override fun setSend(source: Int, slot: Int, filter: Int) =
        AL11.alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER, slot, 0, filter)

    override fun deleteSlot(slot: Int) = EXTEfx.alDeleteAuxiliaryEffectSlots(slot)
    override fun deleteEffect(effect: Int) = EXTEfx.alDeleteEffects(effect)
    override fun deleteFilter(filter: Int) = EXTEfx.alDeleteFilters(filter)
}
