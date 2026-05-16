package com.pafoid.skate.engine.physics3d

import com.jme3.bullet.collision.PhysicsRayTestResult
import com.jme3.math.Quaternion
import com.pafoid.skate.engine.utils.JmeVector3f
import org.joml.Quaternionf
import org.joml.Vector3f

fun PhysicsRayTestResult.toRayTestResult(): RayTestResult {
    return RayTestResult(this.hitFraction)
}

fun JmeVector3f.toVector3f(): Vector3f {
    return Vector3f(this.x, this.y, this.z)
}

fun Quaternion.toQuaternionf(): Quaternionf {
    return Quaternionf(this.x, this.y, this.z, this.w)
}

fun Quaternionf.toQuaternion(): Quaternion {
    return Quaternion(this.x, this.y, this.z, this.w)
}