package com.pafoid.skate.engine.ecs

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.components.Transform
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TagLayerTest {
    private val scene = Scene("s")
    private val a = GameObject("a").also { it.tag = "enemy"; it.layer = 1 }
    private val b = GameObject("b").also { it.tag = "enemy"; it.layer = 2 }
    private val c = GameObject("c").also { it.layer = 2 }

    init {
        scene.addChild(a); a.addChild(b); scene.addChild(c)
    }

    @Test
    fun TagLayer_FindByTagIncludesNestedInPreOrder() {
        assertEquals(listOf(a, b), scene.findByTag("enemy"))
        assertEquals(a, scene.findFirstByTag("enemy"))
        assertEquals(emptyList(), scene.findByTag("none"))
    }

    @Test
    fun TagLayer_FindByLayerUsesMask() {
        assertEquals(listOf(b, c), scene.findByLayer(GameObject.layerBit(2)))
        assertEquals(listOf(a, b, c), scene.findByLayer(GameObject.layerBit(1) or GameObject.layerBit(2)))
    }

    @Test
    fun TagLayer_InvalidLayerRejected() {
        assertFailsWith<IllegalArgumentException> { GameObject("x").layer = 32 }
        assertFailsWith<IllegalArgumentException> { GameObject("x").layer = -1 }
    }

    @Test
    fun TagLayer_QueryLayerMaskFiltersWithoutRebuild() {
        listOf(a, b, c).forEach { it.addComponent(Transform()) }
        val q = ComponentQuery({ scene }, listOf(Transform::class), layerMask = GameObject.layerBit(2))
        assertEquals(listOf(b, c), q.entities)
        c.layer = 1
        assertEquals(listOf(b), q.entities)
        assertEquals(1, q.rebuildCount)
    }

    @Test
    fun TagLayer_SurvivesSerialization() {
        val ser = Serializer()
        val copy = ser.decode<GameObject>(ser.encode(b))
        assertEquals("enemy", copy.tag)
        assertEquals(2, copy.layer)
    }
}
