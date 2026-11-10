# ANI-14 — Animation editor: how to test

Needs a project with a `.skanim` clip (make one with **Video to Animation**, or open any FBX/glTF animation: the editor
makes an editable `.skanim` copy next to it).

1. **View > Windows > Animation Editor** (it docks at the bottom). The window has a `?` help popover.
2. Pick the clip in the **Clip** list, or click the folder button and choose a file. Expected: one row per bone track
   (bone name, then T/R/S), keys as diamonds, an **Events** row, and the skater playing the clip as a skeleton in the
   preview. Creating a clip in Video to Animation opens it here automatically.
3. **Scrub:** click or drag on the ruler: the red playhead moves frame by frame (Snap on) and the preview pose follows.
   Play/Pause (Space), previous/next frame (arrows), start/end (Home/End), Loop and Speed work.
4. **Select and move keys:** click a key (yellow), Ctrl+click adds, drag on empty space draws a box. Drag a selected
   key sideways: it moves in whole frames while you drag. Release, then **Ctrl+Z**: the keys jump back in one step
   (Edit > Command History shows one "Move Keys"). Esc during a drag cancels it.
5. **Key:** click a bone name (or a joint in the preview: it turns blue), move the playhead, press **Key bones** (K):
   new keys appear on that bone's tracks at the playhead. Select one key: the inspector shows its frame and value
   (rotation in degrees); change the rotation: the preview pose changes. Ctrl+C / Ctrl+V pastes keys at the playhead;
   Delete removes the selection.
6. **Events:** type a name, **Add event**: a marker appears at the playhead. Drag it; rename or delete it.
7. **Retime:** set **Length** to twice the value, **Retime**: every key and event spreads out and the clip plays half
   as fast. **Trim** with a shorter length cuts the end. Undo restores each.
8. **Save:** the Clip list shows `* name` after edits; **Save** clears it. Close and reopen the project, open the clip:
   the edits are there. Add the clip to the skater's Animator and press Play: it plays as edited.

Not in this task: the curve editor and cleanup tools (ANI-15), and a rendered mesh preview (the preview draws the
skeleton; a skinned-mesh offscreen viewport needs the render team).
