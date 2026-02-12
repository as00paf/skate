package com.pafoid.skate.engine.ecs.systems

/**
 * Manages the lifecycle and operations of Systems within a scene.
 * This class centralizes all System management responsibilities to reduce
 * the burden on the Scene class and improve separation of concerns.
 */
class SystemManager {
    val systems = mutableListOf<System>()
    val pendingSystems = mutableListOf<System>()

    /**
     * Adds a System to the scene. If the scene is running, adds it to pending systems
     * to be processed in the next update cycle.
     */
    fun addSystem(system: System, isRunning: Boolean = false) {
        if (!isRunning) {
            systems.add(system)
        } else {
            pendingSystems.add(system)
        }
    }

    /**
     * Removes a System from the scene.
     */
    fun removeSystem(system: System) {
        systems.remove(system)
        pendingSystems.remove(system)
    }

    /**
     * Gets a System by its class type.
     */
    inline fun <reified T : System> getSystem(): T? {
        return systems.firstOrNull { it is T } as T?
    }

    /**
     * Updates all systems in the scene during editor mode.
     */
    fun editorUpdate(dt: Float) {
        systems.forEach { system ->
            if (system.enabled) {
                system.editorUpdate(dt)
            }
        }

        processPendingSystems()
    }

    /**
     * Updates all systems in the scene during runtime.
     */
    fun update(dt: Float) {
        systems.forEach { system ->
            if (system.enabled) {
                system.update(dt)
            }
        }

        processPendingSystems()
    }

    /**
     * Processes any pending systems that need to be added to the scene.
     */
    private fun processPendingSystems() {
        pendingSystems.forEach { system ->
            systems.add(system)
            system.start()
        }

        pendingSystems.clear()
    }

    /**
     * Destroys all systems managed by this manager.
     */
    fun destroy() {
        systems.forEach { it.destroy() }
        systems.clear()
        pendingSystems.clear()
    }
}