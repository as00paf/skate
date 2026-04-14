---
name: dead-code-cleanup
description: >
  Find and remove unused code: unused methods, classes, interfaces, fields,
  imports, callback interfaces, and duplicate classes. Use after refactoring
  when switching from one pattern to another (e.g., callbacks → events).
---

# Dead Code Cleanup Skill

## When to Use

- After refactoring from callbacks to events
- After merging duplicate classes
- After changing architecture patterns
- When you suspect unused methods/classes are lingering

## Types of Dead Code

| Type | Example | How to Find |
|------|---------|-------------|
| **Unused methods** | Helper methods no longer called | `grep_search` for method name |
| **Unused classes** | Old implementations, duplicates | Check imports/usages |
| **Callback interfaces** | `interface XyzCallbacks { ... }` | No implementations remain |
| **Unused imports** | `import com.pafoid.skate.Xyz` | IDE warnings, grep for usage |
| **Deprecated fields** | `val oldPath: String` superseded by new field | Check all references |
| **Duplicate classes** | Two `PrefabType` enums in different packages | One has zero importers |

## Process

### 1. Identify the Dead Code

After a refactor, list what changed:
- "Replaced callbacks with events" → find and delete the callback interface
- "Renamed X to Y" → find and delete old X
- "Moved X from package A to B" → find and delete old X in A

### 2. Verify No Remaining References

Use `grep_search` to find ALL references:
```
grep_search: pattern="methodName", glob="**/*.kt"
```

If the only results are the definition itself, it's safe to delete.

### 3. Delete Dead Code

- Delete entire unused methods
- Delete entire unused classes/files
- Delete entire unused interfaces
- Remove unused imports

### 4. Verify Build Still Passes

```powershell
.\gradlew.bat compileKotlin
```

## Common Scenarios

### Scenario: Callbacks → Events Refactor

Before: UI implements `XyzCallbacks`, calls `service.doX()`
After: UI publishes `XyzAction` event, handler calls command

**Delete:**
- The `interface XyzCallbacks { ... }` definition
- The `object : XyzCallbacks { ... }` implementation
- Any `callbacks: XyzCallbacks` constructor parameters
- Unused helper methods that were only called by the callbacks

### Scenario: Duplicate Class Cleanup

Two `PrefabType` enums exist in different packages. One is used, one isn't.

**Delete the unused one:**
```powershell
Remove-Item "C:\path\to\unused\PrefabType.kt" -Force
```

**Update any remaining imports that referenced it.**

### Scenario: Method No Longer Called

A method was replaced by event publishing.

**Delete the method entirely** — it's no longer referenced anywhere.

## Anti-Patterns (NEVER Do These)

❌ **Comment out dead code** — delete it, Git has the history
❌ **Leave "just in case" methods** — if it's not called, it's dead
❌ **Keep unused interfaces** — they confuse future developers
❌ **Ignore duplicate classes** — they cause import confusion

## Checklist

- [ ] Searched for all references to suspected dead code
- [ ] Confirmed zero call sites remain
- [ ] Deleted unused methods/classes/interfaces/fields
- [ ] Removed unused imports
- [ ] Build still passes (`.\gradlew.bat compileKotlin`)
- [ ] No orphaned files remaining
