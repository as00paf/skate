# String loading (GUI-08)

## Tables

- Strings live in `.properties` files read as UTF-8, one table per area (EDT-37):
  - `assets/strings/editor/<area>.properties` for the editor.
  - `assets/strings/game/<area>.properties` for runtime UI (HUD, pause menu, showcase).
- A translation sits next to its table as `<area>_<lang>.properties` (`core_fr.properties`). Area names must not end
  in `_xx`, which is reserved for the language suffix.
- A key lives in exactly one area table, in both EN and FR. `StringTablesTest` enforces this.

## Two managers

| Manager                | Used by                                        | Locale from                               |
|------------------------|------------------------------------------------|-------------------------------------------|
| `Engine.stringManager` | Editor UI (ImGui), commands, editor messages   | `EditorSettings.language` (`editor.config`) |
| `Engine.gameStrings`   | Runtime UI (`UiRenderSystem`, pause menu, HUD) | `GameSettings.locale` (`game.settings.json`, GUI-10) |

A game action never changes the editor's locale, and the editor's language never changes the game's.

## Sources and loading

A `StringSource` is either a set of tables on disk (`StringSource.files`) or tables already in memory
(`StringSource.memory`, used for the tables packed into `game.bin`, AST-07).

| Where          | Calls                                                                                              |
|----------------|----------------------------------------------------------------------------------------------------|
| Editor start   | `stringManager.loadStrings(EDITOR_STRINGS)`, then `setLocale(editor language)`. Also `gameStrings.addStrings(GAME_STRINGS)`. |
| Project opened | `gameLocale.attach(<project>/game.settings.json)` sets the game locale.                            |
| Exported game  | `gameStrings.addStrings(PackedStrings.source(<pack tables>))` (only the pack, never the working directory), then `gameLocale.attach(<game.bin dir>/game.settings.json)`. |

## Layering (one rule, `StringTables.layer`)

The sources are ordered: the `loadStrings` base first, then each `addStrings` source in the order added (a source
with the same label replaces the earlier one in place). For each source, its default tables are applied first, then
its translation for the current language. Later layers win.

So:
- A key missing from a translation shows its default (English) text: FR falls back to EN.
- A regional locale (`fr-CA`, `fr_CA`) uses the `fr` tables. A language with no tables (`de`) shows the defaults,
  and `setLocale` returns false.
- The result is the same whatever order `loadStrings`, `addStrings` and `setLocale` are called in.

## Threading (GUI-11)

- **Reloads** (`loadStrings`, `addStrings`, `setLocale`) may run on any thread; the editor loads on the IO thread.
  They are serialized on the manager's monitor. Each one reads the tables, builds a complete new immutable snapshot
  (a plain `HashMap` of the table plus its locale and its "already reported" sets), and publishes it with one
  `AtomicReference` swap.
- **Lookups** (`getString`, `hasString`, `getQuantityString`, `locale`) take one volatile read of the snapshot and
  never lock. They see either the old table or the new one, never a mix. A lookup of a defined key allocates
  nothing. A missing key allocates its `!!key!!` placeholder once per snapshot; formatting allocates as
  `String.format` does.
- Locale listeners run after the swap, outside the lock.

## Lookups

- `getString(key)` returns `!!key!!` when the key is missing. The miss is logged once per key until the next rebuild,
  not every frame, tagged with the manager's source (`editor` or `game`).
- `getString(key, args)` / `getQuantityString`: if a format does not match its arguments, the raw text shows and is
  logged once. It never throws during rendering.
- `hasString(key)` checks without logging (used by `CommandNames`).
- `getQuantityString(key, n)` picks `key.zero` (for 0, when defined), then the locale's plural category, then
  `key.other`.
