# PHY-09 – Raycast vehicle (manual checks)

Start from a NEW project: `.\gradlew.bat run`, `File > New Project`, name it and create it.

1. In the default scene add a Cube, scale it to about (40, 1, 40), position it at y=-0.5; in Properties add `RigidBody3D` (type Static) and `BoxCollider3D`.
2. Add another Cube at y=3, add `RigidBody3D` (mass 1000), `BoxCollider3D` (half extents 1, 0.3, 1.8) and `VehicleComponent` (Add Component > Vehicle).
3. Press Play Simulation. Expected: the car drops and settles on its springs at about y=0.93 (wheels hanging 0.3 below the body), without bouncing for more than a second or two.
4. With debug draw on (Physics debug toggle) nudge the car via a script/game code `vehicle.throttle = 1f`. Expected: it accelerates along its local +Z; `steering = 1f` turns it; `brake = 1f` stops it (input mapping is game code, not part of PHY-09).
5. Stop; save; reopen the project. Expected: the VehicleComponent and its 4 wheels are restored.

Automated: `.\gradlew.bat :test --tests "*RaycastVehicleTest"` (settle, accelerate, steer, brake).
