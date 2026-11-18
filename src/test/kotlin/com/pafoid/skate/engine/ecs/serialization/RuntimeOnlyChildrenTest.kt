package com.pafoid.skate.engine.ecs.serialization

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.prefab.PrefabService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/** ECS-19: runtime-only children are left out of every serialized copy, at any depth. */
class RuntimeOnlyChildrenTest {
    private val serializer = Serializer()

    private fun tree() = GameObject("Root").addComponent(Transform()).apply {
        addChild(GameObject("Kept").addComponent(Transform()).apply {
            addChild(GameObject("DeepRuntime").apply { isRuntimeOnly = true })
        })
        addChild(GameObject("Runtime").apply { isRuntimeOnly = true; addChild(GameObject("UnderRuntime")) })
    }

    @Test
    fun Serialization_SkipsRuntimeOnlyChildren_AtAnyDepth() {
        val decoded = serializer.decode<GameObject>(serializer.encode(tree()))

        assertEquals(listOf("Kept"), decoded.children.map { it.name })
        assertEquals(emptyList<String>(), decoded.children.single().children.map { it.name })
        assertFalse(decoded.children.single().isRuntimeOnly)
    }

    @Test
    fun PersistentChildren_ListsWhatTheHierarchyShows() {
        val root = tree()
        assertEquals(listOf("Kept"), root.persistentChildren.map { it.name })
        assertEquals(2, root.children.size)
    }

    @Test
    fun PrefabCapture_DoesNotIncludeRuntimeOnlyChildren() {
        val asset = PrefabService(serializer).createAsset("p", tree())

        assertFalse(asset.template.toString().contains("Runtime"))
    }
}
