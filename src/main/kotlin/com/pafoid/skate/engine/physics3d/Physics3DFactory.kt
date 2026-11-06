package com.pafoid.skate.engine.physics3d

import com.pafoid.skate.engine.physics3d.bullet.BulletPhysics3D
import com.pafoid.skate.engine.physics3d.jolt.JoltPhysics3D
import com.pafoid.skate.engine.physics3d.jolt.JoltRuntime
import com.pafoid.skate.engine.physics3d.native.NativeLibraryLoader
import com.pafoid.skate.engine.physics3d.simple.SimplePhysics3D
import com.pafoid.skate.engine.render.renderer.DebugRenderer

/**
 * [SIMPLE] is the PHY-05 spike backend (bounding spheres only, no joints); see docs/design/PHY-05.md.
 * [JOLT] is Jolt Physics through jolt-jni (PHY-17, in progress: no contact events, joints or debug draw yet);
 * see docs/design/PHY-17.md.
 */
enum class PhysicsBackend { BULLET, SIMPLE, JOLT }

/** Single entry point for creating [IPhysics3D] backends. */
object Physics3DFactory {
    /** Backend used when none is given; can be changed before scenes are created. */
    @Volatile
    var defaultBackend: PhysicsBackend = PhysicsBackend.BULLET

    /** Loads Jolt; returns null when ready, otherwise why it is unavailable. Replaced in tests to force a fallback. */
    internal var loadJolt: () -> String? = { if (JoltRuntime.ensureLoaded()) null else JoltRuntime.failure ?: "unknown error" }

    /**
     * Creates a world on [backend]. When Jolt's native library cannot be loaded, returns a Bullet world instead and
     * tells [onFallback] why.
     */
    fun create(
        debugRenderer: DebugRenderer,
        backend: PhysicsBackend = defaultBackend,
        onFallback: ((PhysicsBackendFallback) -> Unit)? = null,
    ): IPhysics3D =
        when (backend) {
            PhysicsBackend.BULLET -> BulletPhysics3D(debugRenderer)
            PhysicsBackend.SIMPLE -> SimplePhysics3D()
            PhysicsBackend.JOLT -> when (val failure = loadJolt()) {
                null -> JoltPhysics3D(debugRenderer)
                else -> {
                    onFallback?.invoke(PhysicsBackendFallback(PhysicsBackend.JOLT, PhysicsBackend.BULLET, failure))
                    NativeLibraryLoader().loadNativeLibrary()
                    BulletPhysics3D(debugRenderer)
                }
            }
        }
}

/** A world was created on [used] because [requested] was unavailable, for [reason]. */
data class PhysicsBackendFallback(val requested: PhysicsBackend, val used: PhysicsBackend, val reason: String)
