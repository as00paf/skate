package com.pafoid.skate.editor.systems

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.SceneManager
import io.mockk.mockk
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Transform
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** EDT-60 §7.1 follow-up for EDT-45: `uId` and `isDirty` are no longer saved, so the Play snapshot carries them. */
class ScenePlaySnapshotIdsTest {
    private val serializer = Serializer()

    private fun scene() = Scene("Main").apply {
        addChild(GameObject("Box").addComponent(Transform()).also { it.addChild(GameObject("Lid")) })
    }

    private fun all(go: GameObject): List<GameObject> = listOf(go) + go.children.flatMap(::all)

    private fun manager() =
        SceneManager(mockk(relaxed = true), EventSystem(), serializer, mockk(relaxed = true), mockk(relaxed = true))

    @Test
    fun Restore_KeepsThePrePlayDirtyFlag_AndSessionIds() {
        for (dirty in listOf(true, false)) {
            val manager = manager()
            val playing = scene().apply { isDirty = dirty }
            manager.openScene(playing)
            val snap = ScenePlaySnapshot.capture(playing, serializer)
            val idsBefore = all(playing).map { it.uId }
            playing.isDirty = true // an edit during Play, thrown away by the restore

            val fresh = snap.restore(manager, serializer, playing)

            assertEquals(dirty, fresh.isDirty, "pre-Play dirty = $dirty")
            assertEquals(idsBefore, all(fresh).map { it.uId }, "objects keep their session ids across Stop")
        }
    }

    @Test
    fun KeepSessionIds_MatchesObjectsByGuid() {
        val old = scene()
        val fresh = ScenePlaySnapshot.capture(old, serializer).decode(serializer)
        assertNotEquals(all(old).map { it.uId }, all(fresh).map { it.uId }, "a decode gets new session ids")

        fresh.addChild(GameObject("SpawnedInPlay"))
        val spawnedId = fresh.children.last().uId
        ScenePlaySnapshot.keepSessionIds(old, fresh)

        assertEquals(all(old).map { it.uId }, all(fresh).filter { it.name != "SpawnedInPlay" }.map { it.uId })
        assertEquals(spawnedId, fresh.children.last().uId, "objects without a match keep their own id")
        assertTrue(all(fresh).map { it.guid }.containsAll(all(old).map { it.guid }))
    }
}
