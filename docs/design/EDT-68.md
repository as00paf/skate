# EDT-68 — Help window with Markdown articles

Status: **proposal, awaiting the human's decisions** · Owner: skate-editor · Deps: EDT-53 · Priority: P1

## Goal

Replace the current help popover with a real **Help window**:

- Every window's `?` button (and the tab menu's Help item) opens the Help window on that window's topic.
- The Help window is a normal dockable editor window with a topic list (table of contents) on the left and the
  selected article on the right.
- The articles are **Markdown files** under `assets/help/**`, written by EDT-53.
- A **Help** menu item opens the Help window with the topic list, independently of any `?` button.
- No more "bigger tooltip": the popover implementation is removed from the window standard.

## Proposed design

### Window

| Part | Proposal |
|---|---|
| Kind | `EditorWindow("window.help")`, dockable, listed in View > Windows, saved in the layout |
| Header | Standard header strip, icon `QUESTION_CIRCLE`, no tools on the left |
| Default state | Closed; first open docks it at the bottom next to the Console |
| Layout | Two panes: a searchable topic list on the left (~220 px) and the article on the right, both scrollable |
| Opening | `HelpWindow.openTopic(id)` selects the topic and focuses the window; `open()` with no topic shows the list |
| Related windows | The article may link to another topic (`[Console](console.md)`) and to a window (the article's front matter can name related windows); links select the topic or open the window |
| Locale | Re-reads the article when the editor language changes |

### Articles on disk

```
assets/help/
  en/
    getting-started.md
    shortcuts.md
    hierarchy.md
    properties.md
    viewport.md
    project.md
    asset-browser.md
    console.md
    profiler.md
    command-history.md
    render-graph.md
    shell.md
    script-editor.md
    audio-inspector.md
    keybindings.md
    editor-settings.md
    project-settings.md
    export.md
    project-wizard.md
    project-switcher.md
    search.md
    version-control.md
    ...
  fr/
    (the same file names)
```

- Topic id = the window's help id (`window.console` -> `console`), so the existing help ids survive.
- General topics (`getting-started`, `shortcuts`) are not tied to a window.
- English is the fallback: a missing localized file opens the English article and shows a small "not translated"
  note. Missing English is a test failure, not a placeholder popover.
- Article title = the first `#` heading; fallback to the window's translated title.

### Markdown

A small in-house parser and ImGui renderer in `editor/help/`, no new dependency:

- `#`, `##`, `###` headings
- paragraphs and hard line breaks
- unordered and ordered lists
- **bold**, *italic*, `inline code`
- fenced code blocks (monospace, no highlighting)
- links: `[text](topic.md)` selects another help topic; `https://...` opens the browser
- horizontal rules and block quotes
- images and tables are out of scope for v1 (the parser keeps unknown lines as plain text)

The parser returns a list of blocks and is unit-tested without ImGui; the renderer only maps blocks to ImGui calls.

### Routing and menu

- `EditorWindow.help` no longer shows a popover. `WindowHeader.helpButton` and `TabMenuItem.HELP` call
  `HelpWindow.openTopic(helpId)`.
- `HelpPopover` and its strings are deleted.
- `HelpMenuBuilder` gets a first item **Help Window…** (opens with the topic list). **Getting started** opens the
  same window on `getting-started`; **Keyboard shortcuts** opens it on `shortcuts`; **About Skate** stays the modal.
- `WindowStandardGuardTest` changes its help check from `help.<id>.what/how` strings to
  `assets/help/en/<id>.md` (and FR) coverage.

### Tests

- `HelpMarkdownTest`: headings, lists, emphasis, code, links, unknown lines.
- `HelpArticleCoverageTest`: every registered window has an EN and FR article; every article parses; every
  internal link points to an existing topic.
- `HelpRoutingTest`: `?` and the tab menu select the right topic; the Help menu opens the window with no selection.
- `HelpWindowModelTest`: topic list order, search filter, locale fallback.

## Files

- new `editor/help/HelpTopic.kt`, `HelpArticle.kt`, `HelpArticleLoader.kt`, `HelpMarkdown.kt`, `HelpMarkdownRenderer.kt`
- new `editor/ui/windows/HelpWindow.kt`
- `editor/imgui/EditorWindow.kt`, `WindowHeader.kt`, `WindowRegistry.kt`, `ui/menus/HelpMenuBuilder.kt`
- delete `editor/imgui/HelpPopover.kt`
- `assets/help/en/**`, `assets/help/fr/**` (EDT-53)
- `assets/strings/editor/core*.properties`: remove the popover section keys, add only the window title/topic labels
- tests listed above; update `WindowStandardGuardTest`

## Open questions (asked one by one)

1. **Help window kind:** dockable window (recommended) or floating dialog?
2. **Topic granularity:** one article per window plus general topics (recommended), or one large manual with sections?
3. **Localized articles:** `assets/help/<locale>/<topic>.md` with English fallback (recommended), or English only?
4. **Markdown implementation:** small in-house subset (recommended), or add a Markdown library (e.g. commonmark-java)?
5. **Repeated `?`:** reuse the single Help window and switch topic (recommended), or open a new window per topic?
6. **Help menu:** add **Help Window…** as the first item of the existing Help menu, with Getting started and
   Keyboard shortcuts opening the same window on their topics (recommended)? Confirm the label.
7. **Article ownership:** ship the articles with the editor under `assets/help` (recommended); project-specific
   overrides can come later. Confirm.

## Decisions (recorded from the human)

_To be filled in as the questions are answered._
