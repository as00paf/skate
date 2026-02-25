package com.pafoid.skate.engine.utils

import kotlin.math.round

/**
 * Utility object for encoding and decoding entity IDs for picking/rendering.
 * 
 * Entity IDs are encoded by adding 1 before being passed to shaders, and decoded
 * by subtracting 1 when read back from the GPU. This is necessary because 0 is
 * reserved to mean "no entity" or "nothing hit" in the picking system.
 * 
 * Using a centralized encoder/decoder ensures consistency across the codebase
 * and makes the encoding logic explicit and documented.
 * 
 * All functions work with Float to match GPU uniform types and avoid casting.
 */
object EntityIdEncoder {
    /**
     * The offset added to entity IDs during encoding.
     * This reserves 0 to mean "no entity" in the picking system.
     */
    private const val OFFSET = 1f

    /**
     * Encodes an entity ID for GPU rendering.
     * Adds the offset to reserve 0 for "no entity".
     *
     * @param id The entity's ID
     * @return The encoded ID ready for GPU upload
     */
    fun encode(id: Int): Float = id.toFloat() + OFFSET

    /**
     * Decodes an entity ID read from GPU picking.
     * Subtracts the offset to recover the original ID.
     * Uses roundToInt() to prevent floating point interpolation/truncation errors.
     *
     * @param encodedId The ID read from the picking texture (already a float from GPU)
     * @return The original entity ID
     */
    fun decode(encodedId: Float): Int = round((encodedId - OFFSET)).toInt()

    /**
     * Represents "no entity" or "nothing hit" in the picking system.
     * This is the encoded value of 0 (which represents nothing).
     */
    const val NO_ENTITY: Float = 0f
}
