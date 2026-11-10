package com.pafoid.skate.engine.ecs

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import com.pafoid.skate.engine.ecs.components.DirectionalLightComponent
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import org.joml.Vector3f

/**
 * The scene's sun (REN-26): the first enabled [DirectionalLightComponent] on a live, enabled entity, in scene order.
 * The sun is an entity (default name [NAME]) so the editor draws its icon and direction arrow where it stands, not at
 * the origin. Cached per scene and ECS structure version, so the passes and systems that ask every frame do not walk
 * the scene.
 */
object SceneSun {
    const val NAME = "Sun"

    /** Where new scenes put their sun: above the origin, clear of the default floor and objects. */
    val DEFAULT_POSITION: Vector3f get() = Vector3f(0f, 10f, 0f)

    private var cachedScene: GameObject? = null
    private var cachedVersion = -1L
    private var cached: DirectionalLightComponent? = null

    /** The enabled sun of [scene], or null when it has none (no directional lighting, no sun shadows). */
    fun of(scene: GameObject): DirectionalLightComponent? {
        if (scene !== cachedScene || cachedVersion != EcsChanges.version) {
            cached = find(scene)
            cachedScene = scene
            cachedVersion = EcsChanges.version
        }
        val light = cached ?: return null
        val go = light.gameObject
        return light.takeIf { it.enabled && go != null && !go.isDead && go.isEnabled }
    }

    private fun find(parent: GameObject): DirectionalLightComponent? {
        for (child in parent.children) {
            if (!child.isDead) child.getComponent<DirectionalLightComponent>()?.let { return it }
            find(child)?.let { return it }
        }
        return null
    }

    /** A new "Sun" entity at [DEFAULT_POSITION] with a default directional light. */
    fun create(light: DirectionalLightComponent = DirectionalLightComponent()): GameObject = GameObject(NAME)
        .addComponent(Transform(DEFAULT_POSITION))
        .addComponent(light)

    /**
     * Lighting every new scene starts with: an [EnvironmentComponent] (holds the ambient light), a
     * [DayNightCycleComponent] and a [NAME] entity it drives. Adds only what is missing, so callers may configure the
     * environment before or after.
     */
    fun addDefaults(scene: Scene): Scene {
        if (scene.getComponent<EnvironmentComponent>() == null) scene.addComponent(EnvironmentComponent())
        if (scene.getComponent<DayNightCycleComponent>() == null) scene.addComponent(DayNightCycleComponent())
        if (find(scene) == null) scene.addChild(create())
        return scene
    }
}
