# EDT-45 — Play, Pause, Stop and Reset

1. Open a project with the Feature Showcase scene. Note the viewport toolbar layout, then press **Play**. Check that the toolbar does not move and that the gizmo buttons (select, move, rotate, scale, space, snap, measure) are grayed out but still visible. Hovering one still shows its tooltip.
2. Let it run a few seconds (boxes fall, the character animates, audio plays). Press **Pause**: everything freezes where it is and audio is held. Press **Play** (now "Resume"): it continues from the same point.
3. Press **Reset** (the undo-arrow button) while playing: the scene goes back to its pre-Play state and keeps playing (boxes back on top, animation and audio restart, day/night time restored).
4. Pause, then press **Reset**: the scene is restored and stays paused.
5. Move an object in Edit, then Play and press **Stop**: you are back in Edit mode, the scene looks exactly as before Play (positions back, objects spawned in Play gone, no unsaved marker added by Play), and the gizmos work again. **Stop and Reset clear the undo history**: Undo is empty afterwards, even for the move made before Play (the tooltips say so).
6. With MainScene and Feature Showcase both open, press Play in MainScene, switch to the Feature Showcase tab and let it run, then press **Stop** (or use the Play shortcut). Both scenes are back to their pre-Play state, MainScene is the active tab again, and switching to the showcase shows it stopped.
7. Press **Play**, then **File > Close Project** (or open another project) while it is playing. The next project opens in Edit mode (Play button shown, gizmos enabled), and reopening the first project shows its pre-Play state, not the played state.
8. Press **Play** and quit the editor with the window's close button. Restart the editor: it opens in Edit mode.
