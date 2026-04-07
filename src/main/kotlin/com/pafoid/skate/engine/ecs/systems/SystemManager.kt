package com.pafoid.skate.engine.ecs.systems

/**
 * Manages the lifecycle and operations of Systems within a scene.
 * This class centralizes all System management responsibilities to reduce
 * the burden on the Scene class and improve separation of concerns.
 *
 * Systems are executed in priority order (lowest priority first) to ensure
 * proper dependency ordering (e.g., input systems before physics systems).
 */
class SystemManager {
    private val _systems = mutableListOf<System>()
    private val pendingSystems = mutableListOf<System>()
    private var systemsNeedSort = true

    // Public read-only view of systems
    val systems: List<System> get() = _systems

    /**
     * Adds a System to the scene. If the scene is running, adds it to pending systems
     * to be processed in the next update cycle.
     */
    fun addSystem(system: System, isRunning: Boolean = false) {
        if (!isRunning) {
            _systems.add(system)
            systemsNeedSort = true
        } else {
            pendingSystems.add(system)
        }
    }

    /**
     * Removes a System from the scene.
     */
    fun removeSystem(system: System) {
        _systems.remove(system)
        pendingSystems.remove(system)
        systemsNeedSort = true
    }

    /**
     * Gets a System by its class type.
     */
    inline fun <reified T : System> getSystem(): T? {
        return systems.filterIsInstance<T>().firstOrNull()
    }

    /**
     * Updates all systems in the scene during editor mode.
     * Systems are executed in priority order (lowest first).
     */
    fun editorUpdate(dt: Float) {
        sortSystemsIfNeeded()
        _systems.forEach { system ->
            if (system.enabled) {
                system.editorUpdate(dt)
            }
        }

        processPendingSystems()
    }

    /**
     * Updates all systems in the scene during runtime.
     * Systems are executed in priority order (lowest first).
     */
    fun update(dt: Float) {
        sortSystemsIfNeeded()
        _systems.forEach { system ->
            if (system.enabled) {
                system.update(dt)
            }
        }

        processPendingSystems()
    }

    /**
     * Sorts systems by priority if needed.
     * Called before each update cycle when systems have been added/removed.
     * Priority order: EARLY → DEFAULT → LATE
     */
    private fun sortSystemsIfNeeded() {
        if (systemsNeedSort) {
            _systems.sortBy { it.priority.ordinal }
            systemsNeedSort = false
        }
    }

    /**
     * Processes any pending systems that need to be added to the scene.
     */
    private fun processPendingSystems() {
        pendingSystems.forEach { system ->
            _systems.add(system)
            system.start()
            systemsNeedSort = true
        }

        pendingSystems.clear()
    }

    /**
     * Resets all system caches.
     * Call this when the scene's GameObject list changes (e.g. after reload)
     * so that systems rebuild their cached references.
     */
    fun resetSystemCaches() {
        _systems.forEach { system ->
            system.invalidateCaches()
        }
        pendingSystems.forEach { system ->
            system.invalidateCaches()
        }
    }

    /**
     * Destroys all systems managed by this manager.
     */
    fun destroy() {
        _systems.forEach { it.destroy() }
        _systems.clear()
        pendingSystems.clear()
        systemsNeedSort = false
    }
}