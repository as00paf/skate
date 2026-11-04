package com.pafoid.skate.editor.data

import com.pafoid.skate.game.prefabs.MaterialType
import com.pafoid.skate.game.prefabs.PrefabType

/** Drag-and-drop payload of a prefab picker tile: what to spawn and with which surface. */
data class PrefabData(
    val type: PrefabType,
    val material: MaterialType? = null,
) {
    companion object {
        const val PAYLOAD = "PREFAB"
    }
}
