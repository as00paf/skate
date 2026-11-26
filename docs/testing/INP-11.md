# INP-11 manual test steps: project input actions

A project defines its own input actions and contexts in `Assets/Data/input.actions.json` (`docs/design/INP-11.md`), edited in **Key Bindings**. Use a **new project** (older projects start with no project actions and get the file on the first edit). Run on Windows from the repo root.

1. Run `.\gradlew.bat test`.
   Expected: `BUILD SUCCESSFUL`. New tests: `ProjectActionSetTest`, `ProjectActionsTest`, `InputSystemProjectActionsTest`, `EditProjectActionsCommandTest`, `ProjectActionsModelTest`, `KeyBindingsProjectActionsTest`.
2. Run `.\gradlew.bat run`, open the new project and open **Key Bindings**. Click **New action**. Type the id `Fire!`, then `fire`; set the name **Fire**, a category, and press **OK**.
   Expected: an invalid id shows the reason and blocks OK. **Fire** appears in the list marked as a project action, with no binding.
3. Click the **Fire** binding slot, press **F**.
   Expected: F is bound. `Assets/Data/input.actions.json` now holds `fire` with the F default. Code actions show a lock and cannot be edited.
4. Right-click **Fire**: **Edit** (change the name), **Duplicate**, **Change id…** (read the warning), then **Delete** (confirm).
   Expected: each change shows in Command History and in the file, and the list updates. An id that a code action uses is refused.
5. Under **Contexts**, add a context `vehicle` with **Active at start** on, and a second action `steer` in it. Try to delete `vehicle`.
   Expected: the delete is refused while `steer` uses it. After the action is removed, the delete works.
6. Press **Ctrl+Z** several times, then **Ctrl+Y**.
   Expected: each undo and redo restores the list and the file.
7. Press **Play** with a script that logs `isActionDown("fire")`, hold **F**.
   Expected: it reports true while F is held. Rebind **Fire** to **G** while stopped: G now works.
8. Run `.\gradlew.bat gameJar`, export the game and repeat step 7.
   Expected: the exported game reads the same actions. If it does not, the pack is missing `Assets/Data/input.actions.json` (NEEDS from skate-assets, AST-48).

## Known limits

- Scripts and scene fields that use an action id are not rewritten by **Change id…**, and Delete does not list them yet (INP-12 adds `@InputActionRef`).
- The file is not reloaded when changed on disk.
