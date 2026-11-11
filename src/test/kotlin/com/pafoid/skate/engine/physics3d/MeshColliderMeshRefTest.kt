package com.pafoid.skate.engine.physics3d

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.AssetId
import com.pafoid.skate.engine.assets.AssetRegistry
import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.attachComponent
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.MeshCollider3D
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.PhysicsSystem
import com.pafoid.skate.engine.events.CollisionMeshUnavailable
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.physics3d.native.NativeLibraryLoader
import io.mockk.mockk
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

/** PHY-24 (AST-17 slice PHY-a): `MeshCollider3D.meshRef` loads its triangles from a model by GUID. */
class MeshColliderMeshRefTest {

    @TempDir
    lateinit var project: File

    private lateinit var registry: AssetRegistry
    private var guidValue = ""
    private lateinit var model: File

    @BeforeEach
    fun setUp() {
        model = File(project, "Assets/Models/city/chunk_0_0.collision.gltf").apply {
            parentFile.mkdirs()
            writeText(quadGltf(size = 10f, height = 2f))
        }
        registry = AssetRegistry(project).also { it.scan() }
        guidValue = registry.idOf("Assets/Models/city/chunk_0_0.collision.gltf")!!.value
        CollisionMeshes.source = ModelCollisionMeshSource.forRegistry(registry)
    }

    @AfterEach
    fun tearDown() {
        CollisionMeshes.source = CollisionMeshSource.NONE
    }

    private fun meshObject(collider: MeshCollider3D) = GameObject("Chunk").apply {
        addComponent(Transform(Vector3f()))
        addComponent(RigidBody3D(0f))
        attachComponent(collider)
    }

    @Test
    fun Reader_ReadsTheTrianglesWithNodeTransformsBakedIn() {
        val mesh = AssimpCollisionMeshReader.read(model.path)

        assertEquals(6, mesh.indices.size)
        assertTrue(mesh.vertices.all { it.y == 2f }, "node translation (0, 2, 0) baked in: ${mesh.vertices}")
        assertEquals(10f, mesh.vertices.maxOf { it.x })
        assertEquals(-10f, mesh.vertices.minOf { it.z })
    }

    @ParameterizedTest
    @MethodSource("com.pafoid.skate.engine.physics3d.PhysicsBackends#core")
    fun MeshRef_CollidesWithTheModelsTriangles(backend: PhysicsBackend) {
        val world = PhysicsBackends.create(backend)
        try {
            world.add(meshObject(MeshCollider3D.fromModel(guidValue)))
            assertEquals(1, world.rigidBodyCount)

            // The quad spans ±10 m at y = 2: a ray down hits it inside, and passes beside it
            val hit = world.raycast(Vector3f(3f, 10f, -4f), Vector3f(3f, -10f, -4f))
            assertNotNull(hit, "ray onto the model's triangles")
            assertEquals(2f, hit!!.point.y, 1e-3f)
            assertNull(world.raycast(Vector3f(15f, 10f, 0f), Vector3f(15f, -10f, 0f)), "outside the model")

            // A box dropped on it comes to rest on the surface
            val box = GameObject("Box").apply {
                addComponent(Transform(Vector3f(0f, 4f, 0f)))
                addComponent(RigidBody3D(1f))
                attachComponent(com.pafoid.skate.engine.ecs.components.BoxCollider3D(Vector3f(0.5f)))
            }
            world.add(box)
            repeat(180) { world.step(1f / 60f) }
            assertEquals(2.5f, box.getComponent<RigidBody3D>()!!.getWorldPosition().y, 0.05f)
        } finally {
            world.destroy()
        }
    }

    @ParameterizedTest
    @MethodSource("com.pafoid.skate.engine.physics3d.PhysicsBackends#core")
    fun MeshRef_UnknownGuid_AddsNoBodyAndReportsOnce(backend: PhysicsBackend) {
        val world = PhysicsBackends.create(backend)
        try {
            val missing = AssetId.generate().value
            world.add(meshObject(MeshCollider3D.fromModel(missing)))
            world.add(meshObject(MeshCollider3D.fromModel(missing)))

            assertEquals(0, world.rigidBodyCount, "no body and no 1 m quad fallback")
            assertEquals(listOf(CollisionMeshFailure(missing, "unknown asset reference")), CollisionMeshes.drainFailures())
        } finally {
            world.destroy()
        }
    }

    @Test
    fun MeshRef_UnreadableModel_LeavesOnlyTheOtherColliders() {
        model.writeText("not a model")
        CollisionMeshes.clear()
        val go = meshObject(MeshCollider3D.fromModel(guidValue)).apply {
            attachComponent(com.pafoid.skate.engine.ecs.components.BoxCollider3D(Vector3f(1f)))
        }

        val description = BodyDescription.of(go)!!

        assertEquals(1, description.parts.size)
        assertTrue(description.parts.single().shape is ColliderShape.Box)
        assertEquals(guidValue, CollisionMeshes.drainFailures().single().reference)
    }

    @Test
    fun MeshRef_ModelIsReadOnceAndShared() {
        var loads = 0
        val real = ModelCollisionMeshSource.forRegistry(registry)
        CollisionMeshes.source = CollisionMeshSource { loads++; real.load(it) }

        val a = MeshCollider3D.fromModel(guidValue).toShapeOrNull()
        val b = MeshCollider3D.fromModel(guidValue, Vector3f(1f, 0f, 0f)).toShapeOrNull()

        assertEquals(1, loads)
        assertSame(a, b)
    }

    @Test
    fun MeshRef_ResolvesThroughTheGuidAfterTheModelMoves() {
        val moved = File(project, "Assets/Models/moved/chunk.gltf")
        assertTrue(registry.move(model, moved))
        CollisionMeshes.clear()

        assertNotNull(MeshCollider3D.fromModel(guidValue).toShapeOrNull())
    }

    @Test
    fun MeshRef_Null_KeepsTheInlineTriangles() {
        val inline = MeshCollider3D.fromTriangles(listOf(Triple(Vector3f(0f), Vector3f(1f, 0f, 0f), Vector3f(0f, 0f, 1f))))

        val shape = inline.toShapeOrNull() as ColliderShape.TriangleMesh

        assertEquals(3, shape.vertices.size)
        assertTrue(CollisionMeshes.drainFailures().isEmpty())
    }

    @Test
    fun MeshRef_SavesTheGuidAndNoInlineTriangles() {
        val serializer = Serializer()
        val scene = Scene("City").apply { addChild(meshObject(MeshCollider3D.fromModel(guidValue))) }

        val text = serializer.encodeFile(FileKind.SCENE, scene)
        val loaded = serializer.decodeFile<Scene?>(FileKind.SCENE, text)!!
        val collider = loaded.children.single().getComponent<MeshCollider3D>()!!

        assertEquals(guidValue, collider.meshRef)
        assertTrue(collider.vertices.isEmpty() && collider.indices.isEmpty())
        assertTrue(text.contains(guidValue))
    }

    @Test
    fun PhysicsSystem_PublishesCollisionMeshUnavailableOnce() {
        NativeLibraryLoader().loadNativeLibrary()
        val events = EventSystem()
        val published = mutableListOf<CollisionMeshUnavailable>()
        events.subscribe(CollisionMeshUnavailable::class) { published += it }
        val world = PhysicsBackends.create(PhysicsBackend.BULLET)
        val physics = PhysicsSystem(mockk(relaxed = true), events, world)
        val missing = AssetId.generate().value
        val scene = Scene("City").apply {
            addChild(meshObject(MeshCollider3D.fromModel(missing)))
            addChild(meshObject(MeshCollider3D.fromModel(missing)))
        }
        try {
            physics.init(scene)
            physics.syncEditedTransforms()

            assertEquals(listOf(CollisionMeshUnavailable(missing, "unknown asset reference")), published)
            assertEquals(0, physics.worldBodyCount)
        } finally {
            world.destroy()
        }
    }

    /** A glTF with one 2·[size] m square quad (two triangles) at y = 0, placed by its node at y = [height]. */
    private fun quadGltf(size: Float, height: Float): String {
        val positions = floatArrayOf(-size, 0f, -size, size, 0f, -size, size, 0f, size, -size, 0f, size)
        val indices = shortArrayOf(0, 2, 1, 0, 3, 2)
        val buffer = ByteBuffer.allocate(positions.size * 4 + indices.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        positions.forEach { buffer.putFloat(it) }
        indices.forEach { buffer.putShort(it) }
        val data = Base64.getEncoder().encodeToString(buffer.array())
        return """
            {"asset":{"version":"2.0"},"scene":0,"scenes":[{"nodes":[0]}],
             "nodes":[{"mesh":0,"translation":[0,$height,0]}],
             "meshes":[{"primitives":[{"attributes":{"POSITION":0},"indices":1}]}],
             "buffers":[{"byteLength":${buffer.capacity()},"uri":"data:application/octet-stream;base64,$data"}],
             "bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":48},{"buffer":0,"byteOffset":48,"byteLength":12}],
             "accessors":[
               {"bufferView":0,"componentType":5126,"count":4,"type":"VEC3","min":[-$size,0,-$size],"max":[$size,0,$size]},
               {"bufferView":1,"componentType":5123,"count":6,"type":"SCALAR"}]}
        """.trimIndent()
    }
}
