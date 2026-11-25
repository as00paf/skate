---
name: skate-project
description: Work on {{projectName}}, a Skate engine game project (scenes, assets, scripts, the skate CLI). Use when editing its scripts, scenes, assets or project settings, or when validating or running it headless.
---

# Skate project: {{projectName}}

Read `AGENTS.md` at the project root first: it describes the project layout, the asset reference rules, the script
API and the headless `skate` commands.

Checklist for every change:

1. Make sure the Skate editor does not have the project open before editing `.scene`, `.prefab` or `.skateproject`
   files by hand.
2. Keep asset references project-relative (`Assets/...`) or engine references (`engine:...`); never absolute paths.
3. Move or delete an asset together with its `.meta` file.
4. Run `skate validate .` (and `skate run . --frames 120` for gameplay changes) and fix what it reports.
