# EDT-49 manual test steps: Remove-component confirmation

Before the fix, the dialog was submitted once per expanded component under a shared ID. With two or more components expanded, its text and buttons appeared several times, and Yes could remove the wrong component.

1. Run `.\gradlew.bat run`, open a project, and select an object that has at least three components (for example a Camera, or a cube with a Transform and a Render component).
2. In **Properties**, expand all of its components.
3. Click the trash button on the **last** component. Expected: one centred **Remove Component Confirmation** dialog, with one message that names the component and the object, and one row with **Yes** and **No**.
4. Click **No**. Expected: the dialog closes and the component is still there.
5. Click the trash button again, then press **Esc**. Expected: the dialog closes and nothing is removed.
6. Click the trash button on a **middle** component, then click **Yes**. Expected: exactly that component disappears; the others stay.
7. Press **Ctrl+Z**. Expected: the removed component comes back.
8. Close a scene tab that has unsaved changes. Expected: its close confirmation (which uses the same dialog) is also centred, with wrapped text and Yes/No.
