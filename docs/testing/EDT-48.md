# EDT-48 manual test steps: Search Everywhere input focus

Before the fix, the focus call ran after the window had ended, on every frame, so the field never got keyboard focus and clicks into it did not stick.

1. Run `.\gradlew.bat run` and open a project with a few objects in the scene.
2. Press **Ctrl+P**, or use the search button in the window controls. Expected: the **Search Everywhere** window opens centred, and the text field shows a blinking cursor.
3. Type `cam` straight away, without clicking. Expected: the letters appear in the field and results show below.
4. Click the **X** next to the field. Expected: the field empties and keeps the cursor; you can type again without clicking.
5. Click a filter chip (for example **GameObjects**), then click back into the text field. Expected: the cursor stays in the field and typing works.
6. Press **Esc**. Expected: the window closes.
7. Press **Ctrl+P** again. Expected: the field is empty, focused, and ready for typing.
8. Type a query, press **Down** and then **Enter**. Expected: the selected result opens and the window closes.
