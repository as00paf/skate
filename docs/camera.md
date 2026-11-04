# Cameras

| Component | Use |
|-----------|-----|
| `FollowCameraComponent` (GPL-05) | Third-person game camera: follow, orbit, spring-arm collision, shake. Driven by `FollowCameraSystem` while the scene runs. |
| `FreeFlyCameraComponent` (TLG-09) | Fly the camera with WASD/E/Q and right-drag look while the scene runs. |
| `CameraFocusComponent` (TLG-09) | Fly the camera to named viewpoints, optionally from runtime UI buttons. |

## Follow camera

Add **FOLLOW_CAMERA** to a camera object (Properties > Add Component). It follows the top-level object named by
`targetName` (default `Skater`, which is what the default project's `Camera` follows). The inspector does not edit
text fields yet, so set another target in code or in the scene file. Every value below is a field of the component, so it is
tuned in Properties and saved with the scene. In edit mode the camera stays where it was placed; each Play snaps it
behind the target.

| Group | Fields | Behaviour |
|-------|--------|-----------|
| Follow | `distance`, `pivotHeight`, `followSharpness` | Looks at a pivot `pivotHeight` above the target's origin from `distance` behind it; the pivot trails the target (higher sharpness = tighter). |
| Player presets | `usePlayerPresets`, `presetSharpness` | When the target's behaviour is `Mountable`, its `cameraPreset` (walk 4 m / 1.4 m, ride 6 m / 1.0 m from `SkateControllerConfig.mount`) replaces `distance` and `pivotHeight`, blended smoothly. |
| Orbit | `pitch`, `minPitch`, `maxPitch`, `orbitSensitivity` | The target's look input (`InputStateComponent.cameraLook`: right stick, or the mouse when captured) turns the camera around the pivot. Pitch is in degrees, + looks down. |
| Auto align | `autoAlign`, `autoAlignDelay`, `autoAlignSharpness`, `autoAlignMinSpeed` | After `autoAlignDelay` seconds without look input, while the target moves faster than `autoAlignMinSpeed`, the camera swings back behind it and returns to `pitch`. |
| Spring arm | `collision`, `probeRadius`, `minDistance`, `returnSharpness` | A sphere of `probeRadius` is swept from the pivot to the camera (`PhysicsSystem.sphereCast`, ignoring the target's own body and triggers). The arm shortens at once to stop in front of the hit, never below `minDistance`, and eases back out at `returnSharpness` when clear. |
| Shake | `shakeDecay`, `shakeMaxOffset`, `shakeMaxRoll`, `shakeFrequency`, `impactShakeImpulse`, `impactShake` | `shake(amount)` adds trauma (0..1), which decays at `shakeDecay` per second; offset and roll scale with trauma². A collision on the target with an impulse of at least `impactShakeImpulse` adds `impactShake`. |

The maths is in `engine/gameplay/camera` (`FollowCameraRig`, `SpringArm`, `CameraShake`) and runs without GL or
physics, behind a `SphereProbe`; see `FollowCameraRigTest`, `FollowCameraSystemTest` and `FollowCameraFromDiskTest`.
