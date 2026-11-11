# Version Control

Version Control keeps the history of your project with **Git**. This window is the project's **Git log**: every commit of every local branch, newest first. You do not need to install Git: the editor has it built in.

## What it is

A few words:

- **Repository**: the project folder once Git keeps its history.
- **Commit**: a saved snapshot of the files you picked, with a short message that says what you did.
- **Branch**: a separate line of history. Make a branch to try an idea; switch back if it does not work out.
- **Conflict**: the same file was changed on two branches and Git cannot choose. You decide which version to keep.

The window shows a table of commits:

- **Graph**: one coloured line per line of history. A dot is a commit; lines that split are branches, lines that join are merges. `[main]` marks the commit a branch points at; the current branch is shown in gold.
- **Message**, **Author** and **Date** of each commit.

On the right, the selected commit's message and files. Click a file to see what that commit changed in it.

## How to use it

1. Open it from **View > Windows > Project**, or from **History...** in the branch button at the right of the status bar. If the project has no history yet, click **Initialize**.
2. Click a commit to see its files, then a file to see its diff.
3. Pick a branch in **Branch** to see only its commits, or type in the filter to find a commit by message, author or id. The graph hides while a text filter is on.
4. Everyday work happens from the **branch button** in the status bar, which shows the current branch and how many files changed:
   - **Commit...** (Ctrl+K) and **Changes...** open the [Commit](vcs_commit.md) dialog.
   - **New Branch...** creates a branch. Each branch in the list has **Checkout**, **New Branch from here...** and **Delete...**.
   - **Resolve Conflicts...** opens the [Conflicts](vcs_conflicts.md) dialog.
   - **Update Project** needs a remote repository, which comes later.

Git actions cannot be undone with **Ctrl+Z**: a commit stays in the history. For anything the editor does not do, run `git` in the [Shell](shell.md).

## Related windows

- [Commit Changes](vcs_commit.md): pick files, write a message, commit.
- [Resolve Conflicts](vcs_conflicts.md): keep your version or take theirs.
- [Project](project.md): the files under version control.
- [Shell](shell.md): run other Git commands.
