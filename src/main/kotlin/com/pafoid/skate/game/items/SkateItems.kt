package com.pafoid.skate.game.items

import com.pafoid.skate.engine.assets.AssetFolder
import com.pafoid.skate.engine.assets.PrefabsGenerator
import com.pafoid.skate.engine.assets.ProjectLayout
import com.pafoid.skate.engine.gameplay.items.ItemDatabase
import java.io.File

/**
 * The skate demo's item set (GPL-24 §7): bundled `*.item.json` files, then the open project's `Assets/Items/`
 * folder, whose files override bundled ones with the same id.
 */
object SkateItems {
    const val HAT = "skate.hat"
    const val GLASSES = "skate.glasses"

    /** Bundled item files (classpath resources): a hat and glasses on placeholder meshes (GPL-24e). */
    val BUNDLED: List<String> = listOf(PrefabsGenerator.SKATER_HAT_ITEM, PrefabsGenerator.SKATER_GLASSES_ITEM)

    fun load(db: ItemDatabase, projectRoot: File?) {
        db.loadAll(BUNDLED)
        projectRoot?.let { db.loadFolder(ProjectLayout.typeDir(it, AssetFolder.ITEMS)) }
    }
}
