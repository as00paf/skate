---
name: git-workflow
description: Handle Git branch creation, commits, and PR preparation following the project's branching strategy. Use when starting new work, committing changes, or preparing a PR.
---

# Git Workflow

Handle Git branch creation, commits, and PR preparation following the project's branching strategy.

## When to Use

- Starting a new feature or bug fix
- Committing changes
- Preparing a pull request
- When the user asks to "create a branch", "commit", or "prepare a PR"

## Branch Naming

| Type     | Pattern                       | Example                               |
|----------|-------------------------------|---------------------------------------|
| Feature  | `feature/description-of-task` | `feature/add-point-light-support`     |
| Bug Fix  | `bug/description-of-bug`      | `bug/fix-viewport-assertion-error`    |
| Refactor | `refactor/description`        | `refactor-extract-render-pass-timing` |

## Workflow Steps

### 1. Create a New Branch

```bash
# Ensure we're on master and up to date
git checkout master
git pull origin master

# Create feature branch
git checkout -b feature/description-of-task
```

### 2. Stage and Commit

```bash
# Review changes
git status
git diff HEAD

# Stage all changes
git add .

# Review staged changes
git diff --staged

# Commit with descriptive message
git commit -m "type: brief description

Detailed explanation of what was changed and why."
```

### Commit Message Format

```
feat: add point light support to EnvironmentSystem

- Added PointLightComponent for position/color/intensity
- Updated LightingUniformsLoader to handle point lights
- Modified GeometryPass shader for point light calculations
```

**Prefixes:**

- `feat:` — New feature
- `fix:` — Bug fix
- `refactor:` — Code restructuring (no behavior change)
- `docs:` — Documentation only
- `test:` — Test additions or modifications
- `chore:` — Maintenance tasks, config changes

### 3. Review Recent Commits

```bash
git log -n 3 --oneline
```

### 4. Push Branch

```bash
git push -u origin feature/description-of-task
```

### 5. After User Review — Prepare PR

Summarize changes for the PR description:

```bash
git diff master...feature/description-of-task --stat
git diff master...feature/description-of-task
```

### 6. Merge (Only With User Approval)

```bash
# Switch to master
git checkout master

# Merge feature branch
git merge feature/description-of-task

# Push to remote
git push origin master

# Delete feature branch (local and remote)
git branch -d feature/description-of-task
git push origin --delete feature/description-of-task
```

## Important Rules

- **NEVER merge without explicit user approval**
- **NEVER push to master without user confirmation**
- **NEVER delete branches without user confirmation**
- Always run `git status` after merge to confirm clean state
- Ask before force-pushing or rebasing

## Common Commands Reference

```bash
# Check current branch and status
git status

# See uncommitted changes
git diff HEAD

# See staged changes
git diff --staged

# View recent commits
git log -n 5 --oneline

# Discard all uncommitted changes (DANGEROUS)
git checkout -- .
git clean -fd

# Stash changes temporarily
git stash
git stash pop
```
