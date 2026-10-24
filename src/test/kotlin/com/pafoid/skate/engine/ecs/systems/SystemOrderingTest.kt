package com.pafoid.skate.engine.ecs.systems

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.reflect.KClass
import kotlin.test.assertEquals

class SystemOrderingTest {

    private val log = mutableListOf<String>()

    private open inner class Probe(
        private val tag: String,
        override val phase: SystemPhase = SystemPhase.UPDATE,
        override val runsAfter: List<KClass<out System>> = emptyList(),
        override val runsBefore: List<KClass<out System>> = emptyList(),
    ) : System() {
        override fun update(dt: Float) { log.add(tag) }
        override fun fixedUpdate(fixedDt: Float) { log.add("fixed:$tag") }
        override fun render(alpha: Float) { log.add("render:$tag") }
    }

    private inner class A(phase: SystemPhase = SystemPhase.UPDATE, after: List<KClass<out System>> = emptyList(), before: List<KClass<out System>> = emptyList()) : Probe("A", phase, after, before)
    private inner class B(phase: SystemPhase = SystemPhase.UPDATE, after: List<KClass<out System>> = emptyList(), before: List<KClass<out System>> = emptyList()) : Probe("B", phase, after, before)
    private inner class C(phase: SystemPhase = SystemPhase.UPDATE, after: List<KClass<out System>> = emptyList(), before: List<KClass<out System>> = emptyList()) : Probe("C", phase, after, before)

    private fun names(systems: List<System>) = systems.map { it.javaClass.simpleName }

    @Test
    fun SystemOrdering_OrdersByPhaseThenRegistration() {
        val order = SystemOrdering.resolve(listOf(C(SystemPhase.LATE_UPDATE), B(), A(SystemPhase.PRE_UPDATE)))
        assertEquals(listOf("A", "B", "C"), names(order))
    }

    @Test
    fun SystemOrdering_RunsAfterAndRunsBeforeReorderWithinPhase() {
        val order = SystemOrdering.resolve(listOf(A(after = listOf(C::class)), B(before = listOf(C::class)), C()))
        assertEquals(listOf("B", "C", "A"), names(order))
    }

    @Test
    fun SystemOrdering_IsDeterministicForTies() {
        val systems = listOf(B(), A(), C())
        repeat(5) { assertEquals(listOf("B", "A", "C"), names(SystemOrdering.resolve(systems))) }
    }

    @Test
    fun SystemOrdering_IgnoresMissingAndCrossPhaseDependencies() {
        val order = SystemOrdering.resolve(listOf(A(after = listOf(C::class)), C(SystemPhase.LATE_UPDATE)))
        assertEquals(listOf("A", "C"), names(order))
    }

    @Test
    fun SystemOrdering_CycleThrows() {
        assertThrows<SystemOrderingException> {
            SystemOrdering.resolve(listOf(A(after = listOf(B::class)), B(after = listOf(A::class))))
        }
    }

    @Test
    fun SystemManager_RunsPhasesInOrderWithFixedStepsAndRender() {
        val manager = SystemManager()
        manager.addSystem(Probe("late", SystemPhase.LATE_UPDATE))
        manager.addSystem(Probe("fixed", SystemPhase.FIXED_UPDATE))
        manager.addSystem(Probe("update"))
        manager.addSystem(Probe("pre", SystemPhase.PRE_UPDATE))
        manager.addSystem(Probe("render", SystemPhase.RENDER))

        manager.update(manager.fixedTimeStep * 2.5f)
        manager.render()

        assertEquals(
            listOf("pre", "fixed:fixed", "fixed:fixed", "update", "late", "render:render"),
            log
        )
    }

    @Test
    fun SystemManager_RemoveDuringUpdateIsDeferred() {
        val manager = SystemManager()
        val victim = Probe("victim")
        manager.addSystem(object : System() {
            override fun update(dt: Float) { manager.removeSystem(victim) }
        })
        manager.addSystem(victim)

        manager.update(0.01f)
        assertEquals(1, manager.systems.size)
        assertEquals(emptyList<String>(), log)
    }
}
