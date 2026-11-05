# REN-12 test steps: RHI design note (no runtime change yet)

REN-12 is a rewrite-scale task (GameEngine.md rule 3). This change only adds `docs/design/REN-12.md`; the engine
behaves exactly as before. Steps for this stage:

1. `.\gradlew.bat run`, create a NEW project → the editor opens as before, and MainScene and FeatureShowcase render
   unchanged (nothing in the engine changed).
2. Read `docs/design/REN-12.md`. Approve or adjust §7: the scope, the ImGui exception, the uniform model and the
   Vulkan semantics. Implementation starts with migration step 0 (the ratchet guard) after approval.

Each later migration step will add its own steps here: open MainScene and FeatureShowcase, press Play, and confirm
frames are unchanged and the Render Graph timings are within budget.
