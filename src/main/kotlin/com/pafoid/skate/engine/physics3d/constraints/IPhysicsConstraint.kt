package com.pafoid.skate.engine.physics3d.constraints

interface IPhysicsConstraint {
    /** Backend-owned joint object, set by the backend when the constraint is added. */
    var rawJoint: Any?
}
