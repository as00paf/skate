# EDT-47 manual test steps: Console scrolling

Before the fix, Auto-Scroll forced the view to the bottom on every frame, so you could not scroll up or drag the scrollbar.

1. Run `.\gradlew.bat run`, open a project, and open the **Console** window.
2. Make it log quickly: press Play on a scene that logs every frame. If no scene does, use the Shell panel or toggle Play/Stop repeatedly to add many lines. Expected: the list follows the newest line and **Auto-Scroll** is ticked.
3. Scroll up with the mouse wheel. Expected: the view stays where you put it, **Auto-Scroll** unticks itself, and new lines do not move the view.
4. Drag the scrollbar thumb up and down. Expected: it follows the mouse and does not snap back.
5. Scroll or drag back to the very bottom. Expected: **Auto-Scroll** ticks itself again and the list follows new lines.
6. Untick **Auto-Scroll** while at the bottom. Expected: it stays unticked, and new lines pile up below the view.
7. Tick it again. Expected: the view jumps to the newest line.
8. Scroll up, then click **Clear**. Expected: no error; the empty list shows and logging resumes normally.
