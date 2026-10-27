package com.pafoid.skate.engine.render.texture

import kotlinx.serialization.Serializable
import org.joml.Matrix4f
import org.joml.Vector3f

@Serializable
enum class BillboardMode { NONE, SPHERICAL, CYLINDRICAL }

/** Camera-facing quad math (GL-free). */
object Billboard {
    private val right = Vector3f()
    private val up = Vector3f()
    private val forward = Vector3f()
    private val worldUp = Vector3f(0f, 1f, 0f)

    /**
     * Writes a model matrix for the unit sprite quad used by the 3D batch (corners at x,y in 0..1, z = 1) so it
     * is centered on [position], sized [width] x [height] and faces [cameraPosition].
     * SPHERICAL faces the camera fully; CYLINDRICAL only rotates around world Y. NONE leaves translation+scale.
     */
    fun matrix(
        mode: BillboardMode, position: Vector3f, width: Float, height: Float,
        cameraPosition: Vector3f, dest: Matrix4f,
    ): Matrix4f {
        dest.identity()
        if (mode == BillboardMode.NONE) {
            return dest.translate(position).scale(width, height, 1f).translate(-0.5f, -0.5f, -1f)
        }
        forward.set(cameraPosition).sub(position)
        if (mode == BillboardMode.CYLINDRICAL) forward.y = 0f
        if (forward.lengthSquared() < 1e-12f) forward.set(0f, 0f, 1f)
        forward.normalize()
        val axisUp = if (mode == BillboardMode.CYLINDRICAL || kotlin.math.abs(forward.y) < 0.999f) worldUp else Vector3f(0f, 0f, -1f)
        right.set(axisUp).cross(forward).normalize()
        up.set(forward).cross(right)
        dest.set(
            right.x, right.y, right.z, 0f,
            up.x, up.y, up.z, 0f,
            forward.x, forward.y, forward.z, 0f,
            position.x, position.y, position.z, 1f,
        )
        return dest.scale(width, height, 1f).translate(-0.5f, -0.5f, -1f)
    }
}
