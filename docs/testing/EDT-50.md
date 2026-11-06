# EDT-50: Missing editor strings, manual test

Run `.\gradlew.bat run`, open a project, then set **Editor Settings > Language** to **Français**.

1. **Render Graph** window: untick **Mise à jour auto**. A French sentence shows in place of `!!lbl.render_graph.auto_update_disabled!!`.
2. **Environment** window: change the time of day and the sun colour. In **Command History**, the rows read "Régler l'heure de la journée" and "Régler la couleur du soleil".
3. **Key Bindings** window: mouse and gamepad bindings read "Souris bouton …" and the translated stick and trigger names. Searching for `souris` finds them.
4. Rebind one action, then click its **Reset**. In Command History, the rows read "Réassigner …" and "Réinitialiser les assignations de …" with the target "Raccourcis Clavier".
5. **Input Testing** window: drag a dead-zone slider. Command History shows "Modifier la zone morte : …" with the target "Paramètres d'entrée".
6. **File > Save Scene As**: the native dialog title is "Enregistrer la scène sous", the file name is pre-filled with `<scene>.scene` and the filter reads "Scène Skate".
7. Switch back to English and repeat steps 1 and 2. The text is in English, and no `!!key!!` shows anywhere.

Automated: `.\gradlew.bat :test --tests "*EditorStringKeysTest" --tests "*EditorHardcodedLabelGuardTest" --tests "*StringTablesTest"`.
