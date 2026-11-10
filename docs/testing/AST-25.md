# AST-25: deleting a store asset updates the attribution ledger, manual test

1. Open a project and import **Wooden Table 01** from the Asset Store (Poly Haven, 1k) as in `docs/testing/AST-16.md`. The Attribution tab lists it, and `<project>\.skate\attribution.json` has one entry with one GUID per imported file.
2. In the Project window, delete `Assets\Store\polyhaven\woodentable_01\WoodenTable_01.bin`. `attribution.json` still has the entry, without that file's GUID (compare with the `guid` in its `.meta`, which went to the trash with the file).
3. **Edit > Undo**: the file and its `.meta` come back, and the entry has the GUID again.
4. Delete the whole `woodentable_01` folder: the entry disappears from `attribution.json` and from the Attribution tab (it reloads when you open it). Undo brings both back; Redo removes them again.
5. Delete a file that did not come from the store (e.g. a texture you copied into `Assets`): `attribution.json` does not change.

Automated: `.\gradlew.bat :test --tests "com.pafoid.skate.editor.commands.DeleteFileCommand*"`.
