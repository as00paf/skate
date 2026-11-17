# ANI-21 — Animator window: manual test

Build a controller from scratch in the window and drive a character with it. No file is edited by hand.

## Setup

1. Open a project with a character that has an **Animator** and a few clips (the skater prefab works).
2. Have at least one `.skanim` clip in `Assets/Animations` (Video to Animation or the Animation Editor make one).

## Steps

1. **View > Windows > Assets > Animator.** The window says how to start. Click **New**: `Assets/Animations/New Controller.controller.json` is created and opened with one state, `Idle`.
2. Double-click `Idle` and rename it `idle`. Select the character in the Hierarchy and click **Use selected**: the clip lists now show its clips.
3. Select `idle` and pick a clip in **Clip**. Right-click the graph > **Add state** twice; rename them `walk` and `jump`; give `walk` a clip; drag a `.skanim` from the Project window onto the graph: a state named after the clip appears.
4. In **Parameters**, click **+ Float** and rename it `speed`; click **+ Trigger** and rename it `kick`.
5. Right-click `idle` > **Make transition**, click `walk`. Select the arrow: untick **Wait for the end of the clip**, click **+ Condition**, pick `speed`, **is greater than**, `1`. Hover the arrow: the tooltip reads "idle → walk when speed is greater than 1.00 …".
6. Make `walk` → `jump` with the condition `kick` **is set**. Make `jump` → `idle` and keep **Wait for the end of the clip**.
7. Try **Ctrl+Z** / **Ctrl+Y** on any of these steps: each undoes one change. Drag boxes around: one undo step per drag. Try renaming `walk` to `jump`: refused, with a message.
8. Select `walk`, **Ctrl+D**: `walk 2` appears without transitions. Right-click `walk` > **Duplicate with transitions**: the copy gets `idle → walk 3` and `walk 3 → jump`. Delete both copies.
9. Click **Use on <character>**, then **Save**. Press **Play**. The window shows **Live**: `idle` is green. Drag the live `speed` above 1: the arrow to `walk` fills while it blends, then `walk` is green. Click **Fire** on `kick`: the character jumps, then returns to `idle` at the end of the clip.
10. While still playing, change a blend time and **Save**: the character restarts in its default state with the new graph.
11. Stop. Open the **skater** controller from the **Controller** list: it is read-only. Click **Make a project copy to edit**: the copy opens and can be edited.

## Expected

- Every step works through the window; the saved `.controller.json` holds `parameters`, `layout` and `clips`.
- Help (`?`) opens the Animator article in English and French.
