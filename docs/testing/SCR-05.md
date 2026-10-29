# SCR-05 test steps: Lua host (+ engine input wired into scripts)

Run on Windows from the repo root (`C:\workspace\kotlin\skate-worktrees\scripting`, branch `agents/scripting`).
Natives are Windows-only. From WSL prefix every Gradle command with `cmd.exe /c`.

## A. Automated verification (definitive; works from a clean checkout)

1. Open PowerShell in the repo root. Confirm the commit: `git log --oneline -3`.
   Expected: a commit starting with `SCR-05:` is at the top.
2. Run the Lua host tests: `.\gradlew.bat :test --tests "com.pafoid.skate.engine.scripting.LuaScriptHostTest"`.
   Expected: `BUILD SUCCESSFUL`, 5 tests PASSED:
   - `LuaHost_RunsLifecycleAgainstContext`: onCreate/onUpdate run; position.x == 2, y == 1.5; log "hello hero".
   - `LuaHost_SyntaxErrorHasFileAndLine`: syntax error reported as `scripts/bad.lua` line 2.
   - `LuaHost_RuntimeErrorReportedWithLine`: `error('kaboom')` reported with line 2 and message containing `kaboom`.
   - `LuaHost_SandboxHasNoHostOrIoAccess`: `io`, `os`, `luajava`, `require`, `debug` are all nil.
   - `LuaHost_HotReloadSwapsScript`: editing the source swaps behaviour (x goes 1 -> 2).
3. Run the input wiring test: `.\gradlew.bat :test --tests "com.pafoid.skate.engine.scripting.ReaderScriptInputTest"`.
   Expected: `BUILD SUCCESSFUL`, `ScriptInput_DelegatesToReaderAndDetectsRelease` PASSED.
4. Regression: `.\gradlew.bat :test --tests "com.pafoid.skate.engine.scripting.*"`.
   Expected: `BUILD SUCCESSFUL`, no FAILED lines (Kotlin, TypeScript, Lua, hot reload, runtime tests).
5. Full suite: `.\gradlew.bat :test`. Expected: `BUILD SUCCESSFUL`.
6. Optional negative check: in `LuaScriptHostTest.kt` change the `kaboom` test's expected line `2` to `3`,
   re-run step 2. Expected: that test FAILS. Revert the change afterwards.

## B. Manual check in the editor (clean state)

Prerequisite: create `spin.lua` in the repo root (`C:\workspace\kotlin\skate-worktrees\scripting\spin.lua`):

```lua
function onCreate(ctx)
  ctx.log.info("lua hello from " .. ctx.entity.name)
end
function onUpdate(ctx, dt)
  ctx.entity.transform.rotation.y = ctx.entity.transform.rotation.y + 90 * dt
  if ctx.input.isKeyDown(32) then ctx.log.info("space held") end
end
```

Script paths resolve against the process working directory, which is the repo root when launched with `gradlew run`.

1. Launch the editor: `.\gradlew.bat run` (main class `com.pafoid.skate.MainKt`).
   Expected: the editor window opens.
2. Create a new project: menu bar **File > New Project** (item with the "+" icon). Confirm the dialog.
   Expected: an empty project/scene opens.
3. In the Hierarchy window create an object (right-click, add an empty/cube object) and select it.
   Expected: it is highlighted and the Properties window lists its components.
4. In the Properties window click the **+** button next to the search box and choose **SCRIPT**.
   Expected: a Script component is added to the object.
5. Set the script path to `spin.lua`.
   Known gap (reported as NEEDS EDT): the Properties window may have no editor for `ScriptComponent.path`.
   If no path field is shown: save the scene (**File > Save Scene**, Ctrl+S), close the editor, open the saved scene
   JSON, set `"path": "spin.lua"` on the `ScriptComponent` entry, then relaunch (step 1) and reopen the scene.
6. Open the Console window, **Engine** tab. Press **Play** (toolbar Play button, or the search action "Play").
   Expected: the Engine tab shows `lua hello from <object name>`; the object rotates about Y (90 deg/s).
7. Hold **Space** while playing. Expected: `space held` is logged repeatedly (engine input reaches scripts).
8. Hot reload: while playing, change `90` to `-90` in `spin.lua` and save.
   Expected: within about 0.5 s the rotation reverses, without restarting Play.
9. Error reporting: change the rotation line (line 5) to `ctx.entity.transform.rotation.y = nil + 1` and save.
   Expected: a Console error containing `spin.lua` and line `5`; the editor does not crash.
   Restore the line. Expected: the script recovers and keeps running.
10. Press **Stop**. Expected: rotation stops; no errors.

## C. Sandbox sanity (manual)

1. Temporarily make `onCreate` in `spin.lua` call `os.execute("calc")`, then press Play.
   Expected: Calculator does NOT open; the Console shows an error at line 2 (attempt to index nil `os`).
