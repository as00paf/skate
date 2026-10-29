# TLG-06 manual test: script API for audio, scene switching, UI text and material colours

Run everything from the repository root (Windows; use `.\gradlew.bat`, or `cmd.exe /c gradlew.bat` from WSL).
Branch: `agents/tooling`.

## A. Automated checks

1. `.\gradlew.bat test :tools:cli:test :tools:mcp:test --console=plain`
   Expected: `BUILD SUCCESSFUL`, no `FAILED` lines.
2. `.\gradlew.bat :tools:cli:test --tests "com.pafoid.skate.cli.TetrisProjectBuilderTest" --console=plain`
   Expected: 6 tests pass (pool/script generated, spawn and fall, hard drop, game over + restart,
   game over requests the `GameOver` scene and plays `SfxOver`, locking updates HUD args, `SfxLock` and cell colours).

## B. Generate the Tetris sample from a clean state

3. Pick an empty folder, e.g. `C:\temp\skate-tests` (delete any existing `C:\temp\skate-tests\Tetris`).
4. Run `.\gradlew.bat :tools:cli:run --args="sample tetris --dir C:\temp\skate-tests" --console=plain`
   Expected: a message that the sample was created; no exception.
5. Check the files exist in `C:\temp\skate-tests\Tetris`:
   - `Tetris.skateproject`
   - `Scenes\Game.scene` and `Scenes\GameOver.scene`
   - `Assets\Scripts\tetris.kts` and `Assets\Scripts\gameover.kts`
   - `Assets\strings.properties` (contains `tetris.hud=Score %s   Lines %s   Level %s`)
   - `Assets\SfxLock.wav`, `Assets\SfxClear.wav`, `Assets\SfxOver.wav`
6. `.\gradlew.bat :tools:cli:run --args="validate C:\temp\skate-tests\Tetris" --console=plain`
   Expected: validation succeeds, no errors.
7. `.\gradlew.bat :tools:cli:run --args="run C:\temp\skate-tests\Tetris --scene Game --frames 600" --console=plain`
   Expected: stats line for scene `Game` (about 215 objects); no script error. (Headless run has no input or
   scene switching; it only proves the script loads.)

## C. Play it in the editor

8. Launch the editor: `.\gradlew.bat run`. Expected: the editor window opens.
9. Menu **File > Open Project** (or **Recent Projects**), choose `C:\temp\skate-tests\Tetris\Tetris.skateproject`.
   Expected: the project loads; the scene list shows `Game` and `GameOver`.
10. Open scene `Game` (double-click it in the scene tab/asset browser; or File > Open Scene). Expected: a 10-wide
    frame with walls and floor, plus the `Hud` canvas with `ScoreText` in the hierarchy.
11. Click **Play Simulation** (viewport toolbar). Expected: a coloured piece spawns at the top and falls; the HUD
    reads `Score 0   Lines 0   Level 0`.
12. Press **Left/Right**: piece moves. **Up**: rotates. **Down**: falls faster. Expected: no errors in the console.
13. Press **Space** (hard drop). Expected: piece lands instantly, a short blip (330 Hz) plays, a new piece with a
    possibly different colour spawns; locked cells keep their colour.
14. Fill a row (use hard drops and moves). Expected: the row disappears, a higher blip (660 Hz) plays and the HUD
    score becomes `100` (x level+1) with Lines `1`.
15. Keep hard-dropping without clearing until the stack reaches the top. Expected: a low sound (110 Hz) plays and
    the scene switches to `GameOver`, showing "GAME OVER" and "Press R to play again".
    NOTE: switching only works to scenes that are open in the editor. If the view does not change, open the
    `GameOver` scene tab as well, press Stop, then Play again from `Game` (report this if it is needed).
16. On the game-over screen press **R**. Expected: back to `Game`, the board is empty and the score is 0.
17. In `Game`, press **R** mid-game. Expected: board and score reset.
18. Press **Stop Simulation**. Expected: the script stops without errors in the console.

## D. Script API via a custom script (optional)

19. In the editor create a cube with a `Script` component pointing to a new `.kts`:
    ```kotlin
    onUpdate { ctx, _ ->
        ctx.entity.getComponent(ScriptMaterial::class)?.color = org.joml.Vector4f(1f, 0f, 0f, 1f)
        if (ctx.input.isKeyPressed(80)) ctx.entity.getComponent(ScriptAudioSource::class)?.play() // P
        if (ctx.input.isKeyPressed(78)) ctx.scene.load("Other")                                   // N
    }
    ```
    Give the cube a Render component (with a material) and an Audio component (non-3D). Play.
    Expected: the cube turns red; P plays the sound; N switches to the open scene `Other` (returns false and does
    nothing if no such scene is open).
