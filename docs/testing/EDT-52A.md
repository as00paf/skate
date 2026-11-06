# EDT-52A — Window standard foundation

Chunk A only builds the shared parts. Most windows adopt them in chunks B–I, so the visible changes are limited to
the two dialogs (Export Project, Input Testing) and the error dialog.

1. Open **File > Export…**. The dialog has a thin header line with a **?** button at the right. Hover it: tooltip
   "Help: what this window is…". Click it: a popover shows "What it is" / "How to use it" with the placeholder
   "Help for this window is coming soon.". Click outside or press Esc to close the popover.
2. In the same dialog, right-click the **title bar**: a menu with **Help** and **Close window** (no Reset position,
   since dialogs float). Help opens the same popover; Close window closes the dialog.
3. Reopen it. The bottom has a **Close** button aligned to the right. Press **Esc** with the dialog focused: it
   closes.
4. Open **Input Testing** (hamburger ▸ Input). It has the same header with **?**, the same title-bar menu and the
   right-aligned Close.
5. Trigger an error, for example **File > Open Project…** on a file that is not a project. The error dialog is
   centred and dims the editor. **Copy details** is on the left and **OK** on the right. Press **Enter**: the dialog
   closes (OK). Trigger it again and press **Esc**: it also closes.
6. Trigger two errors quickly (if possible): "1 more message(s) after this one" shows, and OK moves to the next one.
7. Switch the editor language to French: the menu items, tooltips and the help headings are translated (Aide, Fermer
   la fenêtre, Ce que c'est, Comment l'utiliser).
8. **View > Reset layout** still works, and no empty "Environment" or "Physics Tuner" tab appears.
