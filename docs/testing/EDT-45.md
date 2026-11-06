# EDT-45 — Play, Pause, Stop and Reset

1. Open a project with the Feature Showcase scene. Note the viewport toolbar layout, then press **Play**. Check that the toolbar does not move and that the gizmo buttons (select, move, rotate, scale, space, snap, measure) are grayed out but still visible. Hovering one still shows its tooltip.
2. Let it run a few seconds (boxes fall, the character animates, audio plays). Press **Pause**: everything freezes where it is and audio is held. Press **Play** (now "Resume"): it continues from the same point.
3. Press **Reset** (the undo-arrow button) while playing: the scene goes back to its pre-Play state and keeps playing (boxes back on top, animation and audio restart, day/night time restored).
4. Pause, then press **Reset**: the scene is restored and stays paused.
5. Press **Stop**: you are back in Edit mode, the scene looks exactly as before Play (object positions, objects spawned in Play are gone), and the gizmos work again. Reset and Stop are grayed out.
6. Press the Play shortcut, move nothing, then press it again: Play starts, and then stops with the scene restored.
7. Press **Play**, then **File > Close Project** (or open another project) while it is playing. The next project opens in Edit mode (Play button shown, gizmos enabled), and reopening the first project shows its pre-Play state, not the played state.
8. Press **Play** and quit the editor with the window's close button. Restart the editor: it opens in Edit mode.
