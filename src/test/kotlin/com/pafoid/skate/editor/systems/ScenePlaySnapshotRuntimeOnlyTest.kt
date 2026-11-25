package com.pafoid.skate.editor.systems

import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** EDT-94: runtime-only children before a selected object do not shift its selection path across Stop. */
class ScenePlaySnapshotRuntimeOnlyTest {
    @Test
    fun Selection_SurvivesRestore_WhenRuntimeOnlySiblingsComeFirst() {
        val serializer = Serializer()
        val scene = Scene("s")
        val terrain = GameObject("Terrain").also { scene.addChild(it) }
        repeat(3) { terrain.addChild(GameObject("Rock$it").apply { isRuntimeOnly = true }) }
        val sign = GameObject("Sign").also { terrain.addChild(it) }
        scene.setSelection(listOf(sign))

        val snapshot = ScenePlaySnapshot.capture(scene, serializer)
        val fresh = snapshot.decode(serializer)

        assertEquals(listOf(sign.guid), snapshot.selectionIn(fresh).map { it.guid })
    }
}
