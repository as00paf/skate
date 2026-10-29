package com.pafoid.skate.game.prefabs

import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.ecs.components.BoxCollider3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PrefabGeometryTest {

    @Test
    fun Prefab_SkateboardColliderIsLongAlongZAndSitsOnGround() {
        val board = Skateboard(`3dModel`())
        val box = board.getComponent<BoxCollider3D>()!!
        val scale = board.getComponent<Transform>()!!.scale

        // World size after the transform scale: 0.2 m wide (x), 0.76 m long (z).
        assertEquals(0.1f, box.halfExtents.x * scale.x, 1e-4f)
        assertEquals(0.38f, box.halfExtents.z * scale.z, 1e-4f)
        // Box bottom is at the model origin (ground contact).
        assertEquals(0f, (box.offset.y - box.halfExtents.y) * scale.y, 1e-4f)
    }

    @Test
    fun Prefab_FloorTopSurfaceIsAtGroundLevel() {
        val floor = Floor("Floor", `3dModel`(), Material())
        val t = floor.getComponent<Transform>()!!
        // Unit cube spans y in [-1, 1].
        assertEquals(0f, t.translation.y + t.scale.y, 1e-4f)
    }
}
