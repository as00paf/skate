package com.pafoid.skate.engine.physics3d

import com.pafoid.skate.engine.physics3d.bullet.BulletPhysics3D
import com.pafoid.skate.engine.render.renderer.DebugRenderer

enum class PhysicsBackend { BULLET }

/** Single entry point for creating [IPhysics3D] backends. */
object Physics3DFactory {
    /** Backend used when none is given; can be changed before scenes are created. */
    @Volatile
    var defaultBackend: PhysicsBackend = PhysicsBackend.BULLET

    fun create(debugRenderer: DebugRenderer, backend: PhysicsBackend = defaultBackend): IPhysics3D =
        when (backend) {
            PhysicsBackend.BULLET -> BulletPhysics3D(debugRenderer)
        }
}
