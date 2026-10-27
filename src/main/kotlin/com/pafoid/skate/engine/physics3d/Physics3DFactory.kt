package com.pafoid.skate.engine.physics3d

import com.pafoid.skate.engine.physics3d.bullet.BulletPhysics3D
import com.pafoid.skate.engine.physics3d.simple.SimplePhysics3D
import com.pafoid.skate.engine.render.renderer.DebugRenderer

/** [SIMPLE] is the PHY-05 spike backend (bounding spheres only, no joints); see docs/design/PHY-05.md. */
enum class PhysicsBackend { BULLET, SIMPLE }

/** Single entry point for creating [IPhysics3D] backends. */
object Physics3DFactory {
    /** Backend used when none is given; can be changed before scenes are created. */
    @Volatile
    var defaultBackend: PhysicsBackend = PhysicsBackend.BULLET

    fun create(debugRenderer: DebugRenderer, backend: PhysicsBackend = defaultBackend): IPhysics3D =
        when (backend) {
            PhysicsBackend.BULLET -> BulletPhysics3D(debugRenderer)
            PhysicsBackend.SIMPLE -> SimplePhysics3D()
        }
}
