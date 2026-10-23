package com.pafoid.skate.engine.ecs

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.attachComponent
import com.pafoid.skate.engine.ecs.components.NonPickable
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.removeComponent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ComponentQueryTest {

    private lateinit var scene: Scene
    private lateinit var query: ComponentQuery

    @BeforeEach
    fun setup() {
        scene = Scene("test")
        query = ComponentQuery({ scene }, listOf(Transform::class))
    }

    private fun obj(name: String, withTransform: Boolean = true) =
        GameObject(name).also { if (withTransform) it.addComponent(Transform()) }

    @Test
    fun ComponentQuery_ReturnsMatchingEntitiesInHierarchyOrder() {
        val parent = obj("parent")
        val child = obj("child")
        val other = obj("other", withTransform = false)
        scene.addChild(parent)
        scene.addChild(other)
        parent.addChild(child)

        assertEquals(listOf(parent, child), query.entities)
    }

    @Test
    fun ComponentQuery_IsCachedUntilStructureChanges() {
        scene.addChild(obj("a"))
        query.entities
        query.entities
        assertEquals(1, query.rebuildCount)

        scene.addChild(obj("b"))
        assertEquals(2, query.entities.size)
        assertEquals(2, query.rebuildCount)
    }

    @Test
    fun ComponentQuery_InvalidatesWhenComponentAddedOrRemoved() {
        val go = obj("a", withTransform = false)
        scene.addChild(go)
        assertEquals(0, query.entities.size)

        go.addComponent(Transform())
        assertEquals(listOf(go), query.entities)

        go.removeComponent<Transform>()
        assertEquals(0, query.entities.size)
    }

    @Test
    fun ComponentQuery_InvalidatesWhenComponentAttachedOrChildRemoved() {
        val go = obj("a", withTransform = false)
        scene.addChild(go)
        query.entities

        go.attachComponent(Transform())
        assertEquals(1, query.entities.size)

        scene.removeChild(go)
        assertEquals(0, query.entities.size)
    }

    @Test
    fun ComponentQuery_SkipsDeadAndDisabledWithoutRebuild() {
        val a = obj("a")
        val b = obj("b")
        scene.addChild(a)
        scene.addChild(b)
        query.entities
        val rebuilds = query.rebuildCount

        a.isEnabled = false
        b.isDead = true

        assertEquals(0, query.entities.size)
        assertEquals(rebuilds, query.rebuildCount)

        val all = ComponentQuery({ scene }, listOf(Transform::class), includeDisabled = true)
        assertEquals(listOf(a), all.entities)
    }

    @Test
    fun ComponentQuery_MutationDuringIterationAppliesOnNextAccess() {
        scene.addChild(obj("a"))
        var visited = 0
        query.forEach {
            visited++
            scene.addChild(obj("spawned"))
        }
        assertEquals(1, visited)
        assertEquals(2, query.entities.size)
    }

    @Test
    fun ComponentQuery_RebuildsWhenRootSceneChanges() {
        scene.addChild(obj("a"))
        assertEquals(1, query.entities.size)

        scene = Scene("other")
        assertEquals(0, query.entities.size)
    }

    @Test
    fun ComponentQuery_RequiresAllComponentsAndTopLevelOnlyScope() {
        val both = obj("both").also { it.addComponent(NonPickable()) }
        val nested = obj("nested").also { it.addComponent(NonPickable()) }
        scene.addChild(both)
        both.addChild(nested)
        scene.addChild(obj("onlyTransform"))

        val q = ComponentQuery({ scene }, listOf(Transform::class, NonPickable::class))
        assertEquals(listOf(both, nested), q.entities)

        val top = ComponentQuery({ scene }, listOf(Transform::class, NonPickable::class), topLevelOnly = true)
        assertSame(both, top.entities.single())
    }
}
