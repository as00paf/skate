# Sample projects

## Feature showcase (`FeatureShowcase`)

A manual regression suite: one scene per engine feature, built only from public engine components.

Generate it with the CLI (headless), then open `FeatureShowcase.skateproject` in the editor:

```
skate sample --dir <folder>        # creates <folder>/FeatureShowcase
skate validate <folder>/FeatureShowcase
skate run <folder>/FeatureShowcase --scene Physics --frames 120
```

The generator is `ShowcaseProjectBuilder` in `tools/cli`; edit it to change a scene, then regenerate.

| Scene | What to look for |
|-------|------------------|
| `Lights` | Dim ambient plus a weak sun. Red and blue point lights tint the sphere and floor from opposite sides; a spot light makes a clean circular pool with a soft edge. No light should flicker or clip as the camera moves. |
| `Shadows` | Sun-lit cube, tall box and sphere cast shadows on the floor; the small cube (cast shadow disabled) casts none. Shadows should be stable (no shimmering or acne) while the camera moves. |
| `Materials` | Five gold spheres sweeping from rough/dielectric to smooth/metallic; an emissive orange cube that glows; an asphalt-textured cube; a translucent blue cube (alpha blend) through which the floor stays visible. |
| `Physics` | Debug draw is on. A stack of five boxes settles on the static floor without jitter or tunnelling, and the capsule ball drops and rolls. Press Play in the editor. |
| `Audio` | A looping 440 Hz tone (`Assets/tone.wav`, generated). `Emitter` is 3D: volume and panning change with camera position. `Ambient2D` is a quiet non-positional layer. Stopping play silences both. |
| `Animation` | The bundled character with an `Animator` that is playing. Check that the model renders in bind pose without distortion; assign clips from `EngineDefaults/Characters/animations` to verify playback and blending. |

## Not covered yet

The engine has no particle system, post-processing stack or runtime (non-ImGui) UI, so the task's *post-fx*, *particles* and *UI* scenes cannot be built yet. Add a scene for each to `ShowcaseProjectBuilder` and a row above when REN particles/post-fx and the GUI tasks land.
