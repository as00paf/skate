package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.assets.terrain.TerrainAssets
import com.pafoid.skate.engine.ecs.components.TerrainComponent
import com.pafoid.skate.engine.ecs.random.RandomService
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.getComponent

/**
 * Keeps every [TerrainComponent]'s chunks built (REN-28 task 3): early in the frame, before physics and rendering read
 * them, it rebuilds a terrain whose settings, stamps or the project seed changed. Runs while paused (edits in a paused
 * Play session still show). Streaming (REN-28 phase D) replaces "all chunks" with the rings around the focus.
 */
class TerrainSystem : System(priority = ExecutionPriority.EARLY) {
    override val runsWhilePaused: Boolean get() = true

    private val terrains = query(TerrainComponent::class)

    /** Terrains rebuilt by the last [update] (tests, stats). */
    var rebuilt = 0
        private set

    override fun update(dt: Float) {
        rebuilt = 0
        val seed = randomService?.seed ?: RandomService.DEFAULT_SEED
        TerrainAssets.poll() // AST-36: hot reload of `.skterrain` assets (editor; throttled)
        terrains.forEach { go ->
            val terrain = go.getComponent<TerrainComponent>()?.takeIf { it.enabled } ?: return@forEach
            // AST-36: the asset's document, then its saved chunks (DATA, or sculpted chunks of a procedural terrain).
            TerrainAssets.beforeBuild(terrain)
            val built = terrain.runtime.ensureBuilt(terrain.key(seed))
            if (built) rebuilt++
            TerrainAssets.afterBuild(terrain, built)
        }
    }
}
