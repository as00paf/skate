package com.pafoid.skate.editor.commands

import com.pafoid.skate.editor.commands.objects.ApplyAnimationCommand
import com.pafoid.skate.editor.commands.objects.ApplyTextureCommand
import com.pafoid.skate.editor.commands.objects.ResetComponentCommand
import com.pafoid.skate.editor.commands.scene.CreateLightCommand
import com.pafoid.skate.editor.commands.scene.DuplicateGameObjectCommand
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.assets.data.models.animations.SkeletonPose
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.PointLightComponent
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.GameObjectManager
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.data.LightType
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** EDT-10: commands that used to be execute-only (or had an empty undo) now undo and redo. */
class UndoCoverageCommandsTest {
    private val scene = Scene("Park")
    private val objects = GameObjectManager().also { it.init(scene) }
    private val undo = UndoRedoManager(mockk(relaxed = true), mockk(relaxed = true))

    @Test
    fun CreateLight_UndoRemovesTheLight_RedoAddsTheSameObject() {
        val command = CreateLightCommand("Sun", LightType.POINT, scene, objects)
        undo.executeCommand(command)
        val light = command.gameObject!!
        assertTrue(light in scene.children)

        undo.undo()
        assertFalse(light in scene.children)
        undo.redo()
        assertSame(light, scene.children.single())
    }

    @Test
    fun Duplicate_UndoRemovesTheCopy_AndTheOriginalKeepsItsRenderId() {
        val original = GameObject("Box").also { it.addComponent(Transform()); it.addComponent(RenderComponent()) }
        objects.addGameObject(original)
        val originalId = original.getComponent<RenderComponent>()!!.uId

        val command = DuplicateGameObjectCommand(original, scene, objects)
        undo.executeCommand(command)
        assertEquals(2, scene.children.size)
        assertEquals(originalId, original.getComponent<RenderComponent>()!!.uId)

        undo.undo()
        assertEquals(listOf(original), scene.children.toList())
    }

    @Test
    fun ApplyTexture_UndoPutsThePreviousRenderComponentBack() {
        val target = GameObject("Box").also { it.addComponent(RenderComponent()) }
        val before = target.getComponent<RenderComponent>()
        val command = ApplyTextureCommand(target, "assets/textures/test.png", mockk<AssetsManager>(relaxed = true), mockk(relaxed = true))

        undo.executeCommand(command)
        val applied = target.getComponent<RenderComponent>()
        assertTrue(applied !== before)
        undo.undo()
        assertSame(before, target.getComponent<RenderComponent>())
        undo.redo()
        assertSame(applied, target.getComponent<RenderComponent>())
    }

    private fun skinned() = GameObject("Skater").also {
        it.addComponent(SkeletonComponent(SkeletonPose(Skeleton(Bone(0, "root"), 1))))
    }

    @Test
    fun ApplyAnimation_OnAnObjectWithoutAnimator_UndoRemovesTheAnimatorItAdded() {
        val skater = skinned()
        undo.executeCommand(ApplyAnimationCommand(skater, Animation("ollie", duration = 1f, path = "ollie.anim"), mockk(relaxed = true)))
        assertEquals(listOf("ollie"), skater.getComponent<Animator>()!!.animations.map { it.name })

        undo.undo()
        assertNull(skater.getComponent<Animator>())
        undo.redo()
        assertNotNull(skater.getComponent<Animator>())
    }

    @Test
    fun ApplyAnimation_Refused_IsNotInTheHistory() {
        val noSkeleton = GameObject("Box")
        undo.executeCommand(ApplyAnimationCommand(noSkeleton, Animation("ollie", duration = 1f, path = "ollie.anim"), mockk(relaxed = true)))
        assertEquals(0, undo.getUndoCount())
        assertNull(noSkeleton.getComponent<Animator>())
    }

    @Test
    fun ResetToDefault_IsUndoable() {
        val light = PointLightComponent(intensity = 3f, linear = 0.7f)
        undo.executeCommand(ResetComponentCommand(light))
        assertEquals(0.1f, light.linear)

        undo.undo()
        assertEquals(0.7f, light.linear)
        assertEquals(3f, light.intensity)
        undo.redo()
        assertEquals(0.1f, light.linear)
    }
}
