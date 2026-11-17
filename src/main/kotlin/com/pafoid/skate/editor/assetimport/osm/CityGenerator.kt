package com.pafoid.skate.editor.assetimport.osm

import com.pafoid.skate.editor.assetstore.AttributionLedger
import com.pafoid.skate.editor.assetstore.LedgerAuthor
import com.pafoid.skate.editor.assetstore.LedgerEntry
import com.pafoid.skate.editor.assetstore.LedgerLicense
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.AssetFolder
import com.pafoid.skate.engine.assets.AssetId
import com.pafoid.skate.engine.assets.AssetMeta
import com.pafoid.skate.engine.assets.EngineDefaults
import com.pafoid.skate.engine.assets.PrefabStore
import com.pafoid.skate.engine.assets.ProjectLayout
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.gltf.GlbWriter
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.BoxCollider3D
import com.pafoid.skate.engine.ecs.components.MeshCollider3D
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.prefab.PrefabAsset
import com.pafoid.skate.engine.ecs.prefab.PrefabService
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.physics3d.PhysicsBackend
import com.pafoid.skate.engine.render.material.MaterialAsset
import com.pafoid.skate.engine.render.material.MaterialSchema
import org.joml.Vector3f
import java.io.File
import java.time.Instant
import java.util.Locale

/** A generation request: [box] is the selected area, [name] the place name (or null for coordinates). */
data class CityRequest(val box: GeoBox, val name: String? = null, val options: CityOptions = CityOptions())

/** What [CityGenerator] wrote; [files] are absolute, the prefab first. [root] is the generated tree (not in a scene). */
class CityResult(
    val slug: String,
    val prefabFile: File,
    val prefab: PrefabAsset,
    val root: GameObject,
    val snapshotFile: File,
    val files: List<File>,
    val chunks: List<CityChunk>,
    val ledgerEntry: LedgerEntry,
    val backend: PhysicsBackend,
)

/**
 * Writes a generated city into a project (AST-17 §4.7, §5, §7):
 *
 * ```
 * Assets/Data/<city>.osm.json                       snapshot (never packed)
 * Assets/Models/<city>/chunk_<x>_<z>_<material>.glb render parts, one per material and chunk
 * Assets/Models/<city>/chunk_<x>_<z>.collision.glb  collider source for MeshCollider3D.meshRef (Bullet/Jolt)
 * Assets/Materials/<city>_<material>.skmat          the styling surface; kept when it already exists
 * Assets/Prefabs/<city>.prefab                      City <name> › Chunk_x_z › parts (+ colliders)
 * ```
 *
 * Each render part is its own child object with its `.skmat`, because a `RenderComponent` takes one material file
 * (no per-part `.skmat` binding exists yet); draw calls per chunk stay ≈ the number of materials. On the Simple
 * backend each chunk gets box colliders instead (one GameObject per box, §4.6). Every file gets a `.meta` GUID (kept
 * across regenerations) and the import is recorded in the attribution ledger under ODbL. Runs on a worker: no GL.
 *
 * Adding an instance to the open scene (one undoable command) is the dialog's job (EDT-71).
 */
class CityGenerator(
    private val projectDir: File,
    private val serializer: Serializer,
    private val now: () -> Instant = Instant::now,
) {
    private val dataDir get() = ProjectLayout.typeDir(projectDir, AssetFolder.DATA)
    private val modelsDir get() = ProjectLayout.typeDir(projectDir, AssetFolder.MODELS)
    private val materialsDir get() = ProjectLayout.typeDir(projectDir, AssetFolder.MATERIALS)
    private val prefabsDir get() = ProjectLayout.typeDir(projectDir, AssetFolder.PREFABS)

    /** Saves [snapshot] (the Overpass response for [request]) and generates from it. */
    fun generate(request: CityRequest, snapshot: String, backend: PhysicsBackend): CityResult {
        val slug = citySlug(request.name, request.box.center)
        val snapshotFile = snapshotFile(slug)
        snapshotFile.parentFile.mkdirs()
        snapshotFile.writeText(snapshot)
        writeMeta(snapshotFile, mapOf(
            META_BOX to boxText(request.box),
            META_NAME to request.name.orEmpty(),
            META_RETRIEVED to now().toString(),
        ))
        return build(slug, request, OsmParser.parse(snapshot), snapshotFile, backend)
    }

    /**
     * Rebuilds a city from its saved snapshot, offline (§3.1; Q-N4: after a physics backend change, or with other
     * options). Files that are no longer produced (e.g. collision models after a switch to Simple) are deleted.
     */
    fun regenerate(snapshotFile: File, options: CityOptions, backend: PhysicsBackend): CityResult {
        val meta = AssetMeta.read(snapshotFile) ?: error("${snapshotFile.name} has no .meta with the city area")
        val box = parseBox(meta.settings[META_BOX]) ?: error("${snapshotFile.name}: no area in its .meta")
        val name = meta.settings[META_NAME]?.ifBlank { null }
        val slug = snapshotFile.name.removeSuffix(SNAPSHOT_SUFFIX)
        return build(slug, CityRequest(box, name, options), OsmParser.parse(snapshotFile.readText()), snapshotFile, backend)
    }

    fun snapshotFile(slug: String): File = File(dataDir, slug + SNAPSHOT_SUFFIX)

    private fun build(slug: String, request: CityRequest, data: OsmData, snapshotFile: File, backend: PhysicsBackend): CityResult {
        val chunks = CityBuilder(data, request.box, request.options).build()
        val folder = File(modelsDir, slug)
        val written = ArrayList<File>()

        val materials = chunks.flatMap { it.parts.keys }.toSet().associateWith { writeMaterial(slug, it) }
        val title = request.name ?: "${slug.removePrefix("city_")}"
        val root = GameObject("City $title").addComponent(Transform())
        val boxes = backend == PhysicsBackend.SIMPLE
        for (chunk in chunks) {
            val node = GameObject("Chunk_${chunk.x}_${chunk.z}").addComponent(Transform())
            chunk.parts.forEach { (material, mesh) ->
                val glb = File(folder, "${chunk.name}_${material.id}.glb")
                GlbWriter.write(glb, "${chunk.name}_${material.id}", listOf(mesh))
                ensureMeta(glb); written += glb
                node.addChild(GameObject(material.id).addComponent(Transform())
                    .addComponent(RenderComponent(`3dModel`(glb.absolutePath), materialPath = materials.getValue(material).absolutePath,
                        castShadow = material == CityMaterial.WALL || material == CityMaterial.ROOF)))
            }
            if (boxes) {
                if (chunk.boxes.isNotEmpty()) {
                    val colliders = GameObject("Colliders").addComponent(Transform())
                    chunk.boxes.forEach { box ->
                        colliders.addChild(GameObject(box.name)
                            .addComponent(Transform(
                                Vector3f(box.center[0].toFloat(), box.center[1].toFloat(), box.center[2].toFloat()),
                                rotation = Vector3f(0f, box.yawDegrees.toFloat(), 0f),
                            ))
                            .addComponent(RigidBody3D(0f, bodyType = BodyType.Static))
                            .addComponent(BoxCollider3D(Vector3f(box.halfExtents[0].toFloat(), box.halfExtents[1].toFloat(), box.halfExtents[2].toFloat()), margin = 0f)))
                    }
                    node.addChild(colliders)
                }
            } else chunk.collision?.let { mesh ->
                val glb = File(folder, "${chunk.name}$COLLISION_SUFFIX")
                GlbWriter.write(glb, "${chunk.name}_collision", listOf(mesh))
                val guid = ensureMeta(glb); written += glb
                node.addComponent(RigidBody3D(0f, bodyType = BodyType.Static))
                node.addComponent(MeshCollider3D.fromModel(guid.value))
            }
            root.addChild(node)
        }
        // Files of an earlier generation that this one did not produce (other chunks, collision models after Simple).
        folder.listFiles().orEmpty().filter { it.isFile && !AssetMeta.isSidecar(it) && it !in written }.forEach {
            it.delete(); AssetMeta.sidecarOf(it).delete()
        }

        val prefabFile = File(prefabsDir, "$slug.${PrefabStore.EXTENSION}")
        val prefab = PrefabService(serializer).createAsset("$PREFAB_ID_PREFIX$slug", root)
        PrefabStore(serializer).save(prefab, prefabFile)
        ensureMeta(prefabFile)

        val files = listOf(prefabFile, snapshotFile) + written + materials.values
        val entry = ledgerEntry(slug, request, data, snapshotFile, files)
        AttributionLedger.save(projectDir, AttributionLedger.load(projectDir).upsert(entry))
        return CityResult(slug, prefabFile, prefab, root, snapshotFile, files, chunks, entry, backend)
    }

    /** The category's `.skmat`, written once: a user's later edits (the styling surface, answer 5) are kept. */
    private fun writeMaterial(slug: String, material: CityMaterial): File {
        val file = File(materialsDir, "${slug}_${material.id}.${MaterialAsset.EXTENSION}")
        if (!file.isFile) {
            val color = CityBuilder.BASE_COLORS.getValue(material).toList()
            val texture = TEXTURES[material]
            val asset = MaterialAsset(
                name = "${slug}_${material.id}",
                params = mapOf(
                    MaterialSchema.Companion.Param.BASE_COLOR to color,
                    MaterialSchema.Companion.Param.ROUGHNESS_FACTOR to listOf(if (material == CityMaterial.WATER) 0.1f else 0.9f),
                ),
                textures = texture?.let { mapOf(MaterialSchema.Companion.Tex.BASE_COLOR to it) }.orEmpty(),
            )
            file.parentFile.mkdirs()
            file.writeText(asset.toJson())
        }
        ensureMeta(file)
        return file
    }

    private fun ledgerEntry(slug: String, request: CityRequest, data: OsmData, snapshot: File, files: List<File>): LedgerEntry {
        val retrieved = AssetMeta.read(snapshot)?.settings?.get(META_RETRIEVED) ?: now().toString()
        val c = request.box.center
        return LedgerEntry(
            assetIds = files.mapNotNull { AssetMeta.read(it)?.guid?.value },
            provider = PROVIDER,
            sourceId = "${boxText(request.box)}@${data.timestamp ?: retrieved}",
            title = request.name ?: slug,
            sourceUrl = String.format(Locale.ROOT, "https://www.openstreetmap.org/#map=16/%.5f/%.5f", c.lat, c.lon),
            authors = listOf(LedgerAuthor(OSM_AUTHOR, OSM_COPYRIGHT_URL)),
            license = LedgerLicense(ODBL_SPDX, ODBL_URL),
            modifications = listOf("Generated 3D city (buildings, roads, areas) by the Skate city generator"),
            retrievedAt = retrieved,
            folder = ProjectLayout.assetPath("${AssetFolder.MODELS.dirName}/$slug"),
        )
    }

    private fun ensureMeta(file: File): AssetId {
        AssetMeta.read(file)?.let { return it.guid }
        val id = AssetId.generate()
        AssetMeta.write(file, AssetMeta(guid = id, importer = AssetMeta.importerFor(file)))
        return id
    }

    private fun writeMeta(file: File, settings: Map<String, String>) {
        val guid = AssetMeta.read(file)?.guid ?: AssetId.generate()
        AssetMeta.write(file, AssetMeta(guid = guid, importer = AssetMeta.importerFor(file), settings = settings))
    }

    companion object {
        const val PROVIDER = "osm"
        const val SNAPSHOT_SUFFIX = ".osm.json"
        const val COLLISION_SUFFIX = ".collision.glb"
        const val PREFAB_ID_PREFIX = "city_"
        const val META_BOX = "osm.box"
        const val META_NAME = "osm.name"
        const val META_RETRIEVED = "osm.retrievedAt"
        const val ODBL_SPDX = "ODbL-1.0"
        const val ODBL_URL = "https://opendatacommons.org/licenses/odbl/1-0/"
        const val OSM_AUTHOR = "OpenStreetMap contributors"
        const val OSM_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"

        /** The attribution line the dialog shows and the export credits carry (§7). */
        const val ATTRIBUTION = "© OpenStreetMap contributors, ODbL 1.0"

        private val TEXTURES = mapOf(
            CityMaterial.ROAD to EngineDefaults.Textures.ASPHALT,
            CityMaterial.PATH to EngineDefaults.Textures.CONCRETE_SIMPLE,
            CityMaterial.PLAZA to EngineDefaults.Textures.CONCRETE_SIMPLE,
            CityMaterial.WALL to EngineDefaults.Textures.CONCRETE_SIMPLE,
        )

        fun boxText(box: GeoBox): String = String.format(Locale.ROOT, "%.6f,%.6f,%.6f,%.6f", box.south, box.west, box.north, box.east)

        fun parseBox(text: String?): GeoBox? {
            val v = text?.split(',')?.mapNotNull { it.trim().toDoubleOrNull() } ?: return null
            return if (v.size == 4) runCatching { GeoBox(v[0], v[1], v[2], v[3]) }.getOrNull() else null
        }
    }
}
