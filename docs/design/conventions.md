# Units and coordinate-system conventions (ECS-09)

| Topic | Convention |
|---|---|
| Up axis | **Y-up** |
| Handedness | **Right-handed** (forward = -Z, right = +X) |
| Length | **Metres** |
| Mass | Kilograms |
| Time | Seconds (`deltaTime`); fixed step in seconds |
| Angles in components / serialized data / editor | **Degrees** (e.g. `Transform.rotation`, `CameraComponent.fov`, light cut-off angles) |
| Angles internally (JOML, math, physics backends) | **Radians**; convert at the boundary with `toRadiansF()` / `toDegreesF()` |
| Euler order | `Transform.rotation` is Euler XYZ degrees; pitch = X, yaw = Y, roll = Z |
| Velocities | Metres per second; angular velocity in radians per second internally |
| Colours | Linear RGB(A), 0..1 floats |

Rules:
- Public component fields carry units in KDoc, or a unit suffix when ambiguous (`...Deg`, `...Rad`, `...Mps`, `...Metres`).
- Never mix degrees and radians in one field; convert on entry to math code.
- Asset importers convert to metres/Y-up/right-handed on load.
