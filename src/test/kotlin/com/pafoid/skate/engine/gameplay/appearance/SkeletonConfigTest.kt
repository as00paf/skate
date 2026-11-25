package com.pafoid.skate.engine.gameplay.appearance

import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.assets.loaders.AssimpLoader
import com.pafoid.skate.engine.gameplay.CharacterConfigs
import io.mockk.mockk
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** GPL-24e §6.6: one humanoid config fits several rigs; slots are a dynamic list with defaults. */
class SkeletonConfigTest {

    /** hips -> spine -> neck -> head with Mixamo names, plus a leftover end bone. */
    private fun mixamoRig(withHead: Boolean = true): Skeleton {
        val hips = Bone(0, "mixamorig:Hips")
        val spine = Bone(1, "mixamorig:Spine").also(hips::addChild)
        val neck = Bone(2, "mixamorig:Neck").also(spine::addChild)
        if (withHead) Bone(3, "mixamorig:Head").also(neck::addChild)
        return Skeleton(hips, if (withHead) 4 else 3)
    }

    @Test
    fun SkeletonConfig_DefaultSlotsAreTheSevenHumanoidSlots() {
        assertEquals(listOf("HEAD", "EYES", "NECK", "TORSO", "LEGS", "FEET", "HANDS"), SkeletonConfig.HUMANOID.slotIds)
        assertEquals(SkeletonConfig.DEFAULT_SLOT_IDS, SkeletonConfig.HUMANOID.slotIds)
    }

    @Test
    fun SkeletonConfig_HumanoidResolvesOnTheJamesRig() {
        val skeleton = AssimpLoader(mockk(relaxed = true), mockk(relaxed = true)).loadModel("assets/Models/james.glb").skeleton!!
        val resolved = SkeletonConfig.HUMANOID.resolve(skeleton)
        assertEquals("Head", skeleton.bones[resolved.boneOf("head")!!]!!.name)
        assertEquals("Head", skeleton.bones[resolved.boneOf("eyes")!!]!!.name)
        assertTrue(resolved.warnings.isEmpty(), "every humanoid socket resolves on james: ${resolved.warnings}")
        assertEquals(SkeletonConfig.DEFAULT_SLOT_IDS.toSet(), resolved.enabledSlots)
    }

    @Test
    fun SkeletonConfig_HumanoidResolvesOnAMixamoNamedRig() {
        val resolved = SkeletonConfig.HUMANOID.resolve(mixamoRig())
        assertEquals(3, resolved.boneOf("head"))
        assertEquals(2, resolved.boneOf("neck"))
        assertEquals("head", resolved.socketOf("HEAD")!!.name)
    }

    @Test
    fun SkeletonConfig_UnresolvedSocketDisablesItsSlot() {
        val resolved = SkeletonConfig.HUMANOID.resolve(mixamoRig(withHead = false))
        assertNull(resolved.boneOf("head"))
        assertFalse("HEAD" in resolved.enabledSlots)
        assertFalse("EYES" in resolved.enabledSlots)
        assertTrue("NECK" in resolved.enabledSlots)
        assertTrue("FEET" in resolved.enabledSlots, "a slot without a socket takes skinned items on any rig")
        assertNull(resolved.socketOf("HEAD"))
        assertTrue(resolved.warnings.any { it.contains("'head'") })
    }

    @Test
    fun SkeletonConfig_RawBoneNamesAreUsedAsIs() {
        val config = SkeletonConfig(sockets = listOf(Socket("tail", "mixamorig:Spine")), slots = listOf(SlotDef("TAIL", "tail")))
        val resolved = config.resolve(mixamoRig())
        assertEquals(1, resolved.boneOf("tail"))
        assertEquals(setOf("TAIL"), resolved.enabledSlots)
    }

    @Test
    fun SkeletonConfig_SlotsCanBeAddedRenamedAndRemoved() {
        val config = SkeletonConfig.HUMANOID.withSlot(SlotDef("BACK", "torso")).renameSlot("HANDS", "RIGHT_HAND").withoutSlot("LEGS")
        assertEquals(listOf("HEAD", "EYES", "NECK", "TORSO", "FEET", "RIGHT_HAND", "BACK"), config.slotIds)
        assertSame(config, config.renameSlot("HEAD", "EYES"), "renaming onto a taken id is refused")
        assertSame(config, config.renameSlot("MISSING", "X"))
        assertEquals("neck", config.withSlot(SlotDef("BACK", "neck")).slot("BACK")!!.socket, "same id replaces")
    }

    @Test
    fun SkeletonConfig_FilesDecodeLenientlyWithDefaults() {
        val partial = CharacterConfigs.decode("a.skeleton.json", """{ "slots": [ { "id": "HEAD", "socket": "head" }, { "id": "TAIL" } ] }""") { SkeletonConfig() }
        assertEquals(listOf("HEAD", "TAIL"), partial.slotIds)
        assertEquals(SkeletonConfig.HUMANOID_SOCKETS, partial.sockets, "omitted sockets fall back to the humanoid ones")
        val sockets = CharacterConfigs.decode("b.skeleton.json", """{ "sockets": [ { "name": "head", "bone": "HEAD", "offset": { "y": 0.2 } } ] }""") { SkeletonConfig() }
        assertEquals(Vector3f(0f, 0.2f, 0f), sockets.socket("head")!!.offset)
        assertEquals(SkeletonConfig.HUMANOID_SLOTS, sockets.slots)
    }
}
