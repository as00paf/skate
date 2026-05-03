---
name: event-driven-pattern
description: >
  Ensure UI actions follow the event-driven pattern:
  UI publishes Event → EventSystem delivers → ActionHandler executes → Command → UndoRedoManager tracks.
  Use when implementing UI actions, context menus, toolbars, or any user-triggered operation.
---

# Event-Driven Pattern Skill

## When to Use

- Implementing new UI actions (buttons, context menus, keyboard shortcuts)
- Refactoring existing callback-based UI code
- Adding new editor operations

## The Pattern

All user-triggered operations follow this flow:

```
UI Component → publishes Event → EventSystem → ActionHandler subscribes → executes Command → UndoRedoManager
```

## Steps

### 1. Define Events (One file per event group)

Create/update a sealed class file in `engine/events/`:

```kotlin
// MyAction.kt
package com.pafoid.skate.engine.events

sealed class MyAction(eventName: String) : Event(eventName)

data class MyDoSomething(val param: String, val scene: Scene) : MyAction("my.do_something")
object MyReset : MyAction("my.reset")
```

**Rules:**

- Event subclasses are **top-level**, not nested inside the sealed class
- One file per event group (e.g., `SceneAction.kt`, `ViewportAction.kt`)
- Each event has a unique event name string

### 2. Create/Update ActionHandler

Create or update `*ActionHandler` in `editor/ui/handlers/`:

```kotlin
class MyActionHandler : KoinComponent {
    private val eventSystem: EventSystem by inject()
    private val undoRedoManager: UndoRedoManager by inject()

    fun init() {
        eventSystem.subscribe<MyDoSomething> { event ->
            val command = MyDoSomethingCommand(event.param, event.scene)
            undoRedoManager.executeCommand(command)
        }
        eventSystem.subscribe<MyReset> {
            // Handle reset
        }
    }
}
```

### 3. Create Commands (One per file)

Create command in `editor/commands/`:

```kotlin
class MyDoSomethingCommand(
    private val param: String,
    private val scene: Scene
) : Command {
    override fun execute() { /* do work */ }
    override fun undo() { /* reverse work */ }
    override fun getDisplayName(): String = "Do Something"
    override fun getTargetName(): String? = param
}
```

### 4. Register Handler in KoinModule

```kotlin
single { MyActionHandler().also { it.init() } }
```

### 5. UI Publishes Events

```kotlin
class MyWindow(
    private val eventSystem: EventSystem
) {
    fun onButtonClicked() {
        eventSystem.publish(MyDoSomething("value", currentScene))
    }
}
```

## Anti-Patterns (NEVER Do These)

❌ **Callbacks**: `interface MyCallbacks { fun onDoSomething() }`
❌ **Direct method calls**: `myService.doSomething()`
❌ **Nested event classes**: `sealed class MyAction { data class DoSomething(...) : MyAction() }`
❌ **Commands in same file**: Multiple command classes in one `.kt` file
❌ **Direct state mutations**: `scene.name = newName` (use command instead)

## Checklist

- [ ] Events defined in sealed class, top-level subclasses, own file
- [ ] ActionHandler created/updated with event subscriptions
- [ ] Commands in separate files, one per file
- [ ] Handler registered in KoinModule with `.also { it.init() }`
- [ ] UI publishes events instead of calling methods
- [ ] No callback interfaces left behind
- [ ] No dead code from old pattern
