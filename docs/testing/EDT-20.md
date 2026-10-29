# EDT-20 manual test: Key Bindings rework

Prerequisites: Windows, JDK 17, repo checked out on branch `agents/editor` (commit `7cb14b4f`). A gamepad is optional (steps 12-13).

1. Open a terminal in the repo root and launch: `.\gradlew.bat run`. Expected: the editor window opens.
2. File > New Project. Enter name `KbTest`, pick an empty folder, confirm. Expected: project opens with a default scene.
3. Settings > Key Bindings... Expected: window opens, first tab "Game Actions" with a search box, Reset All button, Profile row, and groups "Gameplay / Movement", "Gameplay / Camera", "Gameplay / Actions", "UI / Navigation". Names are translated (Jump, Sprint...).
4. Under Gameplay / Actions, "Jump" shows buttons `SPACE` and `A`. Hover them: tooltip explains rebinding.
5. Click the `SPACE` button of Jump. Expected: it reads "Press an input...". Press `Q`. Expected: it now reads `Q`; the Jump row shows `*` and a "Reset" button.
6. Edit > Undo (or Ctrl+Z). Expected: Jump is back to `SPACE`, `*` gone. Edit > Redo restores `Q`.
7. Click `Q`, press `E` (used by "Mount / Dismount"). Expected: conflict dialog listing Mount / Dismount with Swap / Unbind other / Cancel.
   - Cancel: nothing changes.
   - Repeat, choose Swap: Jump = `E`, Mount / Dismount now has `Q`.
   - Undo: both revert.
   - Repeat, choose Unbind other: Jump = `E`, Mount loses `E` (keeps gamepad Y).
8. Click `+` on Sprint > "Mouse button" > "Mouse Left". Expected: Sprint gets a "Mouse Left" button.
9. Click `x` next to it: removed. Click Sprint's "Clear": all bindings removed. Click its "Reset": defaults return.
10. Click the small "Reset" next to the "Gameplay / Actions" header: all Actions return to defaults. Rebind Jump to `Q` again, then click "Reset All": everything default.
11. Type `space` in the search box: only Jump (and anything bound to Space) is listed. Type `zzz`: "No action matches the search." Clear the box.
12. (Gamepad) Click Jump's `A` button, press gamepad button `X`: it reads `X`. Click `+` > "Gamepad input...", push the left stick left: it adds "Left X (-)".
13. Without a gamepad, steps 12 can't be done; verify `+` > "Gamepad input..." shows "Press an input..." and "Cancel" aborts.
14. Profiles: rebind Jump to `Q`. Type `Lefty` in the profile name box, click Save. Expected: message "Profile saved..." and file `KbTest\InputProfiles\Lefty.json` exists.
15. Click Reset All, then Profile "Load profile..." > `Lefty`. Expected: Jump = `Q`. Undo returns to defaults.
16. Click "Copy JSON", then Reset All, then "Paste JSON". Expected: Jump = `Q` again ("Profile imported"). Put invalid text on the clipboard and click Paste JSON: "Could not read that profile."
17. Delete: with `Lefty` in the box click Delete; reopen the Load list: "No saved profiles".
18. Persistence: rebind Jump to `Q`, close the editor (File > Exit or window X), relaunch `.\gradlew.bat run`, open KbTest (File > Open/recent), open Settings > Key Bindings...: Jump still `Q`.
19. Other tabs: Editor / Camera / Gamepad / Hierarchy still work as before (Gamepad tab says dead zones live in Input Testing; Input > Input Testing... opens it). "Reset to Defaults" is hidden on the Game Actions tab.
20. With no project open (File > Close Project), the Game Actions tab shows "No project is open...".

Automated: `.\gradlew.bat :test` (EditBindingsCommandTest, BindingProfileStoreTest, ActionBindingsModelTest).
