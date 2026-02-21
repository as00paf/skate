package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.Scene

/**
 * Execution priority for ECS systems.
 * Systems are updated in priority order (EARLY first, LATE last).
 */
enum class ExecutionPriority {
    EARLY,      // Input, timing systems
    DEFAULT,    // Physics, animation systems
    LATE        // Rendering, UI systems
}

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
     * Updates the system during editor mode.
     */
    open fun editorUpdate(dt: Float) {}

    /**
     * Renders ImGui interface for the system (if applicable).
     */
    open fun imgui() {}

    /**
     * Called when the system is destroyed.
     */
    open fun destroy() {}
}