package com.pafoid.skate.engine.editor.assetBrowser

import com.pafoid.skate.engine.prefabs.MaterialType

data class PrefabConfig(
    val name: String,
    val type: PrefabType,
    val modelPath: String?,
    val dragDropPayload: String? = null,
    val allowedMaterials: List<MaterialType>,
)