# EDT-57 — Missing editor and project settings, Show tooltips

1. **Editor Settings › Interface:** turn **Show tooltips** off and press OK. Hover the Hierarchy's + button, a viewport toolbar button and a menu item: no tooltip. Hover a setting's label in Editor Settings, or a field label in the Properties inspector: its explanation still shows. Turn it back on: button tooltips return.
2. **Interface › UI scale:** set 1.50x and press OK: all editor text and controls grow. Set it back to 1.00x.
3. **Editor Settings › Scene view:** set Move step to 2, Rotate step to 45, then OK. Turn **Snap** on in the viewport toolbar and move / rotate an object: it moves in steps of 2 m and turns in steps of 45°. Change the steps while Snap stays on: the new steps apply right away.
4. **Scene view › Move speed** 20 and **Mouse sensitivity** 2.00x, then OK: WASD flies much faster and right-drag look / orbit turns twice as fast. Reset both with their reset buttons.
5. **Editor Settings › General:** turn **Open the last project on startup** off, OK, restart the editor: it opens on the New Project wizard instead of the last project. Turn it back on and restart: the last project opens.
6. **Project Settings › Metadata:** pick another **Startup scene** (e.g. FeatureShowcase), OK, close and reopen the project: that scene opens first. Ctrl+Z before closing restores the previous choice.
7. **Project Settings › Game:** set **Game language** to French, OK. Press Play: the in-game pause menu (Esc) is in French. Stop, set it back to English.
8. **Project Settings › Physics:** set **120 steps per second**, OK. Play the Feature Showcase: physics still runs at normal speed (smoother). Save, close and reopen the project: the setting is still 120.
