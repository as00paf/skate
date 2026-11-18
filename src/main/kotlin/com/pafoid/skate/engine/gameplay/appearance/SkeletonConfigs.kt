package com.pafoid.skate.engine.gameplay.appearance

import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.PlayerController
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.gameplay.CharacterConfigs
import com.pafoid.skate.engine.getComponent
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

/**
 * Loads `*.skeleton.json` files (GPL-24 §6.1) like character configs, and binds them to rigs. A blank path is the
 * built-in [SkeletonConfig.HUMANOID]. A character's config comes from its behavior
 * ([com.pafoid.skate.engine.gameplay.CharacterBehavior.skeletonConfig]); characters without one use the humanoid.
 */
object SkeletonConfigs {
    private val logger = Logger.getLogger("SkeletonConfigs")
    private val configs = ConcurrentHashMap<String, SkeletonConfig>()
    private val resolved = IdentityHashMap<Skeleton, MutableMap<SkeletonConfig, ResolvedSkeleton>>()

    fun load(path: String): SkeletonConfig =
        if (path.isBlank()) SkeletonConfig.HUMANOID
        else configs.getOrPut(path) { CharacterConfigs.load(path) { SkeletonConfig.HUMANOID } }

    /** The skeleton config [character] uses. */
    fun configOf(character: GameObject): SkeletonConfig =
        load(character.getComponent<PlayerController>()?.behavior?.skeletonConfig.orEmpty())

    /** [config] bound to [skeleton], cached; unresolved sockets are logged once per rig. */
    @Synchronized
    fun resolve(config: SkeletonConfig, skeleton: Skeleton): ResolvedSkeleton =
        resolved.getOrPut(skeleton) { HashMap() }.getOrPut(config) {
            config.resolve(skeleton).also { r -> r.warnings.forEach(logger::warning) }
        }

    /** [character]'s config bound to its rig, or null without a `SkeletonComponent`. */
    fun resolveFor(character: GameObject): ResolvedSkeleton? {
        val skeleton = character.getComponent<SkeletonComponent>()?.pose?.skeleton ?: return null
        return resolve(configOf(character), skeleton)
    }

    /** Equip slots [character] offers: the enabled ones on its rig, or every config slot without a rig. */
    fun slotsOf(character: GameObject): Set<String> =
        resolveFor(character)?.enabledSlots ?: configOf(character).slotIds.toSet()

    /** Forgets loaded files and bindings (Play start), so edited configs are picked up. */
    @Synchronized
    fun clear() {
        configs.clear()
        resolved.clear()
    }
}
