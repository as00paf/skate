# PHY-08 – Ragdoll/joint gaps found after PHY-05

- Ragdoll bones and joints were never added to the physics world, so `RagdollState.RAGDOLL` could not simulate.
  `IPhysics3D.createRigidBody` now adds the body (like `createStaticAnchor`); `PhysicsSystem.syncRagdolls` registers
  the joints once, turns bones dynamic in `RAGDOLL` state and removes joints and bones with the component.
- `IPhysics3D.removeRigidBody` added for bodies created without a GameObject.
- `org.joml.Matrix3f.toJme()` transposed the matrix (JOML is `m{column}{row}`); slider joints on non-X axes and any
  non-identity cone-twist frame were wrong.
- Tests: `JointConstraintsTest` (fixed, removal, hinge, slider) and `PhysicsSystemRagdollTest`.
- Not done (candidates): raycast vehicle for general games; joint motors/breakable joints; static anchors are not removed
  with their joint; Simple backend has no joints.
