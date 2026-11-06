# GUI-12 test steps: the exported game's Esc menu matches editor Play

The cause: the exported game rendered at a fixed 1920x1080 and presented the image at that size, even when fullscreen
at another resolution. So the pause menu covered only part of the screen and the mouse clicks missed it. Now the game
renders at the window's size, as editor Play renders at the viewport's size. Both get the menu from one factory.

Setup: a monitor that is NOT 1920x1080 shows the old bug best (e.g. 2560x1440, or 1920x1200).

1. `.\gradlew.bat run`, open a project with a playable scene, press Play, click the viewport, press Esc → note the
   menu: "Paused" title, Resume, Restart, Language, Quit, then the Volume label and slider, centred.
2. Export the project and run the exported game. The scene fills the whole screen, with no black or stretched area.
3. Press Esc → the same menu, centred on the screen, with the same items in the same order and proportions as step 1.
4. Move the mouse over each button → the hovered button highlights. Then click Resume → the menu closes and the game continues.
5. Esc, Language → the game text switches language and the menu stays open. Drag Volume → the music level changes.
6. Esc, Restart → the level reloads and the HUD timer resets. Esc, Quit → the window closes (in the editor: Play stops).
7. `.\gradlew.bat test --tests "com.pafoid.skate.game.ui.PauseMenuParityTest"` → BUILD SUCCESSFUL.
