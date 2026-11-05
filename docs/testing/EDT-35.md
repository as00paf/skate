# EDT-35: Fly-cam look and UI pointer without ImGui workarounds, test steps

Start from a NEW project (`.\gradlew.bat run`, then File > New Project, then Create Project).

1. Open the `FeatureShowcase` scene, save it (Ctrl+S), close the project and reopen it from disk. Open `FeatureShowcase` and press Play.
2. Hover and click the legend buttons on the left: they highlight under the cursor and the camera glides to each area. The widget-panel button reacts to hover and press as before.
3. Hold the right button inside the game view and drag: the camera looks around. Keep holding and drag out over the Hierarchy or Inspector: the camera keeps turning until you release.
4. Start a right-drag on the Inspector and move into the game view: the camera does not turn. A left click on another panel does not press any in-game button.
5. Drag the widget-panel slider and move the cursor out of the game view while holding: the slider keeps following until release.
6. Standalone: with `FeatureShowcase` as the start scene, export the project and double-click `run-game.bat`. The legend clicks and the right-drag look work as in steps 2 and 3.
