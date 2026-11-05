# GUI-09 test steps: pause menu in editor Play and in the game

The pause menu (Resume, Restart, Language, Quit, Volume) is engine runtime UI, shared by editor Play and the
exported game. Esc (or gamepad B) opens it while playing, and Esc or Resume closes it. While it is open, simulation,
animation, particles, scripts and audio are frozen, and Resume continues from the same point. Restart starts Play
again from the pre-play state (in the editor) or the launch state (in the game). Quit is Stop in the editor and
closes the window in the game. Language switches the runtime text between English and French (in the editor this
also switches the editor's own strings until you change it back). While editing, Esc still only deselects.

Setup: run `.\gradlew.bat run`, create a NEW project (File > New Project), and open the Feature Showcase scene.

1. While editing, select an object and press Esc -> it is deselected. No pause menu appears.
2. Press Play and click the viewport. The hint at the top mentions Esc. Press Esc -> a dimmed screen with the
   "Paused" panel. The character animation, particles, swinging pendulum and music all stop.
3. Press Esc again (or click Resume) -> everything continues from where it stopped. The music does not restart.
4. Esc, then click Restart -> the scene jumps back to its pre-play state and keeps playing (the boxes fall again).
5. Esc, click Language -> the menu text switches to French. Click it again to return to English. Drag Volume to
   change the music level.
6. Esc, click Quit -> Play stops, as with the toolbar's Stop. The pause menu is gone and does not appear in the
   Hierarchy.
7. Game: export the project (the editor's Export action), then run the exported game. Press Esc -> the same menu with the same behaviour. Restart
   reloads the level and resets the HUD timer. Quit closes the window.
8. Run `.\gradlew.bat test` -> BUILD SUCCESSFUL (`PauseMenuSystemTest`, `SystemManagerPauseTest`,
   `FeatureShowcaseSmokeTest`, `AudioSystemShowcaseTest`, `InputSystemTest`, `EditorPauseActionsTest`).
