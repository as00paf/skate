package com.pafoid.skate.engine.utils

import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.AssetId
import com.pafoid.skate.engine.assets.AssetPaths
import com.pafoid.skate.engine.assets.AssetRegistry
import com.pafoid.skate.engine.assets.EngineDefaultResolver
import com.pafoid.skate.engine.assets.EngineDefaults
import com.pafoid.skate.engine.assets.ProjectLayout
import com.pafoid.skate.engine.assets.pack.PackedGuids
import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.MeshCollider3D
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.physics3d.CollisionMeshSource
import com.pafoid.skate.engine.physics3d.CollisionMeshes
import com.pafoid.skate.engine.physics3d.ColliderShape
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.ByteBuffer

/** AST-35: the pack's GUID table lets an exported game resolve `MeshCollider3D.meshRef` (PHY-24). */
class PackedGuidsTest {
    @TempDir
    lateinit var dir: File

    private val projectDir get() = File(dir, "Game").also { ProjectLayout.create(it) }

    @AfterEach
    fun tearDown() {
        CollisionMeshes.source = CollisionMeshSource.NONE
    }

    /** A 2-triangle collision quad as OBJ, registered so it has a GUID. */
    private fun collisionModel(): Pair<File, AssetId> {
        val obj = File(ProjectLayout.assetsDir(projectDir), "Models/city/chunk_0_0.collision.obj").apply {
            parentFile.mkdirs()
            writeText("v -5 0 -5\nv 5 0 -5\nv 5 0 5\nv -5 0 5\nf 1 3 2\nf 1 4 3\n")
        }
        return obj to AssetRegistry(projectDir).register(obj)
    }

    private fun project(scene: Scene): Project {
        val paths = AssetPaths().apply { projectRoot = projectDir.absoluteFile }
        val sceneFile = File(ProjectLayout.scenesDir(projectDir), "Main.scene")
        sceneFile.writeText(Serializer(paths).encodeFile(FileKind.SCENE, scene))
        val file = File(projectDir, "Game.skateproject").apply { writeText("{}") }
        return Project(name = "Game", projectPath = file.absolutePath, defaultScene = sceneFile.absolutePath, scenesPath = listOf(sceneFile.absolutePath))
    }

    private fun openPack(out: File): AssetPaths {
        val bin = File(out, AssetsPacker.BIN_FILE).readBytes()
        val headerSize = ByteBuffer.wrap(bin.copyOfRange(0, Int.SIZE_BYTES)).int + Int.SIZE_BYTES
        val atlas = Serializer().decode<Atlas>(bin.copyOfRange(Int.SIZE_BYTES, headerSize).toString(Charsets.UTF_8))
        val resolver = AssetsResolver(Serializer(), LoggerService()).apply { initialize(atlas, bin, headerSize) }
        return AssetPaths().apply { pack = resolver; engineDefaults = EngineDefaultResolver(File(dir, "no-engine")) }
    }

    @Test
    fun ExportedGame_ResolvesAReferencedMeshCollider() {
        val (_, guid) = collisionModel()
        val scene = Scene("Main").apply { addChild(GameObject("Chunk").addComponent(MeshCollider3D.fromModel(guid.value))) }
        val out = File(dir, "out")
        assertTrue(AssetsPacker(LoggerService(), gameStringsDir = File(dir, "no-strings")).pack(project(scene), outputDir = out))
        projectDir.renameTo(File(dir, "Hidden")) // the game has only the pack

        val game = openPack(out)
        assertEquals("Assets/Models/city/chunk_0_0.collision.obj", PackedGuids.read(game)[guid.value])
        CollisionMeshes.source = PackedGuids.collisionMeshSource(game)
        val loaded = Serializer().decodeFile<Scene>(FileKind.SCENE, game.readText("Scenes/Main.scene")!!)
        val shape = loaded.children.single().getComponent<MeshCollider3D>()!!.toShapeOrNull()

        assertTrue(shape is ColliderShape.TriangleMesh, "$shape")
        assertEquals(6, (shape as ColliderShape.TriangleMesh).indices.size)
        assertEquals(emptyList<Any>(), CollisionMeshes.drainFailures())
    }

    @Test
    fun Resolve_GuidsThroughTheTable_EngineGuidsDerived_PathsAsThemselves() {
        val id = AssetId.generate().value
        val table = mapOf(id to "Assets/Models/a.obj")
        assertEquals("Assets/Models/a.obj", PackedGuids.resolve(table, id))
        assertEquals(EngineDefaults.Models.CUBE, PackedGuids.resolve(table, EngineDefaults.idOf(EngineDefaults.Models.CUBE).value))
        assertEquals("Assets/Models/b.obj", PackedGuids.resolve(table, "Assets/Models/b.obj"))
        assertNull(PackedGuids.resolve(table, AssetId.generate().value))
        assertEquals(table, PackedGuids.decode(PackedGuids.encode(table)))
    }
}
