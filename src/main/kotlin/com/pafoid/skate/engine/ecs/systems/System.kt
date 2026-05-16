package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.config.ExecutionPriority

/**
 * Abstract base class for systems in the ECS architecture.
 * Systems are global entities that operate on collections of GameObjects/components
 * rather than being attached to individual GameObjects like Components.
 *
 * Systems are updated in priority order (EARLY first, LATE last).
 * Use priority values to ensure systems with dependencies execute in the correct order.
 *
 * @param priority Execution priority. Default is DEFAULT.
 */
abstract class System(
    val priority: ExecutionPriority = ExecutionPriority.DEFAULT
) {
    var enabled = true

    /**
     * Display name for this system, used in UI (e.g., SystemsWindow).
     * Defaults to the simple class name but can be overridden for custom display.
     */
    open val displayName: String get() = javaClass.simpleName
    
    protected lateinit var scene: Scene

    /**
     * Initializes the system with the scene it operates in.
     */
    open fun init(scene: Scene) {
        this.scene = scene
    }

    /**
     * Called once when the system starts running.
     */
    open fun start() {}

    /**
     * Updates the system during runtime.
     */
    open fun update(dt: Float) {}

    /**
     * Renders ImGui interface for the system (if applicable).
     */
    open fun imgui() {}

    /**
     * Called when the system is destroyed.
     */
    open fun destroy() {}

    /**
     * Called when the scene's GameObject list changes (e.g. after reload).
     * Subsystems that cache GameObject references should clear them here.
     * Default implementation does nothing.
     */
    open fun invalidateCaches() {}
}