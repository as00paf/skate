# AST-50 — Bundled metal.png: how to test

BUG-020 is fixed: `assets/Textures/metal.png` is in the repo and `EngineAssetCopier` fails on any missing catalog source.

1. Create a **new project** (a pre-existing one keeps its old files).
2. Open `MainScene`: the console shows no `metal.png` ERROR and the rails are metal-textured.
3. Open `FeatureShowcase`: same check.
4. Run `cmd.exe /c gradlew.bat test --tests "com.pafoid.skate.editor.project.EngineAssetCopierTest" --tests "com.pafoid.skate.editor.systems.ProjectLifecycleEndToEndTest"`.
