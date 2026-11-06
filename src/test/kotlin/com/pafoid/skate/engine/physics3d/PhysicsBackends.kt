package com.pafoid.skate.engine.physics3d

import com.pafoid.skate.engine.physics3d.jolt.JoltPhysics3D
import com.pafoid.skate.engine.physics3d.native.NativeLibraryLoader
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import io.mockk.mockk

/**
 * Backends a contract test runs against (PHY-17). Use with
 * `@ParameterizedTest @MethodSource("com.pafoid.skate.engine.physics3d.PhysicsBackends#core")`.
 * A backend joins a list once it implements that feature group.
 */
object PhysicsBackends {
    /** Bodies, shapes, layers, stepping and ray/shape queries. */
    @JvmStatic
    fun core(): List<PhysicsBackend> = listOf(PhysicsBackend.BULLET, PhysicsBackend.JOLT)

    /** Collision and trigger events. */
    @JvmStatic
    fun contacts(): List<PhysicsBackend> = listOf(PhysicsBackend.BULLET)

    /** Creates a world on [backend] through [Physics3DFactory], loading natives first. */
    fun create(backend: PhysicsBackend, debugRenderer: DebugRenderer = mockk(relaxed = true)): IPhysics3D {
        if (backend == PhysicsBackend.BULLET) NativeLibraryLoader().loadNativeLibrary()
        // A silent fallback to Bullet would make a Jolt run pass for the wrong reason
        val world = Physics3DFactory.create(debugRenderer, backend) { error("$backend unavailable: ${it.reason}") }
        if (backend == PhysicsBackend.JOLT) check(world is JoltPhysics3D) { "expected a Jolt world, got ${world::class.simpleName}" }
        return world
    }
}
