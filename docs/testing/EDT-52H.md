# EDT-52H: domain windows follow the window standard

Audio Inspector (with its mixer) and Script Editor get the header icon, the `?` help, the tab menu and the shared empty states (docs/design/EDT-51.md §2).

1. Start the editor with **no project open**. Open View > Windows > Audio Inspector and Script Editor. Each title starts with an icon (music note, pencil), and each shows the centred "No project open" state with **New Project** and **Open Project** buttons.
2. Click **?** at the right of each window's header strip. A popover shows "What it is", "How to use it" and "Related windows". The related buttons (Properties / Asset Browser, Project / Console) open those windows. Click outside or press Esc to close it.
3. Right-click each window's tab. The menu shows **Help**, **Reset position** and **Close window**. Help opens the same popover. Drag a window elsewhere, then use Reset position: it docks back in its default place.
4. Open a project and a scene, and select nothing. Audio Inspector > Selected object shows "Select an object to see its audio source" in a short section, and the Mixer section stays right below it, not pushed to the bottom.
5. Select an object with no audio source. The section shows the message and an **Add Audio Source** button. Click it: the source inspector appears. Ctrl+Z removes it again.
6. Open the Mixer section and move a bus slider. You hear the change while dragging, and it saves on release (no change from before).
7. In the Script Editor with a project and no tab open, check for the centred "No script open" state with the double-click/New hint. Save, Check, the name box and New sit on the left of the header strip, and `?` sits on the right. There is no empty menu bar above them.
8. Switch the editor to French (Editor Settings). Titles, help sections and empty states are translated.
