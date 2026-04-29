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

    fun start() {
        systems.forEach { it.start() }
    }

    fun update(dt: Float) {
        sortSystemsIfNeeded()
        _systems.forEach { system ->
            if (system.enabled) {
                system.update(dt)
            }
        }

        processPendingSystems()
    }

    fun addSystem(system: System, isRunning: Boolean = false) {
        if (!isRunning) {
            _systems.add(system)
            systemsNeedSort = true
        } else {
            pendingSystems.add(system)
        }
    }

    fun removeSystem(system: System) {
        _systems.remove(system)
        pendingSystems.remove(system)
        systemsNeedSort = true
    }

    inline fun <reified T : System> getSystem(): T? {
        return systems.filterIsInstance<T>().firstOrNull()
    }

    private fun sortSystemsIfNeeded() {
        if (systemsNeedSort) {
            _systems.sortBy { it.priority.ordinal }
            systemsNeedSort = false
        }
    }

    private fun processPendingSystems() {
        pendingSystems.forEach { system ->
            _systems.add(system)
            system.start()
            systemsNeedSort = true
        }

        pendingSystems.clear()
    }

    fun resetSystemCaches() {
        _systems.forEach { system ->
            system.invalidateCaches()
        }
        pendingSystems.forEach { system ->
            system.invalidateCaches()
        }
    }

    fun destroy() {
        _systems.forEach { it.destroy() }
        _systems.clear()
        pendingSystems.clear()
        systemsNeedSort = false
    }
}