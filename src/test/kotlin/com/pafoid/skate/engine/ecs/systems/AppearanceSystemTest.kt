package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.assets.data.models.animations.SkeletonPose
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.BoneAttachment
import com.pafoid.skate.engine.ecs.components.EquipmentComponent
import com.pafoid.skate.engine.ecs.components.InventoryComponent
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.events.InventoryRequest
import com.pafoid.skate.engine.gameplay.appearance.SkeletonConfigs
import com.pafoid.skate.engine.gameplay.items.EquipDef
import com.pafoid.skate.engine.gameplay.items.ItemDatabase
import com.pafoid.skate.engine.gameplay.items.ItemDef
import com.pafoid.skate.engine.gameplay.items.ItemStack
import com.pafoid.skate.engine.getComponent
import org.joml.Matrix4f
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** GPL-24e §6.6: equipping shows rigid items as `Equip:<slot>` children on sockets, at the ECS level with no GL. */
class AppearanceSystemTest {
    private val events = EventSystem()
    private val items = ItemDatabase().apply {
        register(ItemDef("hat", equip = EquipDef("HEAD", model = "hat.obj", scale = Vector3f(0.1f, 0.1f, 0.1f))))
        register(ItemDef("glasses", equip = EquipDef("EYES", model = "glasses.obj")))
        register(ItemDef("chain", equip = EquipDef("NECK", model = "chain.obj")))
        register(ItemDef("hoodie", equip = EquipDef("TORSO", model = "hoodie.glb", skinned = true)))
        register(ItemDef("broken", equip = EquipDef("HEAD", model = "missing.obj")))
    }
    private val loaded = mutableListOf<String>()
    private val appearance = AppearanceSystem(events, items) { path -> if (path == "missing.obj") null else `3dModel`(path).also { loaded += path } }
    private val inventory = InventorySystem(events, items, slotsOf = SkeletonConfigs::slotsOf)

    /** hips -> neck -> head, head 1.6 m up. */
    private fun rig(): SkeletonComponent {
        val hips = Bone(0, "Hips", Matrix4f().translation(0f, 1f, 0f))
        val neck = Bone(1, "Neck", Matrix4f().translation(0f, 0.5f, 0f)).also(hips::addChild)
        Bone(2, "Head", Matrix4f().translation(0f, 0.1f, 0f)).also(neck::addChild)
        return SkeletonComponent(SkeletonPose(Skeleton(hips, 3)))
    }

    private fun character(vararg carried: String) = GameObject("Hero")
        .addComponent(Transform())
        .addComponent(rig())
        .addComponent(InventoryComponent(4))
        .addComponent(EquipmentComponent())
        .also { go -> carried.forEach { events.publish(InventoryRequest.Add(go, it)) } }

    private fun GameObject.equip(slot: Int) = events.publish(InventoryRequest.Equip(this, slot))
    private fun GameObject.child(slot: String) = children.firstOrNull { it.name == AppearanceSystem.PREFIX + slot }

    @Test
    fun Appearance_EquippingARigidItemAddsAnAttachedChildOnTheSlotSocket() {
        val hero = character("hat")
        hero.equip(0)
        val hat = hero.child("HEAD")!!
        assertEquals("head", hat.getComponent<BoneAttachment>()!!.socket)
        assertEquals(Vector3f(0.1f, 0.1f, 0.1f), hat.getComponent<BoneAttachment>()!!.scale)
        assertEquals("hat.obj", hat.getComponent<RenderComponent>()!!.model!!.path)
        assertSame(hero, hat.parent)
    }

    @Test
    fun Appearance_UnequippingRemovesTheChild() {
        val hero = character("hat")
        hero.equip(0)
        events.publish(InventoryRequest.Unequip(hero, "HEAD"))
        assertNull(hero.child("HEAD"))
    }

    @Test
    fun Appearance_HeadEyesAndNeckItemsGiveThreeChildren() {
        val hero = character("hat", "glasses", "chain")
        (0..2).forEach { hero.equip(it) }
        assertEquals(setOf("Equip:HEAD", "Equip:EYES", "Equip:NECK"), hero.children.map { it.name }.toSet())
        assertEquals("eyes", hero.child("EYES")!!.getComponent<BoneAttachment>()!!.socket)
    }

    @Test
    fun Appearance_SkinnedItemsAndUnloadableModelsShowNothing() {
        val hero = character("hoodie", "broken")
        hero.equip(0)
        hero.equip(1)
        assertNull(hero.child("TORSO"), "skinned clothes wait for GPL-24f")
        assertNull(hero.child("HEAD"))
        assertEquals("broken", hero.getComponent<EquipmentComponent>()!!.equipped["HEAD"]!!.itemId, "still worn, just not drawn")
    }

    @Test
    fun Appearance_RebuildAfterSceneInitReplacesStaleChildren() {
        val scene = Scene("s")
        val hero = GameObject("Hero").addComponent(Transform()).addComponent(rig())
            .addComponent(EquipmentComponent(linkedMapOf("HEAD" to ItemStack("hat", 1), "EYES" to ItemStack("glasses", 1))))
        hero.addChild(GameObject("Equip:HEAD")) // a stale child kept by a save
        scene.addChild(hero)
        appearance.init(scene)
        assertEquals(1, hero.children.size, "nothing happens before the first update")
        appearance.update(0f)
        assertEquals(listOf("Equip:HEAD", "Equip:EYES"), hero.children.map { it.name })
        assertTrue(hero.children.all { it.getComponent<BoneAttachment>() != null })
        appearance.update(0f)
        assertEquals(2, hero.children.size, "rebuilt once per init")
    }

    @Test
    fun BoneAttachment_ChildFollowsTheSocketOnTheAnimatedBone() {
        val hero = character("hat")
        hero.equip(0)
        val skeleton = hero.getComponent<SkeletonComponent>()!!
        val attachments = BoneAttachmentSystem().also { it.init(Scene("s").apply { addChild(hero) }) }
        val transforms = TransformSystem()

        attachments.update(0f)
        val hat = hero.child("HEAD")!!.getComponent<Transform>()!!
        // Head bone at 1.6 m; head socket 0.17 m up, 0.02 m forward
        assertTrue(Vector3f(0f, 1.77f, 0.02f).distance(hat.translation) < 1e-4f, "got ${hat.translation}")

        // Animate: the head bone moves; the hat follows on the next frame
        skeleton.pose.globalTransforms[2].translation(0.5f, 1.2f, 0f)
        attachments.update(0f)
        transforms.updateTransform(hero.getComponent<Transform>()!!)
        transforms.updateTransform(hat)
        assertTrue(Vector3f(0.5f, 1.37f, 0.02f).distance(hat.worldMatrix.getTranslation(Vector3f())) < 1e-4f)
    }

    @Test
    fun Appearance_EquipChildrenAreRuntimeOnlyAndNeverSaved() {
        val hero = character("hat")
        hero.equip(0)
        assertTrue(hero.child("HEAD")!!.isRuntimeOnly)
        val serializer = com.pafoid.skate.engine.assets.serialization.Serializer()
        val saved = serializer.decode<GameObject>(serializer.encode(hero))
        assertTrue(saved.children.none { it.name.startsWith(AppearanceSystem.PREFIX) })
        assertEquals("hat", saved.getComponent<EquipmentComponent>()!!.equipped["HEAD"]!!.itemId, "what is worn is saved")
    }
}
