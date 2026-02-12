package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.components.Component

/**
 * Manages the lifecycle and operations of Components that act as Systems within a scene.
 * This class centralizes all System management responsibilities to reduce
 * the burden on the Scene class and improve separation of concerns.
 */
class SystemManager {
    val systems = mutableListOf<Component>()
    val pendingSystems = mutableListOf<Component>()

    /**
     * Adds a Component to the scene as a system. If the scene is running, adds it to pending systems
     * to be processed in the next update cycle.
     */
    fun addSystem(system: Component, isRunning: Boolean = false) {
        if (!isRunning) {
            systems.add(system)
        } else {
            pendingSystems.add(system)
        }
    }

    /**
     * Removes a Component from the scene.
     */
    fun removeSystem(system: Component) {
        systems.remove(system)
        pendingSystems.remove(system)
    }

    /**
     * Gets a Component by its class type.
     */
    inline fun <reified T : Component> getSystem(): T? {
        return systems.firstOrNull { it is T } as T?
    }

    /**
     * Updates all systems in the scene during editor mode.
     */
    fun editorUpdate(dt: Float) {
        systems.forEach { system ->
            system.editorUpdate(dt)
        }

        processPendingSystems()
    }

    /**
     * Updates all systems in the scene during runtime.
     */
    fun update(dt: Float) {
        systems.forEach { system ->
            system.update(dt)
        }

        processPendingSystems()
    }

    /**
     * Processes any pending systems that need to be added to the scene.
     */
    private fun processPendingSystems() {
        pendingSystems.forEach { system ->
            systems.add(system)
            // Note: We don't call start() on systems as they are components attached to game objects
            // and their lifecycle is managed differently than game objects
        }

        pendingSystems.clear()
    }

    /**
     * Destroys all systems managed by this manager.
     */
    fun destroy() {
        systems.clear()
        pendingSystems.clear()
    }
}