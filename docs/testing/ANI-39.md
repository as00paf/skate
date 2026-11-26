# ANI-39 test steps (BUG-021: exported game keeps clips and controller)

`Animator.resolveAnimationsFromPaths` resolves clips through `AssetsManager.paths` (pack or disk), so exported games keep them.

1. From a new project (not an old one), add the Skater to a scene and Export > Windows (or Pack only).
2. Run the exported game.
3. Check: the skater animates (idle/walk) and the log has no `uses unknown motion` errors.
4. Automated: `cmd.exe /c gradlew.bat :test --tests "*AnimatorClipResolutionTest" --tests "*ProjectLifecycleEndToEndTest"` passes (`Lifecycle_8`, pack and disk clip cases).
