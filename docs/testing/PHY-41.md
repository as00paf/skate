# PHY-41 — Showcase slider joint: manual test

Needs a **new project** (it ships the `FeatureShowcase` scene).

1. Create a new project and open `FeatureShowcase`.
2. In the Hierarchy, find `[Joints] SliderBlock` (y = 3.5, axis +Y, limits -2 / 0).
3. Press **Play**. The block drops 2 m (to y ≈ 1.5) and stops there.
4. Stop, then change the limits in the Joint inspector (for example -2 / 1). On Play the block still drops to -2 and cannot rise above 1 m over its start.
