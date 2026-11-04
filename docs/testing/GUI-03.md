# GUI-03 / GUI-05 test steps: UI authoring in the editor

GUI-05 fixes UI built by hand. Adding a UI widget from the inspector's Add Component now also adds what it needs:
a `UI_ELEMENT` rect (centered, default size) and, when the object has no canvas on itself or a parent, a
`UI_CANVAS`. A canvas object can hold widgets itself. If something is still missing, the inspector shows a yellow
hint with an "Add missing component" button.

Setup (once): `.\gradlew.bat run`, create a NEW project (File > New Project), open its scene.

1. Right-click the viewport > Create Empty. Select the new GameObject. In the inspector, click `+` (Add Component) >
   `UI_CANVAS`, then `+` > `UI_BUTTON` -> a dark 300x80 "Button" appears centered in the viewport, and the object
   now also lists `UI_ELEMENT`.
2. Create a second empty GameObject and add `UI_BUTTON` first, then look at its components -> `UI_ELEMENT` and
   `UI_CANVAS` were added automatically, and a second button is drawn at the center (on top of the first; drag
   it aside in step 3).
3. With a button selected, drag inside its blue outline to move it, and drag a corner square to resize it.
   Ctrl+Z undoes each drag. (Steps 1 and 2 made two separate canvases: select between them in the hierarchy.)
4. In the hierarchy, right-click the first object > Create Empty Child, then `+` > `UI_BUTTON` on the child -> it
   gets only `UI_ELEMENT` (no second canvas) and is drawn centered inside its parent button's rect.
5. Ctrl+Z after step 4's Add Component -> the child loses both `UI_BUTTON` and `UI_ELEMENT`; Redo brings them back.
6. File > Save Scene, then File > Open Scene and reopen it -> all buttons are back where you left them.
7. Press Play in the viewport toolbar -> the buttons still show, hovering lightens them, and clicking does not throw.
   Stop restores the editor state.
8. Menu path: right-click the viewport > UI > Button -> a "Canvas" object with a "Button" child is created and
   drawn. Run `.\gradlew.bat test` -> BUILD SUCCESSFUL (`UiManualAuthoringTest`, `AddUiComponentCommandTest`).
