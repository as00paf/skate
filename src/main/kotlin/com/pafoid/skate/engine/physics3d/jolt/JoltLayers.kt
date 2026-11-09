package com.pafoid.skate.engine.physics3d.jolt

import com.github.stephengold.joltjni.BroadPhaseLayerInterfaceTable
import com.github.stephengold.joltjni.ObjVsBpFilter
import com.github.stephengold.joltjni.ObjectLayerPairFilterTable

/**
 * Maps the engine's 32-bit layer/mask pairs onto compact slots (native-free, unit tested). Two bodies collide when
 * `(a.layer & b.mask) != 0 && (b.layer & a.mask) != 0`; each distinct (layer, mask) pair gets one slot on first use.
 */
internal class LayerSlots(val capacity: Int) {
    private val layers = IntArray(capacity)
    private val masks = IntArray(capacity)
    private val lookup = HashMap<Long, Int>()

    var size = 0
        private set

    fun layerOf(slot: Int) = layers[slot]
    fun maskOf(slot: Int) = masks[slot]

    /**
     * Slot for ([layer], [mask]). A new slot calls [onNewPair] once for every existing slot (itself included) it
     * collides with, so the native pair table can be updated incrementally.
     */
    fun slotFor(layer: Int, mask: Int, onNewPair: (Int, Int) -> Unit = { _, _ -> }): Int {
        val key = (layer.toLong() shl 32) or (mask.toLong() and 0xFFFFFFFFL)
        lookup[key]?.let { return it }
        check(size < capacity) { "Jolt backend supports at most $capacity distinct collision layer/mask combinations" }
        val slot = size++
        layers[slot] = layer
        masks[slot] = mask
        lookup[key] = slot
        for (other in 0..slot) if (collides(slot, other)) onNewPair(slot, other)
        return slot
    }

    fun collides(a: Int, b: Int): Boolean =
        (layers[a] and masks[b]) != 0 && (layers[b] and masks[a]) != 0

    companion object {
        /** Object layer of [slot]: even for static bodies (non-moving broad phase), odd for moving ones. */
        fun objectLayer(slot: Int, moving: Boolean) = slot * 2 + if (moving) 1 else 0

        fun slotOf(objectLayer: Int) = objectLayer / 2
    }
}

/**
 * Jolt layer tables for one world. Broad phase: [NON_MOVING] and [MOVING]. The object-vs-broad-phase filter accepts
 * everything (it is only an early-out); the exact layer/mask rule lives in the incrementally filled pair table, so no
 * Java callback runs during the simulation.
 */
internal class JoltLayers(slotCapacity: Int = DEFAULT_SLOTS) : AutoCloseable {
    val slots = LayerSlots(slotCapacity)
    private val objectLayerCount = slotCapacity * 2

    val pairFilter = ObjectLayerPairFilterTable(objectLayerCount)
    val broadPhaseLayers = BroadPhaseLayerInterfaceTable(objectLayerCount, BROAD_PHASE_LAYERS).also { table ->
        for (layer in 0 until objectLayerCount) table.mapObjectToBroadPhaseLayer(layer, if (layer % 2 == 0) NON_MOVING else MOVING)
    }
    val objectVsBroadPhase = ObjVsBpFilter(objectLayerCount, BROAD_PHASE_LAYERS)

    /** Object layer for a body on ([layer], [mask]); [moving] is false for static bodies. */
    fun objectLayer(layer: Int, mask: Int, moving: Boolean): Int {
        val slot = slots.slotFor(layer, mask) { a, b ->
            // Static-vs-static pairs never need testing
            pairFilter.enableCollision(LayerSlots.objectLayer(a, true), LayerSlots.objectLayer(b, true))
            pairFilter.enableCollision(LayerSlots.objectLayer(a, true), LayerSlots.objectLayer(b, false))
            pairFilter.enableCollision(LayerSlots.objectLayer(a, false), LayerSlots.objectLayer(b, true))
        }
        return LayerSlots.objectLayer(slot, moving)
    }

    override fun close() {
        objectVsBroadPhase.close()
        broadPhaseLayers.close()
        pairFilter.close()
    }

    companion object {
        const val NON_MOVING = 0
        const val MOVING = 1
        const val BROAD_PHASE_LAYERS = 2
        const val DEFAULT_SLOTS = 128
    }
}
