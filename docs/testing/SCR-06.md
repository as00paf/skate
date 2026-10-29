# SCR-06 test steps: script code-completion service

There is no editor popup yet (that is EDT-26), so verification is by unit tests and a command-line probe.
Run from PowerShell in `C:\workspace\kotlin\skate-worktrees\scripting` (branch `agents/scripting`). From WSL prefix
Gradle commands with `cmd.exe /c`.

## A. Automated

1. `git log --oneline -3` - expected: a commit starting with `SCR-06:`.
2. `.\gradlew.bat :test --tests "com.pafoid.skate.engine.scripting.completion.*"` - expected: `BUILD SUCCESSFUL`, 11 tests
   PASSED (members, chained members and signatures, partial identifier, camel humps/aliases, top level, snippet caret,
   error-tolerant text, strings/comments, TypeScript and Lua syntax, host registry and service, cancellation and custom provider).
3. `.\gradlew.bat :test` - expected: `BUILD SUCCESSFUL` (includes the architecture guard tests).

## B. Manual probe from the command line

The probe prints completions for a file at a 1-based `line:col` caret (col 1 = start of line).
Arguments have no spaces: `--args=<file>@<line>:<col>`.

1. Create `probe.lua` in the repo root with exactly these 3 lines (line 2 ends right after `transform.`):

   ```
   function onUpdate(ctx, dt)
     ctx.entity.transform.
   end
   ```

   The caret is at column 25 of line 2.
2. Run `.\gradlew.bat completeScript --args=probe.lua@2:25 -q`.
   Expected output: three lines `PROPERTY position ...`, `PROPERTY rotation ...`, `PROPERTY scale ...`, each with its doc text.
3. Partial identifier: change line 2 to `  ctx.entity.transform.po` and run with `--args=probe.lua@2:27`.
   Expected: only `position`.
4. Top level: change line 2 to `  ` + `c` (i.e. `  c`) and run with `--args=probe.lua@2:4`.
   Expected: `VARIABLE ctx ...` first, then other matches such as keywords/snippets containing `c`.
5. Kotlin: create `probe.kts` with `onUpdate { ctx, dt -> ctx.entity.transform.position. }`, caret after the last `.`
   (line 1, col 53): `--args=probe.kts@1:53`. Expected: `x`, `y`, `z`, then `add`, `dot`, `length`, `mul`, `normalize`, `sub`,
   with Kotlin-style signatures (`var x: Float`, `fun add(v: Vector3f): Vector3f`).
6. TypeScript: `probe.ts` containing `export function onCreate(ctx: any) { ctx.log. }` caret after `log.` (line 1, col 46):
   `--args=probe.ts@1:46`. Expected: `error`, `info`, `warn` with signatures like `info(message: string): void`.
7. Strings/comments: `probe.lua` with `-- ctx.` and caret at the end (`--args=probe.lua@1:8`). Expected: `(no completions)`.
8. Error tolerance: `probe.kts` with `val = = ((` on line 1 and `ctx.entity.` on line 2, caret at line 2 col 12.
   Expected: `name`, `id`, `transform`, ... listed; no crash.
9. Delete the `probe.*` files.
