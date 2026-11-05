# EDT-36: Edit String fields in the generic inspector (manual test)

1. Launch the editor, create a **new** project and open its default scene.
2. Create two empty objects named `Hero` and `Rig`, then create a child `Sidekick` under `Rig`.
3. Create a camera object and add a **Follow Camera** component; its **Target** shows a text field (`Skater`), a
   **Pick object** dropdown and an orange "No object named Skater" warning.
4. Type `Hero` in the Target field and press Enter; the warning disappears. Ctrl+Z restores `Skater`, Ctrl+Y redoes.
5. Open **Pick object**: it lists `Hero`, `Rig`, `Sidekick`, …; choose `Sidekick`. Undo/redo work.
6. Drag `Hero` from the Scene Hierarchy onto the Target text field: the target becomes `Hero`.
7. Press Play: the camera follows `Hero` (move it in the hierarchy under `Rig` and Play again: still followed).
8. Switch the editor language to French: labels are translated and the fields keep working.
