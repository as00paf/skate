package com.pafoid.skate.game.items

import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.loaders.AssimpLoader
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.BoneAttachment
import com.pafoid.skate.engine.ecs.components.EquipmentComponent
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.AppearanceSystem
import com.pafoid.skate.engine.ecs.systems.BoneAttachmentSystem
import com.pafoid.skate.engine.ecs.systems.InventorySystem
import com.pafoid.skate.engine.ecs.systems.TransformSystem
import com.pafoid.skate.engine.events.InventoryRequest
import com.pafoid.skate.engine.gameplay.appearance.SkeletonConfigs
import com.pafoid.skate.engine.gameplay.items.ItemDatabase
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.game.prefabs.Skater
import io.mockk.mockk
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** GPL-27 acceptance (GPL-24e): the skater wears the bundled hat and glasses on its head, and can take them off. */
class SkaterAccessoriesTest {
    private val james = AssimpLoader(mockk(relaxed = true), mockk(relaxed = true)).loadModel("assets/Models/james.glb")

    private inner class World {
        val events = EventSystem()
        val items = ItemDatabase()
        val inventory = InventorySystem(events, items, slotsOf = SkeletonConfigs::slotsOf, reload = { SkateItems.load(it, null) })
        val appearance = AppearanceSystem(events, items) { `3dModel`(it) }
        val attachments = BoneAttachmentSystem()
        val transforms = TransformSystem()
        val scene = Scene("Skatepark")
        val skater = Skater("Skater", james).also(scene::addChild)

        init {
            listOf(inventory, appearance, attachments, transforms).forEach { it.init(scene) }
        }

        fun frame() {
            appearance.update(0f)
            attachments.update(0f)
            transforms.update(0f)
        }

        fun worn(slot: String) = skater.children.firstOrNull { it.name == AppearanceSystem.PREFIX + slot }
        fun headY(): Float {
            val pose = skater.getComponent<SkeletonComponent>()!!.pose
            val head = pose.skeleton.getBoneByName("Head")!!.index
            return pose.globalTransforms[head].getTranslation(Vector3f()).y
        }
    }

    @Test
    fun SkateItems_BundledHatAndGlassesLoad() {
        val db = ItemDatabase()
        SkateItems.load(db, null)
        assertEquals("HEAD", db[SkateItems.HAT]!!.equip!!.slot)
        assertEquals("EYES", db[SkateItems.GLASSES]!!.equip!!.slot)
        assertTrue(db.warnings.isEmpty(), db.warnings.toString())
    }

    @Test
    fun SkateItems_ProjectItemsOverrideBundledOnes(@TempDir project: File) {
        File(project, "Assets/Items").mkdirs()
        File(project, "Assets/Items/hat.item.json").writeText("""{ "id": "skate.hat", "equip": { "slot": "HEAD", "model": "Assets/Models/beanie.obj" } }""")
        val db = ItemDatabase()
        SkateItems.load(db, project)
        assertEquals("Assets/Models/beanie.obj", db[SkateItems.HAT]!!.equip!!.model)
    }

    @Test
    fun Skater_WearsTheHatOnTopOfTheHeadAndTheGlassesInFrontOfTheEyes() {
        val w = World()
        w.frame()
        val hat = w.worn("HEAD")!!
        val glasses = w.worn("EYES")!!
        assertEquals("head", hat.getComponent<BoneAttachment>()!!.socket)
        assertEquals("eyes", glasses.getComponent<BoneAttachment>()!!.socket)

        // In the skater's own space (the prefab stands 0.15 m up), where the bone matrices are
        val toSkater = w.skater.getComponent<Transform>()!!.worldMatrix.invert(org.joml.Matrix4f())
        val hatAt = toSkater.transformPosition(hat.getComponent<Transform>()!!.worldMatrix.getTranslation(Vector3f()))
        val glassesAt = toSkater.transformPosition(glasses.getComponent<Transform>()!!.worldMatrix.getTranslation(Vector3f()))
        val head = w.headY()
        assertTrue(hatAt.y > head + 0.15f && hatAt.y < head + 0.3f, "hat at $hatAt, head bone at y=$head")
        assertTrue(glassesAt.y > head && glassesAt.y < hatAt.y, "glasses at $glassesAt")
        assertTrue(glassesAt.z > 0.05f, "glasses sit in front of the face (+Z): $glassesAt")
    }

    @Test
    fun Skater_TakesTheGlassesOffAndPutsThemBackOn() {
        val w = World()
        w.frame()
        w.events.publish(InventoryRequest.Unequip(w.skater, "EYES"))
        assertNull(w.worn("EYES"))
        assertEquals(SkateItems.GLASSES, w.skater.getComponent<com.pafoid.skate.engine.ecs.components.InventoryComponent>()!!.inventory.slots[0]!!.itemId)

        w.events.publish(InventoryRequest.Equip(w.skater, 0))
        assertEquals(SkateItems.GLASSES, w.skater.getComponent<EquipmentComponent>()!!.equipped["EYES"]!!.itemId)
        assertEquals("eyes", w.worn("EYES")!!.getComponent<BoneAttachment>()!!.socket)
        assertTrue(w.worn("HEAD") != null, "the hat stays on")
    }

    @Test
    fun Skater_SlotsComeFromTheHumanoidSkeletonConfig() {
        val w = World()
        assertEquals(setOf("HEAD", "EYES", "NECK", "TORSO", "LEGS", "FEET", "HANDS"), SkeletonConfigs.slotsOf(w.skater))
    }
}
