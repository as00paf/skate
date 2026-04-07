package com.pafoid.skate.editor.data

import com.pafoid.skate.editor.ui.windows.assetBrowser.PrefabType
import com.pafoid.skate.game.prefabs.MaterialType

data class PrefabConfig(
    val name: String,
    val type: PrefabType,
    val modelPath: String?,
    val dragDropPayload: String? = null,
    val allowedMaterials: List<MaterialType>,
)