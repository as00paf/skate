package com.pafoid.skate.engine.assets.database

import java.util.UUID

/**
 * Type-safe value class for asset GUIDs.
 *
 * Provides compile-time safety to prevent accidentally passing regular strings
 * where a GUID is expected. Backed by a standard UUID string representation.
 */
@JvmInline
value class AssetGuid(val value: String) {
    companion object {
        /**
         * Generate a new random GUID.
         */
        fun generate(): AssetGuid = AssetGuid(UUID.randomUUID().toString())

        /**
         * Parse a raw string into an AssetGuid.
         */
        fun parse(raw: String): AssetGuid = AssetGuid(raw)

        /**
         * Empty/unset GUID sentinel value.
         */
        val EMPTY = AssetGuid("")
    }

    /**
     * True if this GUID is set (not empty).
     */
    val isSet: Boolean get() = value.isNotEmpty()

    override fun toString(): String = value
}
