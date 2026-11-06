# EDT-55 — Script editor shown by default and refined

1. Delete `imgui.ini` (or use View > Reset Layout) and start the editor: **Script Editor** is a tab in the bottom panel next to Assets/Console.
2. Double-click two `.kts` scripts in the Project window: each opens in its own tab; hovering a tab shows the file path.
3. Type in one script: its tab shows `* `. Press Ctrl+S: the `*` goes away.
4. Add a syntax error (e.g. `val x: Int = "a"`) and press Ctrl+S: a red dot appears in the gutter on that line, hovering it shows the message, and the tab shows `! `.
5. Edit a script, then click its tab's close button: an "Unsaved Changes" dialog appears. Cancel keeps the tab; Discard Changes closes it.
6. Press Ctrl+F, type a word: every match is highlighted, the count is shown, and Enter / F3 / Shift+F3 cycle through matches (the view scrolls to each).
7. Press Ctrl+H, type a replacement: Replace changes the highlighted match only, Replace All changes every match; Escape closes the bar.
8. Press Ctrl+G, type `12` or `12:5` and Enter: the caret moves to that line (and column) and the view scrolls there.
