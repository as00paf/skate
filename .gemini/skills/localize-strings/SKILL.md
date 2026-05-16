---
name: localize-strings
description: Find hardcoded English strings in ImGui/editor UI code and add them to strings.properties with proper localization keys. Use when reviewing UI code or when the user mentions missing translations.
---

# Localize Strings

Find hardcoded English strings in ImGui/editor UI code and add them to strings.properties with proper localization keys.

## When to Use

- After implementing new UI features
- When reviewing editor code
- When the user mentions "missing translations" or "hardcoded strings"
- Before marking UI tasks as complete

## Strings File Location

```
C:\workspace\kotlin_workspace\skate\resources\values\strings.properties
```

## How to Execute

### 1. Find Hardcoded Strings

Search for English text in ImGui/editor code:

```
Search in: editor/ directory
Look for: ImGui function calls with string literals
- ImGui.text("Some text")
- ImGui.button("Click Me")
- ImGui.checkbox("Enable Something", ref)
- window.title = "My Window"
- label = "Some Label"
- tooltip = "Hover text"
```

### 2. Check if Already Localized

For each string found, check if it's already in `strings.properties`:

```
grep the string in strings.properties
If found → already localized, skip
If not found → proceed to step 3
```

### 3. Add to strings.properties

Add a new key following this convention:

```properties
# Category: Descriptive label
category.descriptive_key=String Value
```

**Key naming convention:**
- Prefix with the window/component name
- Use snake_case for the key
- Be descriptive about the string's purpose

**Examples:**
```properties
# Render Graph Window
render_graph.title=Render Graph
render_graph.auto_update=Auto Update
render_graph.zoom=Zoom: {0}x

# Project Management
project.wizard.title=Create New Project
project.wizard.name_label=Project Name
project.wizard.browse_label=Browse...

# Common Actions
common.save=Save
common.cancel=Cancel
common.delete=Delete
```

### 4. Update the Code

Replace the hardcoded string with a StringManager call:

**Before:**
```kotlin
ImGui.text("Project Name")
```

**After:**
```kotlin
ImGui.text(stringManager.getString("project.wizard.name_label"))
```

### 5. Verify

Search for any remaining hardcoded strings in the same file and repeat.

## Categories Reference

Use these prefixes for consistency:

| Prefix           | Used For                           |
|------------------|------------------------------------|
| `common.`        | Shared actions (save, cancel, etc.)|
| `editor.`        | General editor UI                  |
| `menu.`          | Menu bar items                     |
| `toolbar.`       | Viewport toolbar                   |
| `hierarchy.`     | Scene hierarchy window             |
| `properties.`    | Properties window                  |
| `asset_browser.` | Asset browser window               |
| `render_graph.`  | Render graph window                |
| `project.`       | Project management                 |
| `settings.`      | Settings windows                   |
| `search.`        | Search everywhere                  |
| `gizmo.`         | Gizmo-related strings              |
| `physics.`       | Physics/debug strings              |

## Commands to Avoid

- Do NOT use English text directly in any ImGui call
- Do NOT create keys without a descriptive prefix
- Do NOT duplicate existing keys
- Do NOT modify the strings.properties file format (key=value, no spaces around =)
