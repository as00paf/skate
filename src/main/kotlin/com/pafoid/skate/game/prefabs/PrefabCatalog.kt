package com.pafoid.skate.game.prefabs

import com.pafoid.skate.engine.assets.EngineDefaults
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.procedural.MeshBuilder
import com.pafoid.skate.engine.assets.procedural.PropShapes
import org.joml.Vector3f

/** Sections of the editor's prefab picker, in display order. */
enum class PrefabCategory { PLAYER, STREET, TRANSITION, GENERIC }

/** Every prefab the editor can spawn (see docs/obstacles.md). Display names are `prefab.<lowercase name>` strings. */
enum class PrefabType {
    SKATEBOARD, SKATER,
    LEDGE, HUBBA, MANUAL_PAD, GRIND_BOX, FUNBOX, RAIL, HANDRAIL, KINKED_RAIL, STAIRS_3, STAIRS_5, EURO_GAP,
    KICKER, BANK, QUARTER_PIPE, HALF_PIPE, BOWL,
    POINT_LIGHT, SPOT_LIGHT, CRATE, BARREL, BENCH, TRIGGER_VOLUME, SPAWN_POINT;

    val nameKey: String get() = "prefab.${name.lowercase()}"
}

/**
 * One picker entry.
 *
 * @param modelPath engine-relative model used for the picker thumbnail (and the spawned mesh, bundled as
 *   [bundledModelPath] in projects)
 * @param materials surface choices; the first is the default, empty means the prefab has no surface material
 * @param generatedMesh builds the OBJ written to [modelPath] (null for hand-authored models)
 */
class PrefabEntry(
    val type: PrefabType,
    val category: PrefabCategory,
    val modelPath: String,
    val bundledModelPath: String,
    val materials: List<MaterialType>,
    val generatedMesh: (() -> MeshBuilder)? = null,
)

object PrefabCatalog {

    private val parkSurfaces = listOf(
        MaterialType.CONCRETE, MaterialType.WOOD_BROWN, MaterialType.WOOD_LIGHT, MaterialType.WOOD_TAN, MaterialType.WOOD_DARK,
    )
    private val metal = listOf(MaterialType.METAL)

    val entries: List<PrefabEntry> = listOf(
        PrefabEntry(PrefabType.SKATEBOARD, PrefabCategory.PLAYER, Assets.Models.SKATEBOARD, EngineDefaults.Models.SKATEBOARD, emptyList(), SkateboardShape::mesh),
        PrefabEntry(PrefabType.SKATER, PrefabCategory.PLAYER, Assets.Models.JAMES, EngineDefaults.Models.JAMES, emptyList()),

        PrefabEntry(PrefabType.LEDGE, PrefabCategory.STREET, Assets.Models.LEDGE, EngineDefaults.Models.LEDGE, parkSurfaces),
        park(PrefabType.HUBBA, PrefabCategory.STREET, Assets.Models.HUBBA, EngineDefaults.Models.HUBBA, parkSurfaces),
        PrefabEntry(PrefabType.MANUAL_PAD, PrefabCategory.STREET, Assets.Models.MANUAL_PAD, EngineDefaults.Models.MANUAL_PAD, parkSurfaces),
        park(PrefabType.GRIND_BOX, PrefabCategory.STREET, Assets.Models.GRIND_BOX, EngineDefaults.Models.GRIND_BOX, parkSurfaces),
        park(PrefabType.FUNBOX, PrefabCategory.STREET, Assets.Models.FUNBOX, EngineDefaults.Models.FUNBOX, parkSurfaces),
        PrefabEntry(PrefabType.RAIL, PrefabCategory.STREET, Assets.Models.RAIL, EngineDefaults.Models.RAIL, metal),
        park(PrefabType.HANDRAIL, PrefabCategory.STREET, Assets.Models.HANDRAIL, EngineDefaults.Models.HANDRAIL, metal),
        park(PrefabType.KINKED_RAIL, PrefabCategory.STREET, Assets.Models.KINKED_RAIL, EngineDefaults.Models.KINKED_RAIL, metal),
        park(PrefabType.STAIRS_3, PrefabCategory.STREET, Assets.Models.STAIRS_3, EngineDefaults.Models.STAIRS_3, parkSurfaces),
        park(PrefabType.STAIRS_5, PrefabCategory.STREET, Assets.Models.STAIRS_5, EngineDefaults.Models.STAIRS_5, parkSurfaces),
        park(PrefabType.EURO_GAP, PrefabCategory.STREET, Assets.Models.EURO_GAP, EngineDefaults.Models.EURO_GAP, parkSurfaces),

        park(PrefabType.KICKER, PrefabCategory.TRANSITION, Assets.Models.KICKER, EngineDefaults.Models.KICKER, parkSurfaces),
        park(PrefabType.BANK, PrefabCategory.TRANSITION, Assets.Models.BANK, EngineDefaults.Models.BANK, parkSurfaces),
        park(PrefabType.QUARTER_PIPE, PrefabCategory.TRANSITION, Assets.Models.QUARTER_PIPE, EngineDefaults.Models.QUARTER_PIPE, parkSurfaces),
        park(PrefabType.HALF_PIPE, PrefabCategory.TRANSITION, Assets.Models.HALF_PIPE, EngineDefaults.Models.HALF_PIPE, parkSurfaces),
        park(PrefabType.BOWL, PrefabCategory.TRANSITION, Assets.Models.BOWL, EngineDefaults.Models.BOWL, parkSurfaces),

        PrefabEntry(PrefabType.POINT_LIGHT, PrefabCategory.GENERIC, Assets.Models.SPHERE, EngineDefaults.Models.SPHERE, emptyList()),
        PrefabEntry(PrefabType.SPOT_LIGHT, PrefabCategory.GENERIC, Assets.Models.SPHERE, EngineDefaults.Models.SPHERE, emptyList()),
        PrefabEntry(PrefabType.CRATE, PrefabCategory.GENERIC, Assets.Models.CUBE, EngineDefaults.Models.CUBE, listOf(MaterialType.WOOD_BROWN)),
        PrefabEntry(PrefabType.BARREL, PrefabCategory.GENERIC, Assets.Models.CYLINDER, EngineDefaults.Models.CYLINDER, metal),
        PrefabEntry(PrefabType.BENCH, PrefabCategory.GENERIC, Assets.Models.BENCH, EngineDefaults.Models.BENCH, listOf(MaterialType.WOOD_DARK), PropShapes::bench),
        PrefabEntry(PrefabType.TRIGGER_VOLUME, PrefabCategory.GENERIC, Assets.Models.CUBE, EngineDefaults.Models.CUBE, emptyList()),
        PrefabEntry(PrefabType.SPAWN_POINT, PrefabCategory.GENERIC, Assets.Models.SPAWN_MARKER, EngineDefaults.Models.SPAWN_MARKER, emptyList(), PropShapes::spawnMarker),
    )

    private val byType = entries.associateBy { it.type }

    init {
        check(byType.keys == PrefabType.entries.toSet()) { "every PrefabType needs exactly one catalog entry" }
    }

    fun entry(type: PrefabType): PrefabEntry = byType.getValue(type)

    fun inCategory(category: PrefabCategory): List<PrefabEntry> = entries.filter { it.category == category }

    /** Generated obstacle geometry and collision for [type], or null for hand-authored or non-obstacle prefabs. */
    fun shape(type: PrefabType): ObstacleShape? = when (type) {
        PrefabType.HUBBA -> SkateparkShapes.hubba()
        PrefabType.GRIND_BOX -> SkateparkShapes.grindBox()
        PrefabType.FUNBOX -> SkateparkShapes.funbox()
        PrefabType.HANDRAIL -> SkateparkShapes.handrail()
        PrefabType.KINKED_RAIL -> SkateparkShapes.kinkedRail()
        PrefabType.STAIRS_3 -> SkateparkShapes.stairs(3)
        PrefabType.STAIRS_5 -> SkateparkShapes.stairs(5)
        PrefabType.EURO_GAP -> SkateparkShapes.euroGap()
        PrefabType.KICKER -> SkateparkShapes.kicker()
        PrefabType.BANK -> SkateparkShapes.bank()
        PrefabType.QUARTER_PIPE -> SkateparkShapes.quarterPipe()
        PrefabType.HALF_PIPE -> SkateparkShapes.halfPipe()
        PrefabType.BOWL -> SkateparkShapes.bowl()
        else -> null
    }

    /** Picks [requested] when the prefab offers it, else the prefab's default surface (null when it has none). */
    fun materialFor(type: PrefabType, requested: MaterialType?): MaterialType? {
        val options = entry(type).materials
        return requested?.takeIf { it in options } ?: options.firstOrNull()
    }

    /** Offset from the ground point a prefab is spawned on (most stand on it; lights and centred props are raised). */
    fun defaultPosition(type: PrefabType): Vector3f = when (type) {
        PrefabType.POINT_LIGHT, PrefabType.SPOT_LIGHT -> Vector3f(0f, 3f, 0f)
        PrefabType.CRATE -> Vector3f(0f, 0.4f, 0f)
        // Half of the 0.3-scaled cylinder.obj height (2.85), so the barrel spawns resting on the ground.
        PrefabType.BARREL -> Vector3f(0f, 0.4276f, 0f)
        PrefabType.TRIGGER_VOLUME -> Vector3f(0f, 1f, 0f)
        else -> Vector3f()
    }

    private fun park(type: PrefabType, category: PrefabCategory, model: String, bundled: String, materials: List<MaterialType>) =
        PrefabEntry(type, category, model, bundled, materials) { shape(type)!!.mesh }
}
