# INP-10 manual test steps: game-agnostic input

Delivers migration steps 1–7 of `docs/design/INP-10.md`. The step list is in the note's *Migration steps* section.

| Change | Detail |
|---|---|
| Context stack | `InputContext` is an open type, and `InputSystem.contexts` is a prioritized stack. |
| `InputHistory` | Replaces the old flick buffer. |
| Action split | The engine registers only UI navigation. Walking, jumping and similar actions are the `CharacterActions` preset in `engine/gameplay`, read by `CharacterInputSampler`. |
| Skate layer (step 5) | `game/input`: the `skate` context with `skate.push`, `skate.manual` and `skate.ollie`. The context sits above gameplay while riding, so the same keys push, manual and ollie on the board and sprint, crouch and jump on foot. `FlickRecognizer` is ready for GPL-21 and not used in play yet. |
| Guard | `InputGameAgnosticGuardTest`. |
| Step 7 | Scripts get `isActionDown`, `wasActionPressed`, `pushContext` and `popContext`; a script's contexts are popped when it is destroyed, reloaded or fails. The pause menu holds a blocking `ui` context. Key Bindings shows a **(!)** hint on inputs shared with another context, and Input Testing lists the context stack. |

The skate demo should play **exactly as before**. Run the steps on Windows from the repo root.

1. Run `.\gradlew.bat test --tests "com.pafoid.skate.engine.input.*" --tests "com.pafoid.skate.engine.gameplay.*" --tests "com.pafoid.skate.game.input.*" --tests "com.pafoid.skate.engine.ecs.systems.InputSystemTest" --tests "com.pafoid.skate.architecture.*"`, then `.\gradlew.bat test`.
   Expected: `BUILD SUCCESSFUL` both times. The first run includes `InputContextStackTest`, `InputHistoryTest`, `SkateInputContextTest` (it replays a ride and checks it plays the same states as before INP-10), `FlickRecognizerTest`, `ScriptInputContextsTest`, `PauseMenuSystemTest`, `InputContextRowsTest` and `InputGameAgnosticGuardTest`.
2. Run `.\gradlew.bat run`, open a project with the MainScene (skater and skateboard), and press **Play**. Click the game view, then walk with **WASD**, sprint with **Left Shift** and jump with **Space**.
   Expected: the skater walks, sprints and jumps as on master. The camera follows. Stop, then hold **E** and **Space** while pressing Play: the skater neither mounts nor jumps until you release and press again.
3. Walk next to the board and press **E**, then ride: **W** or **Left Shift** pushes, **Left Ctrl** manuals, **Space** ollies. Press **E** again, then sprint and jump on foot.
   Expected: E mounts the board and the ride controls behave as before. The second E dismounts, and Shift and Space sprint and jump again at once. Each E press toggles only once, even when held.
4. Hold **Space**, walk to the board and press **E** while still holding Space. Release Space, then press it.
   Expected: stepping on with Space held does not ollie. The next Space press ollies.
5. With a gamepad connected, repeat steps 2–3.
   - Use the left stick to move, the right stick to look, **A** to jump or ollie, **LT** to sprint or push, **LB** for manual and **Y** to mount.
   - Unplug the pad mid-play, then plug it back in.

   Expected:
   - The controls are the same as on master.
   - Unplugging stops all pad input, with no stuck jump or movement.
   - After you plug the pad back in, it works again.
6. Ride, then press **Esc**. Open **Input Testing → Input Contexts**, then navigate the pause menu with the arrows or d-pad. Resume while holding **Space**. Press Esc again, choose **Restart**, then stop Play while riding and press Play again.
   Expected:
   - While the menu is open, the list starts with `UI · priority 1000000 · blocks all below · pause-menu` and every row under it is greyed. After resuming, that row is gone.
   - Space held through Resume does not ollie.
   - After Restart and after Stop/Play the skater starts on foot, Shift sprints (no riding controls stay active), and Input Testing lists only UI and Gameplay.
7. Open the **Key Bindings** window.
   Expected: Gameplay (Movement / Camera / Actions), **Skateboard** (Push, Manual, Ollie) and UI (Navigation). A **(!)** follows Space on Jump; hovering it names Ollie (Skateboard). Rebind **Ollie** to **Q** and press Play: on the board Q ollies, and on foot Space still jumps. Reset the binding; the change persists after you save and reopen the project.
8. Run `.\gradlew.bat gameJar`, export the game, run it, and repeat steps 2, 3 and 6.
   Expected: the controls behave the same in the exported game.

## Known limits

- Bindings you made for **Sprint**, **Crouch** or **Jump** before step 5 apply on foot only. The ride actions start on their defaults; rebind them under Skateboard.
- Script contexts have no editor UI; `ScriptInputContextsTest` covers them (push, pop, release on stop, reload, removal and failure, Lua binding). To try one by hand, a script calls `ctx.input.pushContext("menu", 100, "all")` and the context shows in Input Testing.
- A press made on the exact frame that the context stack or the bindings change is treated as already held: release the key and press it again. So a Space press on the very frame you step on or off the board is not taken as an ollie or jump.
