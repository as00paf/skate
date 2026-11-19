package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.assets.PrefabStore
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.TerrainComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.prefab.PrefabAsset
import com.pafoid.skate.engine.ecs.prefab.PrefabService
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.terrain.ScatterInstance
import com.pafoid.skate.engine.utils.toDegreesF
import org.joml.Quaternionf
import org.joml.Vector3f
import java.io.File
import java.util.IdentityHashMap

/** Makes a fresh entity tree for a scatter rule's `prefab` reference; null when it cannot be resolved. */
fun interface ScatterPrefabResolver {
    fun instantiate(prefab: String): GameObject?
}

/**
 * Resolves a prefab reference as a `.prefab` path (project-relative or packed) through [localPath], caching each
 * template. Failures are reported once per reference through [onError] and the rule spawns nothing.
 */
class PrefabFileResolver(
    serializer: Serializer,
    private val localPath: (String) -> String,
    private val onError: (String, Throwable) -> Unit = { _, _ -> },
) : ScatterPrefabResolver {
    private val store = PrefabStore(serializer)
    private val service = PrefabService(serializer)
    private val assets = HashMap<String, PrefabAsset?>()

    override fun instantiate(prefab: String): GameObject? {
        val asset = assets.getOrPut(prefab) {
            runCatching { store.load(File(localPath(prefab))) }.onFailure { onError(prefab, it) }.getOrNull()
        }
        return asset?.let(service::instantiate)
    }
}

/**
 * Spawns the items of prefab scatter rules (REN-28 §7, `ScatterRule.prefab`) as entities under their terrain
 * (ECS-19). They are runtime-only ([GameObject.isRuntimeOnly]): never saved, hidden from the hierarchy, and rebuilt
 * from the terrain's chunks. Each rule keeps at most `maxEntities` items: the ones with the lowest per-item random
 * value, so the kept items spread over the whole terrain and the choice does not depend on chunk order.
 *
 * Items follow chunk changes: when the terrain's runtime version changes, the wanted set is recomputed and diffed by
 * (rule, chunk, item key). Items still wanted keep their entity and state; the others are removed; new ones are
 * spawned, at most [spawnsPerFrame] per frame. An item removed or destroyed by gameplay (a taken prop) stays gone
 * until its chunk changes; remembered per-chunk state is REN-28 §15.10.
 *
 * Terrains ignore their Transform's rotation and scale, and items are placed in terrain-local metres, so a scattered
 * terrain's object should keep rotation 0 and scale 1.
 */
class TerrainScatterEntitySystem(
    private val resolver: ScatterPrefabResolver,
    private val spawnsPerFrame: Int = DEFAULT_SPAWNS_PER_FRAME,
) : System(priority = ExecutionPriority.EARLY) {
    override val runsAfter = listOf(TerrainSystem::class)
    override val runsWhilePaused: Boolean get() = true

    private val terrains = query(TerrainComponent::class, includeDisabled = true)

    /** One placed item: entry index in the generator's scatter entries, the prefab it uses, chunk and item key. */
    data class ItemKey(val entry: Int, val prefab: String, val cx: Int, val cz: Int, val key: Int)

    /** A spawned item and the prefab root's own scale, which the item's scale multiplies. */
    private class Spawned(val go: GameObject, val baseScale: Vector3f)

    private class TerrainState {
        var version = Long.MIN_VALUE
        val items = LinkedHashMap<ItemKey, Spawned>()
        /** Wanted but not spawned yet (spawn budget), in spawn order. */
        val pending = ArrayDeque<Pair<ItemKey, ScatterInstance>>()
        /** Prefabs that failed to resolve this version: not retried every frame. */
        val unresolved = HashSet<String>()
    }

    private val states = IdentityHashMap<TerrainComponent, TerrainState>()

    /** Entities currently spawned for [terrain] (tests, stats). */
    fun spawned(terrain: TerrainComponent): List<GameObject> = states[terrain]?.items?.values?.map { it.go } ?: emptyList()

    override fun update(dt: Float) {
        val seen = java.util.Collections.newSetFromMap(IdentityHashMap<TerrainComponent, Boolean>())
        var budget = spawnsPerFrame
        terrains.forEach { go ->
            val terrain = go.getComponent<TerrainComponent>() ?: return@forEach
            seen += terrain
            val state = states.getOrPut(terrain) { TerrainState() }
            if (!go.isEnabled || !terrain.enabled) {
                clear(go, state)
                state.version = Long.MIN_VALUE
                return@forEach
            }
            dropDetached(go, state)
            if (state.version != terrain.runtime.version) {
                state.version = terrain.runtime.version
                state.unresolved.clear()
                diff(go, terrain, state)
            }
            budget = spawnPending(go, state, budget)
        }
        // Terrains removed from the scene (or a scene replaced on Stop): forget them; their children went with them.
        states.keys.retainAll(seen)
    }

    override fun destroy() {
        states.clear()
        super.destroy()
    }

    /** Items the terrain's chunks want now, capped per rule, keyed for diffing. */
    internal fun wanted(terrain: TerrainComponent): LinkedHashMap<ItemKey, ScatterInstance> {
        val entries = terrain.runtime.generator?.scatterEntries ?: return LinkedHashMap()
        val byEntry = HashMap<Int, MutableList<Triple<Int, Int, ScatterInstance>>>()
        terrain.runtime.chunks.forEach { chunk ->
            chunk.scatter.forEach { item ->
                val rule = entries.getOrNull(item.rule)?.rule ?: return@forEach
                if (rule.prefab.isBlank() || rule.maxEntities <= 0) return@forEach
                byEntry.getOrPut(item.rule) { ArrayList() } += Triple(chunk.cx, chunk.cz, item)
            }
        }
        val out = LinkedHashMap<ItemKey, ScatterInstance>()
        byEntry.keys.sorted().forEach { index ->
            val rule = entries[index].rule
            byEntry.getValue(index)
                .sortedWith(compareBy({ it.third.random }, { it.second }, { it.first }, { it.third.key }))
                .take(rule.maxEntities)
                .forEach { (cx, cz, item) -> out[ItemKey(index, rule.prefab, cx, cz, item.key)] = item }
        }
        return out
    }

    private fun diff(go: GameObject, terrain: TerrainComponent, state: TerrainState) {
        val wanted = wanted(terrain)
        val entries = terrain.runtime.generator?.scatterEntries
        state.items.entries.removeIf { (key, spawned) ->
            val item = wanted[key]
            if (item == null) despawn(go, spawned.go)
            else place(spawned.go, item, entries?.getOrNull(key.entry)?.rule?.alignToNormal ?: 0f, spawned.baseScale)
            item == null
        }
        state.pending.clear()
        wanted.forEach { (key, item) -> if (key !in state.items) state.pending.addLast(key to item) }
    }

    private fun spawnPending(go: GameObject, state: TerrainState, budget: Int): Int {
        var left = budget
        val entries = (go.getComponent<TerrainComponent>() ?: return left).runtime.generator?.scatterEntries
        while (left > 0 && state.pending.isNotEmpty()) {
            val (key, item) = state.pending.removeFirst()
            if (key.prefab in state.unresolved) continue
            val entity = resolver.instantiate(key.prefab)
            if (entity == null) {
                state.unresolved += key.prefab
                continue
            }
            entity.isRuntimeOnly = true
            val transform = entity.getComponent<Transform>()
                ?: Transform().also { entity.components.add(0, it); it.init(entity) }
            val spawned = Spawned(entity, Vector3f(transform.scale))
            place(entity, item, entries?.getOrNull(key.entry)?.rule?.alignToNormal ?: 0f, spawned.baseScale)
            go.addChild(entity)
            state.items[key] = spawned
            left--
        }
        return left
    }

    /** Entities taken out of the terrain by someone else (gameplay, undo) are forgotten, not respawned. */
    private fun dropDetached(go: GameObject, state: TerrainState) {
        state.items.values.removeIf { it.go.parent !== go || it.go.isDead }
    }

    private fun clear(go: GameObject, state: TerrainState) {
        state.items.values.forEach { despawn(go, it.go) }
        state.items.clear()
        state.pending.clear()
    }

    private fun despawn(parent: GameObject, entity: GameObject) {
        if (entity.parent === parent) parent.removeChild(entity)
        entity.destroy()
    }

    companion object {
        /** Spawns per frame across all terrains (REN-28 §15.5 budget). */
        const val DEFAULT_SPAWNS_PER_FRAME = 32

        private val UP = Vector3f(0f, 1f, 0f)

        /**
         * Puts [entity] on [item]: terrain-local position, yaw, the item's uniform scale times [baseScale] (the prefab
         * root's own scale) and a tilt towards the ground normal by [alignToNormal].
         */
        internal fun place(entity: GameObject, item: ScatterInstance, alignToNormal: Float, baseScale: Vector3f) {
            val t = entity.getComponent<Transform>() ?: return
            t.translation.set(item.x, item.y, item.z)
            val tilt = Quaternionf().rotationTo(UP, Vector3f(item.nx, item.ny, item.nz).normalize())
            val q = Quaternionf().slerp(tilt, alignToNormal.coerceIn(0f, 1f)).rotateY(item.yaw)
            val euler = q.getEulerAnglesXYZ(Vector3f())
            t.rotation.set(euler.x.toDegreesF(), euler.y.toDegreesF(), euler.z.toDegreesF())
            t.scale.set(baseScale).mul(item.scale)
        }
    }
}
