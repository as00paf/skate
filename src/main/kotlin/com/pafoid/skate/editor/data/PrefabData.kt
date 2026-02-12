package com.pafoid.skate.editor.data

import com.pafoid.skate.editor.windows.assetBrowser.PrefabType
import com.pafoid.skate.game.prefabs.MaterialType

data class PrefabData(
    val name: String,
    val type: PrefabType,
    val modelPath: String?,
    val dragDropPayload: String? = null,
    val material: MaterialType? = null,
)
