# PHY-09 – Raycast vehicle

`VehicleComponent` (+ `WheelConfig`) on a `RigidBody3D` chassis; `RaycastVehicleSolver` runs from `PhysicsSystem.fixedUpdate`
before each step. Only `raycastClosest` and `applyImpulse` are used, so it works on any `IPhysics3D` backend.
Per wheel: spring/damper along chassis up, engine and brake force along the steered wheel direction, lateral grip that
cancels sideways slip, all clamped by `tyreFriction * suspensionLoad`. Tyre forces apply at hub height to limit body roll.
Game code drives it by setting `throttle`, `steering`, `brake`. Chassis axes: +Y up, +Z forward, +X right.
Limitations: no anti-roll bars, gearbox or per-wheel visuals (read `wheelStates` for those).
