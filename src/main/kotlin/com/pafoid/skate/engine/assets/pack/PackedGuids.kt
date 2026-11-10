package com.pafoid.skate.engine.assets.pack

import com.pafoid.skate.engine.assets.AssetId
import com.pafoid.skate.engine.assets.AssetPaths
import com.pafoid.skate.engine.assets.EngineDefaults
import com.pafoid.skate.engine.physics3d.ModelCollisionMeshSource
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * The asset GUID → pack path table of an exported game (AST-35). An exported game has no `.meta` sidecars, so GUID
 * references saved in scenes (e.g. `MeshCollider3D.meshRef`, PHY-24) resolve through this pack entry. Engine
 * defaults need no entry: their GUIDs are derived from the `engine:` reference ([EngineDefaults.referenceOf]).
 */
object PackedGuids {
    /** Pack path of the table; not a project path (projects have no top-level `skate/` folder). */
    const val PACK_PATH = "skate/guids.json"
    const val TYPE = "JSON"

    private val json = Json { prettyPrint = false }
    private val serializer = MapSerializer(String.serializer(), String.serializer())

    fun encode(table: Map<String, String>): ByteArray = json.encodeToString(serializer, table.toSortedMap()).toByteArray(Charsets.UTF_8)

    fun decode(bytes: ByteArray): Map<String, String> = json.decodeFromString(serializer, bytes.toString(Charsets.UTF_8))

    fun input(table: Map<String, String>): PackInput = PackInput(PACK_PATH, PACK_PATH, TYPE, encode(table))

    /**
     * Resolves a reference read from a packed scene: a GUID through [table] (or an engine default's derived GUID),
     * anything else (a pack path, an `engine:` reference) as itself; null for an unknown GUID.
     */
    fun resolve(table: Map<String, String>, reference: String): String? {
        val id = AssetId.parseOrNull(reference) ?: return reference
        return table[id.value] ?: EngineDefaults.referenceOf(id)
    }

    /** The exported game's table, read from [paths]' pack; empty when the pack has none (older exports). */
    fun read(paths: AssetPaths): Map<String, String> =
        paths.readBytes(PACK_PATH)?.let { runCatching { decode(it) }.getOrNull() }.orEmpty()

    /** `MeshCollider3D.meshRef` models in an exported game: GUIDs through the table, files extracted from the pack. */
    fun collisionMeshSource(paths: AssetPaths): ModelCollisionMeshSource {
        val table by lazy { read(paths) }
        return ModelCollisionMeshSource(resolve = { resolve(table, it) }, localPath = paths::localPath)
    }
}
