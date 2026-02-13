package com.pafoid.skate.engine.physics3d

import com.jme3.bullet.collision.PhysicsRayTestResult

fun PhysicsRayTestResult.toRayTestResult(): RayTestResult {
    return RayTestResult(this.hitFraction)
}
