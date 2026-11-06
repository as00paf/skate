# INP-10 manual test steps: game-agnostic input

Delivers migration steps 1–4 and 6 of `docs/design/INP-10.md`. The step list is in the note's *Migration steps* section.

| Change | Detail |
|---|---|
| Context stack | `InputContext` is an open type, and `InputSystem.contexts` is a prioritized stack. |
| `InputHistory` | Replaces the old flick buffer. |
| Action split | The engine registers only UI navigation. Walking, jumping and similar actions are the `CharacterActions` preset in `engine/gameplay`, read by `CharacterInputSampler`. |
| Guard | `InputGameAgnosticGuardTest`. |

The skate demo should play **exactly as before**. Run the steps on Windows from the repo root, on branch `agents/input`.

1. Run `.\gradlew.bat test --tests "com.pafoid.skate.engine.input.*" --tests "com.pafoid.skate.engine.gameplay.CharacterInputSamplerTest" --tests "com.pafoid.skate.engine.ecs.systems.InputSystemTest" --tests "com.pafoid.skate.architecture.*"`.
   Expected: `BUILD SUCCESSFUL`. The run includes `InputContextStackTest`, `InputHistoryTest` and `InputGameAgnosticGuardTest`.
2. Run `.\gradlew.bat test`.
   Expected: `BUILD SUCCESSFUL` with no failed tests.
3. Run `.\gradlew.bat run`, open a project with the MainScene (skater and skateboard), and press **Play**. Click the game view, then walk with **WASD**, sprint with **Left Shift** and jump with **Space**.
   Expected: the skater walks, sprints and jumps as on master. The camera follows.
4. Walk next to the board and press **E**, then ride: **W** or **Left Shift** pushes, **Left Ctrl** manuals, **Space** ollies. Press **E** again.
   Expected: E mounts the board and the ride controls behave as before. The second E dismounts. Each E press toggles only once, even when held.
5. With a gamepad connected, repeat steps 3–4.
   - Use the left stick to move, the right stick to look, **A** to jump or ollie, **RT** to sprint or push, **LB** for manual and **Y** to mount.
   - Unplug the pad mid-play, then plug it back in.

   Expected:
   - The controls are the same as on master.
   - Unplugging stops all pad input, with no stuck jump or movement.
   - After you plug the pad back in, it works again.
6. Press **Esc** during Play, navigate the pause menu with the arrows or d-pad, and resume. While the menu is open, hold **W**.
   Expected: the menu opens, can be navigated and closes as before. The skater does not move while the game is paused.
7. Open the **Key Bindings** window. Rebind **Jump** to **Q**, press Play, and test it. Then reset the binding.
   Expected:
   - The actions are grouped under Gameplay (Movement / Camera / Actions) and UI (Navigation), as before.
   - Q jumps after the rebind, Space jumps again after the reset, and the binding persists after you save and reopen the project.
8. Run `.\gradlew.bat gameJar`, export the game, run it, and repeat steps 3, 4 and 6.
   Expected: the controls behave the same in the exported game.

## Known limits

- `skate`-specific actions, the context pushed while riding and the flick recognizer are migration step 5, owned by `skate-gameplay`. Riding still reads the generic sprint, crouch and jump actions.
- The pause menu does not push a blocking `ui` context yet (step 7, GUI). Gameplay is still gated by the paused scene.
- A press made on the exact frame that the context stack or the bindings change is treated as already held: release the key and press it again.
