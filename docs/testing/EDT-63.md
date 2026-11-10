# EDT-63: Switching scenes during Play stops Play

Use a project with at least two scenes open as tabs (for example **Main** and **FeatureShowcase**), each with
something that moves or changes while playing.

1. **Tab click:** press **Play** on Main and let objects move. Click the FeatureShowcase tab. Play stops (the toolbar
   is back in Edit mode), then FeatureShowcase opens in Edit mode. Switch back to Main: every object is where it was
   before Play.
2. **While paused:** press **Play**, then **Pause**, then click another tab. Same result: Edit mode, scenes restored,
   the clicked scene active.
3. **Project window:** press **Play**, then double-click a `.scene` file in the **Project** window. Play stops and the
   scenes are restored first, then the scene opens.
4. **New Scene:** press **Play**, then **File > New Scene** (or the **+** tab). Play stops first, then the new scene
   opens in Edit mode.
5. **Script switch keeps playing:** in a scene with a script that calls `Scene.load("<other open scene>")` (for
   example on a key press), press **Play** and trigger it. The game switches scenes and **keeps playing**; the tab
   bar follows to the new scene without stopping Play. **Stop** restores both scenes.
6. **No regressions:** in Edit mode, tab clicks, Project double-clicks and New Scene still switch at once. Play,
   Pause, Stop and Reset behave as before (EDT-45).
