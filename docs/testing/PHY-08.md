# PHY-08 – Manual test steps (ragdoll registration, Bullet joint frames)

Run everything from the repo root in a Windows shell (natives are Windows-only). From WSL prefix with `cmd.exe /c`.
Start from a clean state: close any running editor and delete `C:\temp\phy08` if it exists.

## A. Automated checks (2 min)

1. Run `.\gradlew.bat :test --tests "*JointConstraintsTest" --tests "*PhysicsSystemRagdollTest" --tests "*SimplePhysics3DTest"`.
   Expected: `BUILD SUCCESSFUL`; 4 + 3 + 6 tests pass (fixed joint holds, removed joint releases, hinge keeps radius, slider stays on axis, ragdoll bones stay kinematic when animated, fall in RAGDOLL state, leave the world with the component).
2. Run `.\gradlew.bat :test`. Expected: `BUILD SUCCESSFUL` (no failures).

## B. Slider/hinge joints in a sample project (the visible effect of the matrix-transpose fix)

3. Generate the sample: `.\gradlew.bat :tools:cli:run --args="sample marble-run --dir C:/temp/phy08"` (forward slashes; from WSL use `cmd.exe /c gradlew.bat :tools:cli:run '--args=sample marble-run --dir C:/temp/phy08'`).
   Expected: a `C:\temp\phy08\MarbleRun` folder containing `MarbleRun.skateproject`.
4. Validate it: `.\gradlew.bat :tools:cli:run --args="validate C:/temp/phy08/MarbleRun"`.
   Expected: `Project is valid (1 scenes)`. (`skate run` needs an OpenGL context and aborts headless; use the editor below.)
5. Launch the editor: `.\gradlew.bat run`. Expected: the Skate editor window opens.
6. Menu `File > Open Project`, choose `C:\temp\phy08\MarbleRun\MarbleRun.skateproject`.
   Expected: the project loads and the `Run` scene (ramps, bin, red `Pendulum`, blue `Pusher`, marbles) appears in the viewport. If a different scene shows, open `Run` from the Scene/Project panel.
7. Click the Play Simulation button in the viewport toolbar (tooltip "Play Simulation").
   Expected: marbles drop onto the ramps and roll down; none tunnel through ramps or ground.
8. Watch `Pusher` (blue block on the lowest ramp). Expected: it is shoved only along the world X axis, within about ±2 units of its start (-4, 1.2, 0), and never rotates or falls. Before the fix a slider on a non-default frame could drift off-axis.
9. Watch `Pendulum` (red bar). Expected: it swings when hit and stays within about ±57° of vertical, pivoting about its anchor, without detaching.
10. Let it run ~15 s. Expected: marbles end in the bin (between `BinLeft` and `BinRight`) or on the ground; none fall through the world.
11. Click Stop Simulation (tooltip "Stop Simulation"). Expected: objects return to their authored positions.

## C. Slider on a non-X axis (regression the fix targets)

12. With the project open and simulation stopped, select `Pusher` in the Hierarchy; in Properties find the `JointComponent` and set `Axis` to `0, 1, 0` (Y) and limits `limitMin -1`, `limitMax 0`. Press Play.
    Expected: the pusher moves only vertically (it sags down to at most ~1 unit below its start and stays there); X and Z stay constant. Before the fix it slid sideways and barely fell.
13. Stop, then undo the edit with Ctrl+Z (or reopen without saving). Expected: back to the original axis.

## D. Ragdolls

Ragdoll bones are created in code (`RagdollBuilder.buildMixamoRagdoll`) and there is no editor UI for them yet, so the in-world behaviour
is verified by test steps 1-2 (`PhysicsSystemRagdollTest`). Steps to see it manually once a game/sample builds a ragdoll:

14. Add a `RagdollComponent` built with `RagdollBuilder.buildMixamoRagdoll(physics, skeleton)` to a skinned character and press Play.
    Expected: the character stays animated; its bone bodies are kinematic and follow the animation.
15. Set `ragdoll.state = RagdollState.RAGDOLL` at runtime. Expected: the bone bodies become dynamic, fall under gravity, are held together by the cone-twist joints and the skin follows them.
16. Delete the character GameObject. Expected: bones and joints leave the physics world (no ghost collisions, no exceptions in the log).
