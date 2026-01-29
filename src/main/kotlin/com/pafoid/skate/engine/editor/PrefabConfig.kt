package com.pafoid.skate.engine.editor

data class PrefabConfig(
    val name: String,
    val modelPath: String?,
    val dragDropPayload: String? = null,
    val allowedMaterials: List<MaterialType>,
    val onSpawn: (MaterialType) -> Unit
)