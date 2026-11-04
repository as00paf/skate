package com.pafoid.skate.game.prefabs

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.loaders.AssimpLoader
import com.pafoid.skate.engine.ecs.components.BoxCollider3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.game.player.MountTuning
import com.pafoid.skate.engine.assets.data.models.MeshPart
import com.pafoid.skate.engine.render.VAOLoader
import io.mockk.every
import io.mockk.mockk
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** GPL-16: the game-ready board mesh (SkateboardShape) against the prefab's collider, orientation and ride height. */
class SkateboardModelTest {

    private val mesh = SkateboardShape.mesh()
    private val bounds = mesh.bounds()

    @Test
    fun SkateboardModel_StaysUnderTheTriangleBudget() {
        assertTrue(mesh.triangleCount in 1000..20_000, "${mesh.triangleCount} triangles")
    }

    @Test
    fun SkateboardModel_CommittedObjLoadsAsOneColouredPartUnderBudget() {
        // The real load path minus the GL upload: the VAO loader hands back the arrays it was given.
        val vaoLoader = mockk<VAOLoader> {
            every { loadToVAO(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } answers {
                MeshPart(vertices = firstArg(), indices = arg(3), colors = arg(6))
            }
        }
        val model = AssimpLoader(mockk(), vaoLoader).loadModel(Assets.Models.SKATEBOARD)

        val triangles = model.mesh.sumOf { it.indices.size / 3 }
        assertTrue(triangles < 20_000, "$triangles triangles")
        assertEquals(1, model.mesh.size)
        // Vertex colours survive the OBJ round trip: dark grip tape on top and light wheels both present.
        val colors = model.mesh.single().colors.toList().chunked(4)
        assertTrue(colors.any { it[0] < 0.1f } && colors.any { it[0] > 0.9f }, "vertex colours lost")
        val ys = model.mesh.single().vertices.toList().chunked(3).map { it[1] }
        assertEquals(0f, ys.min(), 1e-3f)
    }

    @Test
    fun SkateboardModel_LongAxisIsZDeckUpWheelsOnTheGround() {
        val (min, max) = bounds
        assertTrue(max.z - min.z > 3f * (max.x - min.x), "long axis is not Z: $min..$max")
        assertEquals(SkateboardShape.LENGTH, max.z - min.z, 1e-3f)
        assertEquals(0f, min.y, 1e-4f)
        assertEquals(0f, min.x + max.x, 1e-4f)
        assertEquals(0f, min.z + max.z, 1e-4f)

        // Grip tape faces up: the highest triangles between the trucks are dark and their normals point up.
        val topFaces = mesh.faceNormals().zip(mesh.triangles()).filter { (_, t) ->
            val c = Vector3f(t.first).add(t.second).add(t.third).div(3f)
            kotlin.math.abs(c.z) < 0.2f && kotlin.math.abs(c.x) < 0.05f && c.y > SkateboardShape.DECK_TOP - 1e-3f
        }
        assertTrue(topFaces.isNotEmpty() && topFaces.all { (n, _) -> n.y > 0.95f })
        // Kicktails: the tips are higher than the middle of the deck, nose (+Z) and tail alike.
        assertTrue(SkateboardShape.kick(0f) == 0f && SkateboardShape.kick(0.4f) > 0.03f && SkateboardShape.kick(-0.4f) > 0.03f)
    }

    @Test
    fun SkateboardModel_ColliderFitsTheMeshAndTheDeckIsAtRideHeight() {
        val board = Skateboard(`3dModel`())
        val visual = board.children.single().getComponent<Transform>()!!
        val box = board.getComponent<BoxCollider3D>()!!
        val (min, max) = bounds.let { (lo, hi) -> Vector3f(lo).add(visual.translation) to Vector3f(hi).add(visual.translation) }
        val lo = Vector3f(box.offset).sub(box.halfExtents)
        val hi = Vector3f(box.offset).add(box.halfExtents)
        val tol = 0.01f

        assertEquals(1f, visual.scale.x, 0f)
        assertTrue(lo.x >= min.x - tol && lo.y >= min.y - tol && lo.z >= min.z - tol, "collider $lo below mesh $min")
        assertTrue(hi.x <= max.x + tol && hi.y <= max.y + tol && hi.z <= max.z + tol, "collider $hi above mesh $max")
        // Box bottom on the wheels' ground contact, box top at the grip tape.
        assertEquals(min.y, lo.y, 1e-4f)
        assertEquals(SkateboardShape.DECK_TOP + visual.translation.y, hi.y, tol)
        // GPL-15: riding holds the feet rideHeight above the ground, which is the deck top.
        assertEquals(MountTuning().rideHeight, SkateboardShape.DECK_TOP, tol)
    }
}
