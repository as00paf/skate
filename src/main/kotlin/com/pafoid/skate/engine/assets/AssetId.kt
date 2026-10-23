package com.pafoid.skate.engine.assets

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.UUID

/** Stable 128-bit asset identifier, serialised as a 32-char lowercase hex string. */
@Serializable(with = AssetIdSerializer::class)
@JvmInline
value class AssetId(val value: String) {
    init {
        require(HEX.matches(value)) { "Invalid AssetId: $value" }
    }

    override fun toString(): String = value

    companion object {
        private val HEX = Regex("[0-9a-f]{32}")

        fun generate(): AssetId = AssetId(UUID.randomUUID().toString().replace("-", ""))

        fun parseOrNull(text: String?): AssetId? =
            text?.trim()?.lowercase()?.takeIf { HEX.matches(it) }?.let { AssetId(it) }
    }
}

object AssetIdSerializer : KSerializer<AssetId> {
    override val descriptor = PrimitiveSerialDescriptor("AssetId", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: AssetId) = encoder.encodeString(value.value)
    override fun deserialize(decoder: Decoder): AssetId = AssetId(decoder.decodeString())
}
