package com.pafoid.skate.engine.utils

/**
 * Named constants for PBR texture slots.
 * 
 * These constants define the texture unit bindings for the PBR material system.
 * Using named constants instead of magic numbers improves code readability and
 * makes it easier to modify texture slot assignments.
 * 
 * Texture slots are used in the following order:
 * - Slot 0: Base Color (albedo/diffuse)
 * - Slot 1: Normal Map (surface normals for lighting)
 * - Slot 2: Metallic Roughness (metalness and roughness packed in RG channels)
 * - Slot 3: Ambient Occlusion (AO occlusion factor)
 * - Slot 4: Emissive (self-illumination color)
 */
object TextureSlots {
    /** Base Color / Albedo texture slot */
    const val BASE_COLOR = 0

    /** Normal Map texture slot */
    const val NORMAL = 1

    /** Metallic Roughness texture slot */
    const val METALLIC_ROUGHNESS = 2

    /** Ambient Occlusion texture slot */
    const val AO = 3

    /** Emissive texture slot */
    const val EMISSIVE = 4

    /** Total number of PBR texture slots */
    const val COUNT = 5
}
