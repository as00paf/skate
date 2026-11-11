package com.pafoid.skate.editor.animation.controllereditor

import com.pafoid.skate.editor.animation.controllereditor.ControllerGraphView.Companion.NODE_H
import com.pafoid.skate.editor.animation.controllereditor.ControllerGraphView.Companion.NODE_W
import com.pafoid.skate.engine.animation.graph.AnimationControllerAsset
import com.pafoid.skate.engine.animation.graph.AnimationControllerAsset.Companion.ANY_STATE_NODE
import com.pafoid.skate.engine.animation.graph.ConditionOp
import com.pafoid.skate.engine.animation.graph.NodePos
import com.pafoid.skate.engine.animation.graph.StateDef
import com.pafoid.skate.engine.animation.graph.TransitionCondition
import com.pafoid.skate.engine.animation.graph.TransitionDef
import com.pafoid.skate.engine.assets.Assets
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ControllerGraphViewTest {
    private val asset = AnimationControllerAsset(
        "c", "a", listOf(StateDef("a", ""), StateDef("b", ""), StateDef("c", "")),
        listOf(
            TransitionDef("a", "b", hasExitTime = true),
            TransitionDef("b", "a", hasExitTime = true),
            TransitionDef("a", "b", listOf(TransitionCondition("go", ConditionOp.TRUE))),
            TransitionDef(null, "c", listOf(TransitionCondition("c", ConditionOp.TRUE))),
        ),
    )

    @Test
    fun AutoLayout_ColumnsByDistanceFromTheDefault_NoOverlaps() {
        val skater = AnimationControllerAsset.fromJson(javaClass.classLoader.getResourceAsStream(Assets.Configs.SKATER_CONTROLLER)!!.readBytes().toString(Charsets.UTF_8))
        for (a in listOf(asset, skater)) {
            val pos = ControllerGraphView.positions(a)
            assertEquals(a.states.size + (if (a.transitions.any { it.from == null }) 1 else 0), pos.size)
            val boxes = pos.values.toList()
            for (i in boxes.indices) for (j in i + 1 until boxes.size) {
                val overlap = kotlin.math.abs(boxes[i].x - boxes[j].x) < NODE_W && kotlin.math.abs(boxes[i].y - boxes[j].y) < NODE_H
                assertTrue(!overlap, "${a.name}: ${boxes[i]} overlaps ${boxes[j]}")
            }
        }
        val pos = ControllerGraphView.positions(asset)
        assertEquals(NodePos(0f, 0f), pos["a"])
        assertEquals(ControllerGraphView.COLUMN, pos.getValue("b").x)
        assertTrue(ANY_STATE_NODE in pos)
    }

    @Test
    fun StoredLayoutWins() {
        val pos = ControllerGraphView.positions(asset.copy(layout = mapOf("b" to NodePos(500f, 500f))))
        assertEquals(NodePos(500f, 500f), pos["b"])
    }

    @Test
    fun Arrows_GroupSameSourceAndTarget_AndOffsetReversePairs() {
        val pos = mapOf("a" to NodePos(0f, 0f), "b" to NodePos(300f, 0f), "c" to NodePos(0f, 200f), ANY_STATE_NODE to NodePos(300f, 200f))
        val arrows = ControllerGraphView.arrows(asset, pos)
        assertEquals(3, arrows.size)
        val ab = arrows.single { it.from == "a" && it.to == "b" }
        val ba = arrows.single { it.from == "b" && it.to == "a" }
        assertEquals(listOf(0, 2), ab.indices)
        assertEquals(NODE_W, ab.x0, 1e-3f, "starts on the right edge of a")
        assertNotEquals(ab.y0, ba.y0, "a->b and b->a are offset")
        assertEquals(ab, ControllerGraphView.arrowAt(arrows, 200f, ab.y0))
        assertNull(ControllerGraphView.arrowAt(arrows, 200f, 100f))
    }

    @Test
    fun HitTests_AndBoxSelect() {
        val pos = mapOf("a" to NodePos(0f, 0f), "b" to NodePos(300f, 0f))
        assertEquals("a", ControllerGraphView.nodeAt(pos, 10f, 10f))
        assertNull(ControllerGraphView.nodeAt(pos, 200f, 10f))
        assertEquals(setOf("b"), ControllerGraphView.nodesIn(pos, 280f, -10f, 320f, 10f))
    }

    @Test
    fun View_ZoomKeepsThePointUnderTheMouse_AndFrameAllFits() {
        val v = ControllerGraphView()
        val before = v.toCanvas(100f, 80f, 10f, 20f)
        v.zoomAt(1.5f, 100f, 80f, 10f, 20f)
        val after = v.toCanvas(100f, 80f, 10f, 20f)
        assertEquals(before.first, after.first, 1e-3f)
        assertEquals(before.second, after.second, 1e-3f)
        v.frameAll(mapOf("a" to NodePos(0f, 0f), "b" to NodePos(800f, 0f)), 400f, 300f)
        val (lx, _) = v.toScreen(0f, 0f, 0f, 0f)
        val (rx, _) = v.toScreen(800f + NODE_W, 0f, 0f, 0f)
        assertTrue(lx >= 0f && rx <= 400f, "$lx..$rx")
    }
}
