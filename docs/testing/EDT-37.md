# EDT-37: Per-area string tables, manual test

1. Start the editor and create a **new project**. Menus, panels and inspector labels show English text, with no `!!key!!` placeholders.
2. Open the Feature Showcase scene: the overlay title and area labels render in the viewport (keys from the game tables, which the editor now loads too).
3. Switch to French (**Settings > Language > Français**). Menus, windows, tooltips and the Properties inspector (Transform, RigidBody3D, Camera…) switch to French.
4. Accented text displays correctly, e.g. **Éditer > Rétablir** and **Paramètres > Plein écran**, not `RÃ©tablir`.
5. Open the Input Testing, Physics Tuner, Environment, Audio Inspector and Command History windows: all their labels are in French.
6. Press **Play** in the Feature Showcase scene: the overlay and HUD text are French; **Stop**, then switch back to English: everything returns to English.
7. Restart the editor: the chosen language is kept and the text is still complete.
