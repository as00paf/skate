package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.assets.terrain.TerrainAssets
import com.pafoid.skate.engine.ecs.components.TerrainComponent
import com.pafoid.skate.engine.ecs.random.RandomService
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.terrain.TerrainKey

/**
 * Keeps every [TerrainComponent]'s chunks built (REN-28 task 3): early in the frame, before physics and rendering read
 * them, it rebuilds a terrain whose settings, stamps or the project seed changed. Runs while paused (edits in a paused
 * Play session still show). Streaming (REN-28 phase D) replaces "all chunks" with the rings around the focus.
 */
class TerrainSystem(
    /** Reports a terrain that could not be built (once per settings change). */
    private val report: (String) -> Unit = {},
    /** Seconds the settings of an already built terrain must stay unchanged before it is rebuilt. */
    private val debounceSeconds: Float = DEBOUNCE_SECONDS,
) : System(priority = ExecutionPriority.EARLY) {
    override val runsWhilePaused: Boolean get() = true

    private val terrains = query(TerrainComponent::class)

    /** Terrains rebuilt by the last [update] (tests, stats). */
    var rebuilt = 0
        private set

    private class Pending(var key: TerrainKey, var age: Float)

    private val pending = java.util.IdentityHashMap<TerrainComponent, Pending>()
    private val failed = java.util.IdentityHashMap<TerrainComponent, TerrainKey>()

    override fun update(dt: Float) {
        rebuilt = 0
        val seed = randomService?.seed ?: RandomService.DEFAULT_SEED
        TerrainAssets.poll() // AST-36: hot reload of `.skterrain` assets (editor; throttled)
        val running = scene.isRunning
        terrains.forEach { go ->
            val terrain = go.getComponent<TerrainComponent>() ?: return@forEach
            if (running) terrain.runtime.stateTime += dt // scatter respawn timers run in game time
            if (!terrain.enabled) {
                // A disabled terrain frees its chunks (and with them the GPU slots and colliders); enabling rebuilds
                if (terrain.runtime.key != null) terrain.runtime.clear()
                pending.remove(terrain)
                return@forEach
            }
            // AST-36: the asset's document, then its saved chunks (DATA, or sculpted chunks of a procedural terrain).
            TerrainAssets.beforeBuild(terrain)
            val key = terrain.key(seed)
            val built = if (key == terrain.runtime.key || key == failed[terrain]) false else if (ready(terrain, key, dt)) build(terrain, key) else false
            if (built) rebuilt++
            TerrainAssets.afterBuild(terrain, built)
        }
    }

    /**
     * A first build happens at once; a terrain that is already built waits until its settings stop changing for
     * [debounceSeconds], so dragging a slider in the inspector rebuilds once at the end instead of every frame.
     */
    private fun ready(terrain: TerrainComponent, key: TerrainKey, dt: Float): Boolean {
        if (terrain.runtime.key == null) return true
        val p = pending[terrain]
        if (p == null || p.key != key) { pending[terrain] = Pending(key, 0f); return debounceSeconds <= 0f }
        p.age += dt
        return p.age >= debounceSeconds
    }

    private fun build(terrain: TerrainComponent, key: TerrainKey): Boolean {
        pending.remove(terrain)
        val hadState = terrain.runtime.scatterState.chunkCount > 0
        return try {
            terrain.runtime.ensureBuilt(key).also {
                failed.remove(terrain)
                if (hadState && terrain.runtime.scatterState.chunkCount == 0) {
                    report("Terrain '${terrain.gameObject?.name}': the world changed, remembered scatter state was cleared")
                }
            }
        } catch (e: Exception) {
            // Never throw out of the system manager every frame: report once, retry when the settings change
            failed[terrain] = key
            report("Terrain '${terrain.gameObject?.name}' could not be built: ${e.message}")
            false
        }
    }

    companion object {
        const val DEBOUNCE_SECONDS = 0.25f
    }
}
