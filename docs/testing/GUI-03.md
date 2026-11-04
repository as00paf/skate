# GUI-03 / GUI-05 / GUI-06 test steps: UI authoring and button hover

GUI-05: Add Component on a plain GameObject brings what a UI widget needs: a `UI_ELEMENT` rect (centered,
default size) and, when there is no canvas on the object or a parent, a `UI_CANVAS`. A canvas object can hold
widgets itself. If something is still missing, the inspector shows a yellow hint with an "Add missing component" button.

Intended hover/press behaviour (GUI-06):
- **Game and editor Play:** a button is dark grey-blue normally, bright blue while hovered, and dark blue while
  pressed *and* still under the pointer. A click fires on release over the same button. The highlight goes away as
  soon as the pointer leaves. Keyboard/gamepad focus (Tab, d-pad) shows the hover colour plus a yellow outline.
- **Editor, not playing:** no hover or press. Mouse drags in the viewport edit the rect instead (rect gizmo).
  Buttons always show their normal colour. On Stop, any hover or press state is cleared.
- Buttons saved before GUI-06 keep the colours stored in the scene (the old, barely visible hover). Use new
  buttons, or edit `hoverColor` in the inspector.

Setup (once): `.\gradlew.bat run`, create a NEW project (File > New Project), open its scene.

1. Right-click the viewport > Create Empty. Select it, then in the inspector click `+` (Add Component) > `UI_CANVAS`,
   then `+` > `UI_BUTTON` -> a 300x80 "Button" appears centered in the viewport, and `UI_ELEMENT` was added too.
2. Create a second empty GameObject and add only `UI_BUTTON` -> `UI_ELEMENT` and `UI_CANVAS` are added
   automatically, and a second button is drawn at the center.
3. With a button selected, drag inside its blue outline to move it aside, and drag a corner square to resize it.
   Ctrl+Z undoes each drag. Hovering it while not playing does **not** change its colour.
4. In the hierarchy, right-click the first object > Create Empty Child, then `+` > `UI_BUTTON` on the child -> it
   gets only `UI_ELEMENT` (no second canvas). Ctrl+Z removes both components, and Redo brings them back.
5. File > Save Scene, then File > Open Scene and reopen it -> all buttons are back where you left them.
6. Press Play. Move the mouse over a button -> it turns bright blue. Move it off -> normal again at once. Do this
   with the editor window not maximized and dragged away from the top-left corner: the highlight must match the
   button's edges exactly.
7. Still playing: press and hold on a button -> dark blue. Drag off while holding -> normal colour, and releasing
   outside does nothing. Press Tab -> the yellow focus outline appears. Stop -> all buttons are back to normal.
8. Menu path: right-click the viewport > UI > Button -> a "Canvas" with a "Button" child is created and drawn.
   Run `.\gradlew.bat test` -> BUILD SUCCESSFUL (`UiManualAuthoringTest`, `AddUiComponentCommandTest`, `UiHoverPressTest`).
