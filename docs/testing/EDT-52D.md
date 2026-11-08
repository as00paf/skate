# EDT-52D manual test steps (debug windows)

Start the editor (`.\gradlew.bat run`) and open Console, Profiler, Command History and Shell from View > Windows.

1. Each tab title starts with an icon: Console a list, Profiler a line chart, Command History a clock-arrow, Shell a terminal. Right-click a tab: the menu shows Help, Reset position and Close window. Help opens the popover, Close closes the window and View > Windows reopens it.
2. In each window, click the `?` at the right end of the top row. A popover shows "What it is", "How to use it" and "Related windows" with buttons that open those windows. Switch the language to French: the help text is in French.
3. Console: clear it. A centred "No log entries" state shows. Do something that logs, then type a search with no match: "No results" shows with a Clear search button, and clicking it shows the logs again. Right-click with nothing selected: Copy selected is grayed out.
4. Profiler: Freeze and Reset Stats sit in the top row next to `?`. Freeze stops the graphs, and Reset clears them.
5. Command History: with an empty history, a centred "No Command History" state shows. Move an object three times, right-click the first command and choose Undo to here: all three moves are undone. Right-click an undone command and choose Redo to here: the scene comes back to that step.
6. Shell: before running anything, the output area shows "No output yet". Run `echo hi`; the output shows. Right-click the output: Copy all, then a separator, then Clear last.
