# Commit Changes

The Commit dialog lists what changed in the project since the last commit and records the files you pick in the history. **Changes...** in the branch button opens the same dialog.

## What it is

- The **list** on the left groups changed files as Scenes, Prefabs, Scripts, Assets, Settings and Other. `M` is modified, `A` added, `D` deleted. A file's `.meta` companion goes with it. New files start ticked.
- The **diff** on the right shows what changed in the file you click.
- The **message** box at the bottom says what you did.

If a scene has unsaved edits, the dialog names it: click **Save all** first, so the commit has your latest work. While files have conflicts, resolve them in the [Conflicts](vcs_conflicts.md) dialog first.

## How to use it

1. Click the branch button at the right of the status bar, then **Commit...**, or press **Ctrl+K**.
2. Tick the files to commit (**All** ticks every file). Click a file to see its diff.
3. Write a message and click **Commit**, or press **Ctrl+Enter** in the message. The status bar confirms the commit, and the dialog stays open for the next one.
4. To throw away changes, tick the files and click **Discard**. The files go back to the last commit and new files are deleted, so read the confirmation carefully.

Stop Play before committing. A commit cannot be undone with **Ctrl+Z**.

## Related windows

- [Version Control](version_control.md): the Git log with every commit.
- [Resolve Conflicts](vcs_conflicts.md): fix conflicted files before committing.
