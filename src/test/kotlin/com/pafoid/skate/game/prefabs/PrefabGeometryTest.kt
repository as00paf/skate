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
    fun Prefab_SkateboardPhysicsIsUnscaledAndCentred() {
        val board = Skateboard(`3dModel`())
        val box = board.getComponent<BoxCollider3D>()!!
        val visual = board.children.single()

        // Collider is in world meters on an unscaled root: 0.2 m wide (x), 0.76 m long (z), centred.
        assertEquals(1f, board.getComponent<Transform>()!!.scale.x, 0f)
        assertEquals(0.1f, box.halfExtents.x, 1e-4f)
        assertEquals(0.38f, box.halfExtents.z, 1e-4f)
        assertEquals(0f, box.offset.length(), 0f)
        // The model origin (wheels on the ground) sits at the box bottom.
        assertEquals(-box.halfExtents.y, visual.getComponent<Transform>()!!.translation.y, 1e-4f)
        assertEquals(Skateboard.MODEL_SCALE, visual.getComponent<Transform>()!!.scale.x, 0f)
    }

    @Test
    fun Prefab_FloorTopSurfaceIsAtGroundLevel() {
        val floor = Floor("Floor", `3dModel`(), Material())
        val t = floor.getComponent<Transform>()!!
        // Unit cube spans y in [-1, 1].
        assertEquals(0f, t.translation.y + t.scale.y, 1e-4f)
    }
}
