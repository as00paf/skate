package com.pafoid.skate.engine.physics3d.constraints

import com.jme3.bullet.joints.PhysicsJoint

interface IPhysicsConstraint {
    var rawJoint: PhysicsJoint?
    fun createJoint(): PhysicsJoint
}
