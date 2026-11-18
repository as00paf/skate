package com.pafoid.skate.engine.gameplay.items

/**
 * Fixed-capacity slot list (GPL-24 §3.2). Works on the [slots] list it is given, so an `InventoryComponent` can keep
 * the serialized list and wrap it. Pure: no ECS, events or GL.
 */
class Inventory(val slots: MutableList<ItemStack?>) {

    constructor(capacity: Int) : this(MutableList(capacity.coerceAtLeast(0)) { null })

    val capacity: Int get() = slots.size

    /** Total count of [itemId] across all slots. */
    fun count(itemId: String): Int = slots.sumOf { if (it?.itemId == itemId) it.count else 0 }

    fun contains(itemId: String): Boolean = slots.any { it?.itemId == itemId }

    /** Room left for [def]: free space in its partial stacks plus empty slots. */
    fun room(def: ItemDef): Int = slots.sumOf { s ->
        when {
            s == null -> def.stackLimit
            s.itemId == def.id -> (def.stackLimit - s.count).coerceAtLeast(0)
            else -> 0
        }
    }

    /**
     * Adds [count] of [def], topping up partial stacks first, then the first empty slots. Returns the overflow that
     * did not fit (0 when everything was added). New stacks start at full durability.
     */
    fun add(def: ItemDef, count: Int): Int {
        var left = count.coerceAtLeast(0)
        for (s in slots) {
            if (left == 0) break
            if (s == null || s.itemId != def.id) continue
            val moved = minOf(left, def.stackLimit - s.count).coerceAtLeast(0)
            s.count += moved
            left -= moved
        }
        for (i in slots.indices) {
            if (left == 0) break
            if (slots[i] != null) continue
            val moved = minOf(left, def.stackLimit)
            slots[i] = ItemStack(def.id, moved, def.maxDurability)
            left -= moved
        }
        return left
    }

    /** Puts [stack] back as it is (durability kept) into the first empty slot; false when there is none. */
    fun put(stack: ItemStack): Boolean {
        val free = slots.indexOfFirst { it == null }
        if (free < 0) return false
        slots[free] = stack
        return true
    }

    /** Takes up to [count] from [slot]; returns what was taken, or null for an empty or invalid slot. */
    fun remove(slot: Int, count: Int): ItemStack? {
        val s = slots.getOrNull(slot) ?: return null
        if (count <= 0) return null
        if (count >= s.count) {
            slots[slot] = null
            return s
        }
        s.count -= count
        return s.copy(count = count)
    }

    /**
     * Moves [from] onto [to]: merges into a stack of the same item up to [ItemDef.maxStack] (the rest stays in
     * [from]), otherwise swaps the two slots. [db] gives stack limits; unknown items only swap.
     */
    fun move(from: Int, to: Int, db: ItemDatabase) {
        if (from == to || from !in slots.indices || to !in slots.indices) return
        val a = slots[from] ?: return
        val b = slots[to]
        val def = db[a.itemId]
        if (b != null && def != null && b.itemId == a.itemId && a.durability == null && b.durability == null) {
            val moved = minOf(a.count, def.stackLimit - b.count).coerceAtLeast(0)
            b.count += moved
            a.count -= moved
            if (a.count == 0) slots[from] = null
            return
        }
        slots[from] = b
        slots[to] = a
    }

    /**
     * Wears the item in [slot] by [amount]. Returns true when it broke and was removed. Items without durability
     * (no [ItemDef.maxDurability]) never wear.
     */
    fun wear(slot: Int, amount: Int, db: ItemDatabase): Boolean {
        val s = slots.getOrNull(slot) ?: return false
        if (db[s.itemId]?.maxDurability == null) return false
        val left = (s.durability ?: 0) - amount
        if (left > 0) {
            s.durability = left
            return false
        }
        slots[slot] = null
        return true
    }
}
