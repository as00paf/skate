# EDT-52F test steps: modals and the Search Anywhere palette

Switch Project and New Project are now real modals: they dim and block the editor, Esc = Cancel, Enter = the main
action, and they use the standard footer. Search Anywhere is a palette: it closes on Esc, after a choice, or when you
click outside it. It has a Test Input command.

1. `.\gradlew.bat run` with a project open. Hamburger > Switch Project → a centred modal; the editor behind is dimmed
   and does not react to clicks. Footer: [New Project] [Cancel] [Open Project]. Esc → it closes.
2. Open Switch Project again, click `?` at the top right → the help popover (What / How / Related) opens on top.
3. Click a recent project → it opens and the modal goes away. Switch Project, then New Project → the wizard opens
   as a modal with [Open Project] [Cancel] [Create Project].
4. In the wizard, type a valid name: Create Project becomes enabled, and Enter (outside the text field) creates the
   project. Esc cancels and clears the fields.
5. Ctrl+P (Search Anywhere, the default binding) → the palette opens without a title bar. The search box has focus right away, so
   typing works immediately (EDT-48).
6. Type `test input` (or `gamepad`; in French, `manette`) → "Test Input..." appears under Actions; press Enter →
   the Input Testing dialog opens.
7. Open the palette and click anywhere outside it → it closes. Open it again, click `?` → the help shows and the
   palette stays open. Type `zzzz` → "No results" with a Clear search button that empties the box.
