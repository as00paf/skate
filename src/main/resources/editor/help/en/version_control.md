# Version Control

Version Control keeps the history of your project. Each time you are happy with your work, you save a snapshot of it. Later you can see what changed, throw away a change that went wrong, or try an idea on the side without breaking the main version.

## What it is

The window uses **Git**, the most common version control tool. You do not need to install Git: the editor has it built in. A few words:

- **Repository**: the project folder once Git keeps its history.
- **Commit**: a saved snapshot of the files you picked, with a short message that says what you did.
- **Branch**: a separate line of history. Make a branch to try an idea; switch back if it does not work out.
- **Conflict**: the same file was changed in two places and Git cannot choose. You decide which version to keep.

The window has two tabs:

- **Changes**: the files changed since the last commit, grouped as Scenes, Prefabs, Scripts, Assets, Settings and Other. Conflicts are listed first. A file's `.meta` companion goes with it.
- **History**: the earlier commits, newest first.

The branch menu at the top shows the current branch.

## How to use it

1. Open it from **View > Windows > Project**. If the project has no history yet, click **Initialize**: the editor creates the repository and makes a first commit.
2. Work as usual. Open **Changes** to see the files you changed. Click a file to see what changed in it.
3. Tick the files to keep (**All** ticks every file), write a **Commit message**, and press **Commit**. Stop Play and save your scenes first.
4. To throw away changes, tick the files and click **Discard**. The files go back to the last commit and new files are deleted, so read the confirmation carefully.
5. Open **History** and click a commit to see its files. Click a file to see what changed in that commit.
6. Use the branch menu to switch branches, or **New branch** to start one from the current commit. **Delete branch** removes a branch you no longer need. Commit or discard your changes before you switch.
7. If a file has a conflict, choose **Keep mine** or **Take theirs** for it, then commit.

Git actions cannot be undone with **Ctrl+Z**: a commit stays in the history, and discarded changes are gone. For anything this window does not do, run `git` in the [Shell](shell.md).

## Related windows

- [Project](project.md): the files under version control.
- [Shell](shell.md): run other Git commands.
- [Command History](command_history.md): undo editor changes inside the session.
