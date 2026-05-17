package com.pafoid.skate.editor.data

import com.pafoid.skate.game.prefabs.MaterialType

data class PrefabData(
    val name: String,
    val type: PrefabType,
    val modelPath: String?,
    val dragDropPayloadType: String? = null,
    val material: MaterialType? = null,
) {
    companion object {
        const val PAYLOAD_RAIL = "PREFAB_RAIL"
        const val PAYLOAD_LEDGE = "PREFAB_LEDGE"
        const val PAYLOAD_KICKER = "PREFAB_KICKER"
        const val PAYLOAD_MANUAL_PAD = "PREFAB_MANUAL_PAD"
        const val PAYLOAD_BANK = "PREFAB_BANK"
        const val PAYLOAD_QUARTER_PIPE = "PREFAB_QUARTER_PIPE"
        const val PAYLOAD_SKATEBOARD = "PREFAB_SKATEBOARD"
        const val PAYLOAD_SKATER = "PREFAB_SKATER"

        fun createTemplate(
            name: String,
            type: PrefabType,
            modelPath: String?,
            payloadType: String? = null
        ): PrefabData {
            return PrefabData(name, type, modelPath, payloadType)
        }

        fun expandToVariants(
            template: PrefabData,
            materials: List<MaterialType>
        ): List<PrefabData> {
            return materials.map { material ->
                PrefabData(
                    name = "${template.name} (${material.displayName})",
                    type = template.type,
                    modelPath = template.modelPath,
                    dragDropPayloadType = template.dragDropPayloadType,
                    material = material
                )
            }
        }

        fun createVariant(
            name: String,
            type: PrefabType,
            modelPath: String?,
            payloadType: String?,
            material: MaterialType
        ): PrefabData {
            return PrefabData("$name (${material.displayName})", type, modelPath, payloadType, material)
        }
    }
}
