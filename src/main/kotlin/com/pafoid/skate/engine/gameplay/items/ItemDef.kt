package com.pafoid.skate.engine.gameplay.items

import com.pafoid.skate.engine.assets.serialization.Vector3fSerializer
import kotlinx.serialization.Serializable
import org.joml.Vector3f

/**
 * One kind of item (GPL-24 §3.1), stored as `*.item.json` in the project's `Assets/Items/` folder. Categories are
 * free-form [tags] rather than an engine enum, so a game can add its own.
 */
@Serializable
data class ItemDef(
    /** Stable key, e.g. `cap.red`. */
    val id: String,
    /** String-table key of the display name, not literal text. */
    val nameKey: String = "",
    /** Project-relative icon texture. */
    val iconPath: String? = null,
    /** 1 = not stackable. */
    val maxStack: Int = 1,
    /** Null = never wears out. */
    val maxDurability: Int? = null,
    val tags: Set<String> = emptySet(),
    /** Null = cannot be equipped. */
    val equip: EquipDef? = null,
    /** Free-form stats, e.g. `resist.fire` = 0.3. */
    val stats: Map<String, Float> = emptyMap(),
) {
    val stackLimit: Int get() = maxStack.coerceAtLeast(1)
}

/**
 * How an item is worn (GPL-24 §6.3). [slot] is a slot id from the character's skeleton config. A rigid item sits on
 * the slot's socket, adjusted by [offset] (metres), [rotationDeg] (Euler XYZ) and [scale] so one socket fits a cap
 * and a helmet alike. Skinned clothes, hidden body parts and material overrides come with GPL-24f.
 */
@Serializable
data class EquipDef(
    val slot: String,
    /** Project-relative model path; empty = nothing to draw. */
    val model: String = "",
    val skinned: Boolean = false,
    val hidesParts: List<String> = emptyList(),
    val offset: @Serializable(with = Vector3fSerializer::class) Vector3f = Vector3f(),
    val rotationDeg: @Serializable(with = Vector3fSerializer::class) Vector3f = Vector3f(),
    val scale: @Serializable(with = Vector3fSerializer::class) Vector3f = Vector3f(1f, 1f, 1f),
)

/** A pile of one item in a slot. [durability] is null for items that never wear out. */
@Serializable
data class ItemStack(val itemId: String, var count: Int, var durability: Int? = null)
