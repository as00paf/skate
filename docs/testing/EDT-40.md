# EDT-40: Review of round-16 editor work

Start the editor and create a **new project**; the FeatureShowcase scene has all the objects used below.

1. **Animator (EDT-16):** select the "[Animation] Upper-body mask" character. Properties shows the Animator
   inspector (Controller and Mask pickers), not raw fields.
2. **Edits survive a selection change:** in a component's String field (e.g. a FollowCamera target), type a value
   and, without pressing Enter, click another object in the Hierarchy. Command History shows "Edit Field". Do the
   same with a material slider (Ctrl+click, type) and the Audio sound path. Each records one entry on the right object.
3. **Undo marks the scene dirty:** edit a String field, Ctrl+S, Ctrl+Z. The scene tab shows the modified marker again.
4. **French:** switch to French. The audio Bus / Stream / Attenuation and material Alpha combos show French
   names, and Command History shows "Remplacer le matériau" and so on for material overrides.
5. **Theme preview:** Editor Settings > Interface, pick Light, then close with the title-bar X. The theme goes back
   to the saved one. Pick Light, click General, then come back: the preview and Apply are still there. In Light,
   table headers (Hierarchy, Project Settings) and the empty dock area are light too.
6. **Shell:** View > Windows > Debug > Shell docks next to the Console. Run `set /p X=` (Windows) or `read X`: it
   finishes at once instead of hanging. The history is in `%APPDATA%\Skate\shell_history.txt`.
7. **Export:** File > Export. Type `"C:\bad|path"` as the output folder: an "invalid output folder" message, no
   crash. Type `out`: the build goes to `<project>\out`. Sizes show in Mo/Ko in French.
