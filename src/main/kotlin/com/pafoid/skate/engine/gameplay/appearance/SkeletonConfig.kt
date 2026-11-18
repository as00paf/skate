package com.pafoid.skate.engine.gameplay.appearance

import com.pafoid.skate.engine.animation.retarget.HumanoidBone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.assets.serialization.Vector3fSerializer
import kotlinx.serialization.Serializable
import org.joml.Vector3f

/**
 * Attachment points and equip slots of a rig family (GPL-24 §6.1), stored as `*.skeleton.json` in `Assets/Data/`.
 * One config is shared by every character whose rig uses humanoid bone names: bones resolve per rig ([resolve]).
 * The slot list is dynamic: [DEFAULT_SLOT_IDS] is only the default, and a config adds, renames or removes slots.
 */
@Serializable
data class SkeletonConfig(
    val sockets: List<Socket> = HUMANOID_SOCKETS,
    val slots: List<SlotDef> = HUMANOID_SLOTS,
) {
    val slotIds: List<String> get() = slots.map { it.id }

    fun slot(id: String): SlotDef? = slots.firstOrNull { it.id == id }

    fun socket(name: String): Socket? = sockets.firstOrNull { it.name == name }

    /** Adds [slot], or replaces the slot with the same id. */
    fun withSlot(slot: SlotDef): SkeletonConfig =
        copy(slots = if (slots.any { it.id == slot.id }) slots.map { if (it.id == slot.id) slot else it } else slots + slot)

    /** Renames slot [from] to [to]; unchanged when [from] is missing or [to] is taken. */
    fun renameSlot(from: String, to: String): SkeletonConfig {
        if (to.isBlank() || slot(from) == null || slot(to) != null) return this
        return copy(slots = slots.map { if (it.id == from) it.copy(id = to) else it })
    }

    fun withoutSlot(id: String): SkeletonConfig = copy(slots = slots.filterNot { it.id == id })

    /** Finds each socket's bone in [skeleton]; sockets that do not resolve disable their slots (reported once). */
    fun resolve(skeleton: Skeleton): ResolvedSkeleton {
        val bones = sockets.associate { it.name to boneIndex(skeleton, it.bone) }
        val warnings = sockets.filter { bones[it.name] == null }
            .map { "Socket '${it.name}': bone '${it.bone}' not found in the rig, its slots are disabled" }
        return ResolvedSkeleton(this, bones, warnings)
    }

    companion object {
        val DEFAULT_SLOT_IDS = listOf("HEAD", "EYES", "NECK", "TORSO", "LEGS", "FEET", "HANDS")

        /**
         * Humanoid sockets, offsets in metres in bone space, measured on the james rig (Mixamo, Y up, facing +Z,
         * head bone at the base of the skull): `head` on top of the skull for hats, `eyes` in front of the eyes.
         */
        val HUMANOID_SOCKETS = listOf(
            Socket("head", HumanoidBone.HEAD.name, Vector3f(0f, 0.17f, 0.02f)),
            Socket("eyes", HumanoidBone.HEAD.name, Vector3f(0f, 0.09f, 0.10f)),
            Socket("neck", HumanoidBone.NECK.name, Vector3f(0f, 0.03f, 0f)),
            Socket("torso", HumanoidBone.CHEST.name),
            Socket("hips", HumanoidBone.HIPS.name),
            Socket("leftHand", HumanoidBone.LEFT_HAND.name),
            Socket("rightHand", HumanoidBone.RIGHT_HAND.name),
        )

        /** Feet have no rigid socket: shoes are skinned (GPL-24f). */
        val HUMANOID_SLOTS = listOf(
            SlotDef("HEAD", "head"), SlotDef("EYES", "eyes"), SlotDef("NECK", "neck"), SlotDef("TORSO", "torso"),
            SlotDef("LEGS", "hips"), SlotDef("FEET", null), SlotDef("HANDS", "rightHand"),
        )

        val HUMANOID = SkeletonConfig()

        /**
         * A [HumanoidBone] name matches the first bone whose name maps to it ([HumanoidBone.fromName]), so Mixamo,
         * Biped and other rigs share one config; any other name is a raw bone name.
         */
        fun boneIndex(skeleton: Skeleton, bone: String): Int? {
            val humanoid = HumanoidBone.entries.firstOrNull { it.name == bone }
            val found = if (humanoid != null) skeleton.getAllBones().firstOrNull { HumanoidBone.fromName(it.name) == humanoid }
            else skeleton.getBoneByName(bone)
            return found?.index
        }
    }
}

/** A named attachment point on a bone; [offset] in metres and [rotationDeg] (Euler XYZ) in the bone's space. */
@Serializable
data class Socket(
    val name: String,
    val bone: String,
    @Serializable(with = Vector3fSerializer::class) val offset: Vector3f = Vector3f(),
    @Serializable(with = Vector3fSerializer::class) val rotationDeg: Vector3f = Vector3f(),
)

/** An equip slot; [socket] is where rigid items go, null for slots that take only skinned items. */
@Serializable
data class SlotDef(val id: String, val socket: String? = null)

/** A [SkeletonConfig] bound to one rig: socket bone indices and the slots that work on it. */
class ResolvedSkeleton(val config: SkeletonConfig, private val bones: Map<String, Int?>, val warnings: List<String>) {

    fun boneOf(socket: String): Int? = bones[socket]

    /** Slots usable on this rig: socket-less slots, and slots whose socket resolved. */
    val enabledSlots: Set<String> = config.slots.filter { s -> s.socket == null || bones[s.socket] != null }.map { it.id }.toSet()

    /** The socket rigid items in [slotId] attach to, when the slot is enabled and has one. */
    fun socketOf(slotId: String): Socket? {
        val name = config.slot(slotId)?.socket ?: return null
        return config.socket(name)?.takeIf { bones[name] != null }
    }
}
