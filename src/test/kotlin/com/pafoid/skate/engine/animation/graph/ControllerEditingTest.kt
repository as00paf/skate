package com.pafoid.skate.engine.animation.graph

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ControllerEditingTest {

    private fun clip(x: Float) = ClipMotion("c$x", 1f) { _, out -> out.translations[0] = x }
    private val clips = mapOf("idle" to clip(0f), "walk" to clip(10f), "run" to clip(20f))

    private fun asset() = AnimationControllerAsset(
        "loco", "move",
        listOf(StateDef("move", "locomotion"), StateDef("jump", "idle")),
        listOf(TransitionDef("move", "jump", listOf(TransitionCondition("jump", ConditionOp.TRIGGER)))),
        motions = mapOf(
            "locomotion" to Blend1DDef("speed", listOf(Blend1DChild(0f, ClipRef("idle")), Blend1DChild(1f, ClipRef("walk")))),
        ),
    )

    @Test
    fun `Json_RoundTripsBlendTreesAndStates`() {
        val original = asset()
        val copy = AnimationControllerAsset.fromJson(original.toJson())
        assertEquals(original, copy)
        assertTrue(original.toJson().contains("blend1d"))
    }

    @Test
    fun `Motions_BuildBlendTreeFromDefAndResolveStateMotionKey`() {
        val a = asset()
        val trees = a.motions.mapValues { (_, d) -> d.build { clips[it] } }
        val m = AnimationStateMachine(a, clips + trees, 1)
        m.parameters.setFloat("speed", 0.5f)
        m.update(0.1f)
        assertEquals(5f, m.pose.translations[0], 1e-4f)
    }

    @Test
    fun `Motions_UnknownClipFailsToBuild_AndIsReportedByIssues`() {
        val def = Blend1DDef("s", listOf(Blend1DChild(0f, ClipRef("ghost"))))
        assertThrows(IllegalArgumentException::class.java) { def.build { clips[it] } }
        val a = asset().copy(motions = mapOf("locomotion" to def))
        assertEquals(listOf("Motion 'locomotion' uses unknown clip 'ghost'"), a.issues(clips.keys))
    }

    @Test
    fun `Issues_ReportsUnknownStateMotionAndEventTimeOutOfRange`() {
        val a = AnimationControllerAsset("c", "a", listOf(StateDef("a", "nope", events = listOf(AnimEventDef(1.5f, "x")))))
        assertEquals(2, a.issues(clips.keys).size)
    }

    @Test
    fun `BlendEditing_AddMoveAndRemoveChildren`() {
        val e = ControllerEditor(asset())
        e.updateBlend1D("locomotion") { it.withChild(2f, ClipRef("run")).withThreshold(1, 0.5f) }
        val tree = e.asset.motions.getValue("locomotion") as Blend1DDef
        assertEquals(listOf(0f, 0.5f, 2f), tree.children.map { it.threshold })
        e.updateBlend1D("locomotion") { it.withoutChild(0) }
        assertEquals(2, (e.asset.motions.getValue("locomotion") as Blend1DDef).children.size)
        assertThrows(IllegalArgumentException::class.java) {
            e.updateBlend1D("locomotion") { it.withoutChild(0).withoutChild(0) }
        }
    }

    @Test
    fun `Editor_RenameUpdatesTransitionsAndDefault`() {
        val e = ControllerEditor(asset())
        e.renameState("move", "locomote")
        assertEquals("locomote", e.asset.defaultState)
        assertEquals("locomote", e.asset.transitions.single().from)
    }

    @Test
    fun `Editor_RemoveStateDropsTransitions_AndFailedEditsLeaveAssetUnchanged`() {
        val e = ControllerEditor(asset())
        val before = e.asset
        assertThrows(IllegalArgumentException::class.java) { e.addTransition(TransitionDef("move", "ghost")) }
        assertThrows(IllegalArgumentException::class.java) { e.removeMotion("locomotion") } // still used by 'move'
        assertEquals(before, e.asset)

        e.removeState("jump")
        assertTrue(e.asset.transitions.isEmpty())
        assertEquals(listOf("move"), e.asset.states.map { it.name })
        assertThrows(IllegalArgumentException::class.java) { e.removeState("move") }
    }

    @Test
    fun `Editor_TransitionPriorityAndEventsStaySorted`() {
        val e = ControllerEditor(asset())
        e.addTransition(TransitionDef("jump", "move", hasExitTime = true))
        e.moveTransition(1, 0)
        assertEquals("jump", e.asset.transitions[0].from)
        e.addEvent("move", AnimEventDef(0.8f, "b"))
        e.addEvent("move", AnimEventDef(0.2f, "a"))
        assertEquals(listOf("a", "b"), e.asset.states.first { it.name == "move" }.events.map { it.name })
    }

    @Test
    fun `Blend2D_EditingChangesPositions`() {
        val def = Blend2DDef("x", "y", listOf(Blend2DChild(0f, 0f, ClipRef("idle"))))
            .withChild(1f, 0f, ClipRef("walk")).withPosition(1, 2f, 3f)
        assertEquals(Blend2DChild(2f, 3f, ClipRef("walk")), def.children[1])
    }
}
