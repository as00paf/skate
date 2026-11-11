# Resolve Conflicts

When the same file was changed on two branches, Git cannot choose which version to keep: the file has a **conflict**. This dialog lets you choose, file by file. It opens by itself when conflicts appear, and the branch button in the status bar turns red until they are resolved.

## What it is

- The **list** on the left holds every conflicted file.
- The **comparison** on the right shows your version (the current branch) against theirs (the other branch).
- **Keep mine** keeps your version; **Take theirs** replaces it with the other branch's version. Either way the whole file is chosen; there is no line-by-line merge.

Scenes with conflicts do not open in the editor until they are resolved.

## How to use it

1. Click a file to compare both versions.
2. Tick one or more files (or click one), then click **Keep mine** or **Take theirs**.
3. When the last conflict is gone, click **Commit...** to commit the merge in the [Commit](vcs_commit.md) dialog.

## Related windows

- [Commit Changes](vcs_commit.md): commit the merge.
- [Version Control](version_control.md): the Git log.
