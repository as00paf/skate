package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.Scene
import kotlin.reflect.KClass

class SystemManager {
    private val _systems = mutableListOf<System>()
    private val pendingSystems = mutableListOf<System>()
    private var systemsNeedSort = true
    private var accumulator = 0f

    var fixedTimeStep = 1f / 60f
    var maxFixedSteps = 5
    private var loadedScene: Scene? = null
    private var hasStarted = false

    val systems: List<System> get() = _systems

    fun start() {
        sortSystemsIfNeeded()
        systems.forEach { it.start() }
        hasStarted = true
    }

    fun loadScene(scene: Scene) {
        sortSystemsIfNeeded()
        loadedScene = scene
        systems.forEach {
            it.init(scene)
        }
    }

    fun update(dt: Float) {
        sortSystemsIfNeeded()
        // Snapshot: systems may be added/removed (deferred) while updating
        updating = true
        try {
            runPhase(SystemPhase.PRE_UPDATE) { it.update(dt) }

            accumulator += dt
            var steps = 0
            while (accumulator >= fixedTimeStep && steps < maxFixedSteps) {
                runPhase(SystemPhase.FIXED_UPDATE) { it.fixedUpdate(fixedTimeStep) }
                accumulator -= fixedTimeStep
                steps++
            }
            if (steps == maxFixedSteps) accumulator = accumulator.coerceAtMost(fixedTimeStep)

            runPhase(SystemPhase.UPDATE) { it.update(dt) }
            runPhase(SystemPhase.LATE_UPDATE) { it.update(dt) }
        } finally {
            updating = false
        }

        processPendingRemovals()
        processPendingSystems()
    }

    /** Runs the [SystemPhase.RENDER] systems; [alpha] is the fixed-step interpolation factor. */
    fun render() {
        sortSystemsIfNeeded()
        val alpha = if (fixedTimeStep > 0f) (accumulator / fixedTimeStep).coerceIn(0f, 1f) else 0f
        runPhase(SystemPhase.RENDER) { it.render(alpha) }
    }

    /** Systems in execution order (phase, then dependency order, then registration order). */
    val executionOrder: List<System> get() {
        sortSystemsIfNeeded()
        return _systems.toList()
    }

    private var updating = false
    private val pendingRemovals = mutableListOf<System>()

    private inline fun runPhase(phase: SystemPhase, action: (System) -> Unit) {
        for (system in _systems.toTypedArray()) {
            if (system.enabled && system.phase == phase && system !in pendingRemovals) action(system)
        }
    }

    private fun processPendingRemovals() {
        pendingRemovals.toList().forEach { removeSystem(it) }
        pendingRemovals.clear()
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
        if (updating) {
            pendingRemovals.add(system)
            return
        }
        _systems.remove(system)
        pendingSystems.remove(system)
        systemsNeedSort = true
    }

    inline fun <reified T : System> getSystem(): T? {
        return systems.filterIsInstance<T>().firstOrNull()
    }

    private fun sortSystemsIfNeeded() {
        if (systemsNeedSort) {
            val ordered = SystemOrdering.resolve(_systems)
            _systems.clear()
            _systems.addAll(ordered)
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

    fun resetSystemCaches() {
        _systems.forEach { system ->
            system.invalidateCache()
        }
        pendingSystems.forEach { system ->
            system.invalidateCache()
        }
    }

    fun destroy() {
        _systems.forEach { it.destroy() }
        _systems.clear()
        pendingSystems.clear()
        pendingRemovals.clear()
        accumulator = 0f
        loadedScene = null
        hasStarted = false
        systemsNeedSort = false
    }

    // Coarse priority, kept for compatibility: maps to a default phase.
    enum class ExecutionPriority(val phase: SystemPhase) {
        EARLY(SystemPhase.PRE_UPDATE),      // Input, timing systems
        DEFAULT(SystemPhase.UPDATE),        // Physics, animation systems
        LATE(SystemPhase.LATE_UPDATE)       // Camera, audio systems
    }
}
