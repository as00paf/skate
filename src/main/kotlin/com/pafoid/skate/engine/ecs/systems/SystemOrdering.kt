package com.pafoid.skate.engine.ecs.systems

/** Frame phases, executed in declaration order. */
enum class SystemPhase { PRE_UPDATE, FIXED_UPDATE, UPDATE, LATE_UPDATE, RENDER }

class SystemOrderingException(message: String) : IllegalStateException(message)

object SystemOrdering {
    /**
     * Orders [systems] by phase, then topologically by `runsAfter`/`runsBefore` within a phase.
     * Ties are broken by registration order, so the result is deterministic.
     * Dependencies on systems that are not registered are ignored; cross-phase dependencies are
     * already satisfied by the phase order; cycles throw [SystemOrderingException].
     */
    fun resolve(systems: List<System>): List<System> {
        val result = ArrayList<System>(systems.size)
        for (phase in SystemPhase.values()) {
            result.addAll(resolvePhase(systems.filter { it.phase == phase }))
        }
        return result
    }

    private fun resolvePhase(phaseSystems: List<System>): List<System> {
        val indexOf = phaseSystems.withIndex().associate { it.value to it.index }
        val after = phaseSystems.associateWith { mutableSetOf<System>() } // system -> its prerequisites

        fun matches(declared: kotlin.reflect.KClass<out System>) = phaseSystems.filter { declared.java.isInstance(it) }

        for (system in phaseSystems) {
            system.runsAfter.forEach { declared -> matches(declared).filter { it !== system }.forEach { after.getValue(system).add(it) } }
            system.runsBefore.forEach { declared -> matches(declared).filter { it !== system }.forEach { after.getValue(it).add(system) } }
        }

        val remaining = phaseSystems.toMutableList()
        val ordered = ArrayList<System>(phaseSystems.size)
        val done = HashSet<System>()
        while (remaining.isNotEmpty()) {
            val next = remaining.filter { s -> after.getValue(s).all { it in done } }.minByOrNull { indexOf.getValue(it) }
                ?: throw SystemOrderingException(
                    "Cyclic system dependency among: " + remaining.joinToString { it.javaClass.simpleName }
                )
            ordered.add(next)
            done.add(next)
            remaining.remove(next)
        }
        return ordered
    }
}
