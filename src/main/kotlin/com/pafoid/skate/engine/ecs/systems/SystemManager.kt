package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.Scene

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
    private var loadedScene: Scene? = null
    private var hasStarted = false
    private var lastObjectSetVersion: Long = -1
    private var lastSceneComponentVersion: Long = -1
    private val lastObjectComponentVersions = mutableMapOf<Int, Long>()

    // Public read-only view of systems
    val systems: List<System> get() = _systems

    fun start() {
        sortSystemsIfNeeded()
        systems.forEach { it.start() }
        hasStarted = true
    }

    fun loadScene(scene: Scene) {
        sortSystemsIfNeeded()
        loadedScene = scene
        lastObjectSetVersion = scene.objectSetVersion
        snapshotComponentVersions(scene)
        systems.forEach {
            it.init(scene)
        }
    }

    fun update(dt: Float) {
        sortSystemsIfNeeded()
        refreshCachesIfSceneChanged()
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
            loadedScene?.let { scene ->
                system.init(scene)
                if (hasStarted) {
                    system.start()
                }
            }
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
        val scene = loadedScene
        pendingSystems.forEach { system ->
            _systems.add(system)
            if (scene != null) {
                system.init(scene)
            }
            if (hasStarted) {
                system.start()
            }
            systemsNeedSort = true
        }

        pendingSystems.clear()
    }

    private fun refreshCachesIfSceneChanged() {
        val scene = loadedScene ?: return
        if (scene.objectSetVersion != lastObjectSetVersion || hasComponentMutation(scene)) {
            resetSystemCaches()
            lastObjectSetVersion = scene.objectSetVersion
            snapshotComponentVersions(scene)
        }
    }

    private fun hasComponentMutation(scene: Scene): Boolean {
        if (scene.componentMutationVersion != lastSceneComponentVersion) {
            return true
        }

        if (scene.gameObjects.size != lastObjectComponentVersions.size) {
            return true
        }

        for (gameObject in scene.gameObjects) {
            val uid = gameObject.uId
            val currentVersion = gameObject.componentMutationVersion
            val previousVersion = lastObjectComponentVersions[uid]
            if (previousVersion == null || previousVersion != currentVersion) {
                return true
            }
        }

        return false
    }

    private fun snapshotComponentVersions(scene: Scene) {
        lastSceneComponentVersion = scene.componentMutationVersion
        lastObjectComponentVersions.clear()
        scene.gameObjects.forEach { gameObject ->
            lastObjectComponentVersions[gameObject.uId] = gameObject.componentMutationVersion
        }
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
        loadedScene = null
        hasStarted = false
        lastObjectSetVersion = -1
        lastSceneComponentVersion = -1
        lastObjectComponentVersions.clear()
        systemsNeedSort = false
    }
}
