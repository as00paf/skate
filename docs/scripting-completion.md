# Script code completion (SCR-06)

Engine-side, UI-independent completion for script editors (consumed by EDT-26).
Package: `com.pafoid.skate.engine.scripting.completion`.

## Using it (editor side)

```kotlin
val service = CompletionService(hostRegistry)          // owns one daemon worker thread
val handle = service.request(CompletionRequest(path, text, caretOffset, explicit = true))
handle.result.thenAccept { items -> /* hop back to the UI thread and show the popup */ }
handle.cancel()                                        // also happens automatically when a newer request arrives
```

- Never call a `CompletionProvider` on the render/UI thread; use the service.
- `Completion.replaceStart/replaceEnd` give the range to replace, `insertText` the new text and `cursorOffset` the caret
  position inside it (snippets and `method()` put the caret between the parentheses when there are parameters).
- `explicit = false` (auto-popup while typing) returns nothing for an empty prefix outside member access.

## Extension point for hosts

1. Implement `CompletionProvider.complete(request)`: pure function of the request, tolerant of invalid text,
   polling `request.cancellation.throwIfCancelled()` in long loops.
2. Return it from `ScriptHost.completionProvider` (default `null` = no completion for that language).
3. `ScriptHostRegistry.completionProviderFor(path)` resolves it by file extension.

The built-in hosts (Kotlin, TypeScript/JavaScript, Lua) share `ApiCompletionProvider` configured with a
`LanguageProfile` (keywords, snippets, comment/string syntax, how lifecycle parameters and local aliases are
declared, signature rendering). A new language only needs a new profile; a language with a real IDE backend can
supply its own provider instead.

## What it completes

- Members after `.` on chains rooted at the lifecycle `ctx` parameter (whatever it is named) or at simple local aliases
  (`val e = ctx.entity`), including call segments such as `ctx.scene.find("x")`.
- Top level: context names, declared identifiers, keywords and lifecycle snippets.
- Partial identifiers: case-sensitive prefix, case-insensitive prefix, camel humps (`hC` -> `hasComponent`), substring.
- Nothing inside strings and comments; never throws on malformed text.
- The API index is built by reflection over `scripting/api`, so new API members appear automatically; docs are in
  `Docs` (`ApiIndex.kt`), keyed `Type.member`.

Known limits: results of generic lookups (`getComponent(...)`), indexers and arbitrary expressions are not resolved;
TypeScript and Lua offer the script API only (no type inference of user code).
