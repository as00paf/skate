package com.pafoid.skate.engine.net

import com.pafoid.skate.engine.ecs.GameObject
import java.util.concurrent.ConcurrentHashMap

/**
 * A game's predicted player: the deterministic [step] that server and clients both run, and a scripted input source
 * for demos and tests. Games register theirs in [PredictionModels] so engine components can name it by id.
 */
interface PredictionModel {
    val step: PredictionStep

    /** Adds the components [step] reads and writes (beyond `Transform` and `NetState`) to a freshly spawned object. */
    fun prepare(go: GameObject) {}

    /**
     * Input payload for fixed step [tick], chosen like a player would from what the client shows for its own object
     * ([view]). At most [InputCodec.MAX_INPUT_PAYLOAD] bytes.
     */
    fun demoInput(view: GameObject, tick: Int): ByteArray
}

/** Registry of [PredictionModel]s by id (game code registers, engine components look up). */
object PredictionModels {
    private val models = ConcurrentHashMap<String, PredictionModel>()

    fun register(id: String, model: PredictionModel) {
        models[id] = model
    }

    operator fun get(id: String): PredictionModel? = models[id]
}
