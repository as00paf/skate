# SkateSim Editor UI - Comprehensive Refactoring Plan

**Created:** March 31, 2026  
**Status:** Approved for Implementation  
**Total Estimated Effort:** 45-60 hours

---

## Executive Summary

This plan addresses all critical and high-priority issues identified in the ImGui/Editor UI code review. The refactoring is **incremental**, **testable**, and **leverages existing infrastructure** (EventSystem, JobSystem, Command pattern).

### Key Goals
1. Eliminate God classes (GameViewWindow, EditorMenuBar, ImGuiLayer)
2. Implement proper dependency injection
3. Leverage EventSystem for decoupled communication
4. Complete Command pattern implementations
5. Add window lifecycle management
6. Eliminate per-frame allocations
7. Reorganize package structure for maintainability

---

## Architecture Overview

### Current State (Problems)
```
ImGuiLayer (312 lines)
  ↓ Direct instantiation
GameViewWindow (776 lines) ← God Class
EditorMenuBar (308 lines) ← 13 constructor params
SceneHierarchyWindow (395 lines)
  ↓ Direct service calls
UndoRedoManager, SceneManager, etc.
```

### Target State (Solution)
```
ImGuiLayer (150 lines)
  ↓ DI via WindowManager
WindowRegistry
  ↓ Events
EventSystem (existing) ← Already available!
  ↓ ViewModels
ViewModel Layer (new)
  ↓ Split components
Focused Components (<200 lines each)
```

---

## Phase 1: Foundation (8-10 hours)

### Task 1.1: Create Window Lifecycle Interface
**File:** `src/main/kotlin/com/pafoid/skate/editor/ui/interfaces/IWindowLifecycle.kt`  
**Effort:** 1 hour

**Action:**
```kotlin
package com.pafoid.skate.editor.ui.interfaces

import com.pafoid.skate.engine.ecs.Scene

/**
 * Window lifecycle interface for proper initialization and cleanup.
 */
interface IWindowLifecycle {
    /** Called when window is first created */
    fun onInit()
    
    /** Called when scene changes */
    fun onSceneChanged(oldScene: Scene?, newScene: Scene?)
    
    /** Called every frame before render */
    fun onUpdate(dt: Float)
    
    /** Render the window (ImGui immediate mode) */
    fun onRender()
    
    /** Called when window is destroyed */
    fun onDestroy()
}
```

**Acceptance Criteria:**
- [ ] Interface created with KDoc
- [ ] All existing windows implement stub methods
- [ ] ImGuiLayer calls lifecycle methods

---

### Task 1.2: Create Editor Event Definitions
**Files:** 
- `src/main/kotlin/com/pafoid/skate/editor/events/EditorEvents.kt`
- `src/main/kotlin/com/pafoid/skate/editor/events/SelectionEvents.kt`
- `src/main/kotlin/com/pafoid/skate/editor/events/SceneEvents.kt`

**Effort:** 2 hours

**Action:**
```kotlin
// SelectionEvents.kt
package com.pafoid.skate.editor.events

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.events.GameEvent

/** Base class for all selection events */
sealed class SelectionEvent(eventName: String) : GameEvent(eventName)

/** Published when a GameObject is selected */
data class GameObjectSelected(val gameObject: GameObject) : SelectionEvent("editor.gameobject_selected")

/** Published when selection is cleared */
object SelectionCleared : SelectionEvent("editor.selection_cleared")

/** Published when multiple objects are selected */
data class SelectionChanged(val selectedObjects: List<GameObject>) : SelectionEvent("editor.selection_changed")
```

```kotlin
// SceneEvents.kt
package com.pafoid.skate.editor.events

import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.events.GameEvent

sealed class SceneEvent(eventName: String) : GameEvent(eventName)

data class SceneOpened(val scene: Scene) : SceneEvent("editor.scene_opened")
data class SceneSaved(val scene: Scene) : SceneEvent("editor.scene_saved")
object SceneChanged : SceneEvent("editor.scene_changed")
```

**Acceptance Criteria:**
- [ ] All event classes created with data classes
- [ ] Event names follow `category.action` pattern
- [ ] KDoc on all classes

---

### Task 1.3: Create ViewModel Layer
**Files:**
- `src/main/kotlin/com/pafoid/skate/editor/ui/viewmodels/SelectionViewModel.kt`
- `src/main/kotlin/com/pafoid/skate/editor/ui/viewmodels/SceneViewModel.kt`

**Effort:** 3 hours

**Action:**
```kotlin
// SelectionViewModel.kt
package com.pafoid.skate.editor.ui.viewmodels

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.events.EventSystem
import com.pafoid.skate.editor.events.GameObjectSelected
import com.pafoid.skate.editor.events.SelectionCleared

/**
 * ViewModel for selection state.
 * Provides observable selection state to UI components.
 */
class SelectionViewModel(
    private val sceneManager: SceneManager,
    private val eventSystem: EventSystem
) {
    private var _selectedGameObject: GameObject? = null
    val selectedGameObject: GameObject? get() = _selectedGameObject
    
    init {
        // Subscribe to selection events
        eventSystem.subscribe<GameObjectSelected> { event ->
            _selectedGameObject = event.gameObject
        }
        
        eventSystem.subscribe<SelectionCleared> {
            _selectedGameObject = null
        }
    }
    
    fun select(gameObject: GameObject?) {
        _selectedGameObject = gameObject
        if (gameObject != null) {
            eventSystem.publish(GameObjectSelected(gameObject))
        } else {
            eventSystem.publish(SelectionCleared)
        }
    }
    
    fun clear() {
        select(null)
    }
}
```

**Acceptance Criteria:**
- [ ] SelectionViewModel created
- [ ] SceneViewModel created (provides current scene, scene list)
- [ ] ViewModels are injectable via Koin
- [ ] Unit tests for ViewModel logic

---

### Task 1.4: Create Reusable ImGui Components
**File:** `src/main/kotlin/com/pafoid/skate/editor/ui/imgui/components/EditorComponents.kt`  
**Effort:** 2 hours

**Action:**
```kotlin
package com.pafoid.skate.editor.ui.imgui.components

import imgui.ImGui
import imgui.ImVec2
import imgui.flag.ImGuiCol

/**
 * Reusable ImGui component: Icon button with tooltip
 */
object EditorComponents {
    
    /**
     * Renders a button with an icon and optional tooltip
     * @return true if clicked
     */
    fun iconButton(
        icon: String,
        size: Float = 30f,
        tooltip: String? = null,
        active: Boolean = false,
        onClick: () -> Unit
    ): Boolean {
        if (active) ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f)
        
        val clicked = ImGui.button(icon, size, size)
        
        if (active) ImGui.popStyleColor()
        if (tooltip != null && ImGui.isItemHovered()) ImGui.setTooltip(tooltip)
        
        if (clicked) onClick()
        
        return clicked
    }
    
    /**
     * Renders a labeled separator
     */
    fun labeledSeparator(label: String) {
        ImGui.separatorText(label)
    }
}
```

**Acceptance Criteria:**
- [ ] `iconButton()` function
- [ ] `labeledSeparator()` function
- [ ] `propertyField()` functions for common types
- [ ] All functions have KDoc

---

## Phase 2: EventSystem Integration (10-12 hours)

### Task 2.1: Publish Selection Events from SceneHierarchyWindow
**File:** `src/main/kotlin/com/pafoid/skate/editor/windows/SceneHierarchyWindow.kt`  
**Effort:** 2 hours

**Action:**
```kotlin
// Add EventSystem injection
class SceneHierarchyWindow : IWindowWithScene, KoinComponent {
    private val eventSystem: EventSystem by inject()
    
    // In doTreeNode(), where selection happens:
    if (ImGui.isItemClicked()) {
        sceneManager.currentScene?.setSelectedGameObject(obj)
        // NEW: Publish event instead of direct coupling
        eventSystem.publish(GameObjectSelected(obj))
    }
}
```

**Acceptance Criteria:**
- [ ] All selection points publish events
- [ ] No direct references to other windows
- [ ] Events tested in unit tests

---

### Task 2.2: Subscribe to Events in PropertiesWindow
**File:** `src/main/kotlin/com/pafoid/skate/editor/windows/PropertiesWindow.kt`  
**Effort:** 2 hours

**Action:**
```kotlin
class PropertiesWindow : IWindow, KoinComponent {
    private val eventSystem: EventSystem by inject()
    private var selectedGameObject: GameObject? = null
    
    init {
        eventSystem.subscribe<GameObjectSelected> { event ->
            selectedGameObject = event.gameObject
        }
        eventSystem.subscribe<SelectionCleared> {
            selectedGameObject = null
        }
    }
}
```

**Acceptance Criteria:**
- [ ] PropertiesWindow subscribes to selection events
- [ ] Removes direct sceneManager queries for selection
- [ ] Works with EventSystem subscription

---

### Task 2.3: Publish Scene Events from SceneManager
**File:** `src/main/kotlin/com/pafoid/skate/engine/ecs/SceneManager.kt`  
**Effort:** 2 hours

**Action:**
```kotlin
class SceneManager : KoinComponent {
    private val eventSystem: EventSystem by inject()
    
    fun openScene(scene: Scene) {
        val oldScene = currentScene
        _currentScene = scene
        eventSystem.publish(SceneOpened(scene))
        eventSystem.publish(SceneChanged)
    }
}
```

**Acceptance Criteria:**
- [ ] SceneOpened published on scene change
- [ ] SceneSaved published on save
- [ ] Windows subscribe instead of polling

---

### Task 2.4: Update GameViewWindow to Use Events
**File:** `src/main/kotlin/com/pafoid/skate/editor/windows/GameViewWindow.kt`  
**Effort:** 3 hours

**Action:**
- Replace `scene.getSelectedGameObject()` with event-based selection
- Publish `GameObjectSelected` when clicking in viewport
- Subscribe to `SceneOpened` for initialization

**Acceptance Criteria:**
- [ ] No direct selection queries
- [ ] Viewport clicks publish events
- [ ] Drag-drop uses events

---

### Task 2.5: Update EditorMenuBar to Use Events
**File:** `src/main/kotlin/com/pafoid/skate/editor/imgui/EditorMenuBar.kt`  
**Effort:** 2 hours

**Action:**
- Replace direct `sceneManager` calls with events
- Publish `SceneOpened` when creating new scene
- Publish selection events for copy/cut/paste

**Acceptance Criteria:**
- [ ] Menu actions publish events
- [ ] No direct window manipulation

---

## Phase 3: GameViewWindow Refactoring (12-15 hours)

### Task 3.1: Extract ViewportRenderer
**New File:** `src/main/kotlin/com/pafoid/skate/editor/ui/imgui/windows/components/ViewportRenderer.kt`  
**Effort:** 3 hours

**Action:**
```kotlin
class ViewportRenderer(
    private val renderer: Renderer,
    private val sceneManager: SceneManager
) {
    fun render(imageSizeX: Float, imageSizeY: Float) {
        val texId = renderer.frameBuffer.getTextureId()
        ImGui.image(texId.toLong(), imageSizeX, imageSizeY, 0f, 1f, 1f, 0f)
    }
    
    fun updateFramebuffer(width: Int, height: Int) {
        renderer.resize(width, height)
        sceneManager.currentScene?.camera?.let { camera ->
            camera.viewportWidth = width
            camera.viewportHeight = height
        }
    }
}
```

**Acceptance Criteria:**
- [ ] Image rendering extracted
- [ ] Framebuffer sync extracted
- [ ] Tested independently

---

### Task 3.2: Extract ViewportToolbar
**New File:** `src/main/kotlin/com/pafoid/skate/editor/ui/imgui/windows/components/ViewportToolbar.kt`  
**Effort:** 4 hours

**Action:**
- Extract all toolbar button rendering (lines 303-437)
- Extract tool state management
- Extract button click handlers

**Acceptance Criteria:**
- [ ] Toolbar renders independently
- [ ] Tool state encapsulated
- [ ] < 150 lines

---

### Task 3.3: Extract ViewportContextMenu
**New File:** `src/main/kotlin/com/pafoid/skate/editor/ui/imgui/windows/components/ViewportContextMenu.kt`  
**Effort:** 3 hours

**Action:**
- Extract context menu rendering (lines 481-680)
- Extract object creation logic
- Extract primitive spawning

**Acceptance Criteria:**
- [ ] Context menu extracted
- [ ] Object creation logic extracted
- [ ] Uses events for creation

---

### Task 3.4: Extract ViewportOverlays
**New File:** `src/main/kotlin/com/pafoid/skate/editor/ui/imgui/windows/components/ViewportOverlays.kt`  
**Effort:** 2 hours

**Action:**
- Extract FPS, speedometer, trick UI overlays
- Extract overlay positioning logic

**Acceptance Criteria:**
- [ ] All overlays extracted
- [ ] Positioning logic encapsulated

---

### Task 3.5: Update GameViewWindow to Use Components
**File:** `src/main/kotlin/com/pafoid/skate/editor/windows/GameViewWindow.kt`  
**Effort:** 3 hours

**Action:**
```kotlin
class GameViewWindow : IWindowWithScene, KoinComponent {
    private val viewportRenderer: ViewportRenderer by inject()
    private val viewportToolbar: ViewportToolbar by inject()
    private val viewportContextMenu: ViewportContextMenu by inject()
    private val viewportOverlays: ViewportOverlays by inject()
    
    // Now just orchestrates components
    override fun imgui(scene: Scene) {
        ImGui.begin(stringManager.getString("window.game_viewport"))
        viewportToolbar.render()
        viewportRenderer.render(imageSizeX, imageSizeY)
        viewportOverlays.render()
        viewportContextMenu.render()
        ImGui.end()
    }
}
```

**Acceptance Criteria:**
- [ ] GameViewWindow < 200 lines
- [ ] Components are injected
- [ ] All functionality preserved

---

## Phase 4: EditorMenuBar Refactoring (8-10 hours)

### Task 4.1: Extract Menu Builders
**New Files:**
- `src/main/kotlin/com/pafoid/skate/editor/ui/imgui/menus/FileMenuBuilder.kt`
- `src/main/kotlin/com/pafoid/skate/editor/ui/imgui/menus/EditMenuBuilder.kt`
- `src/main/kotlin/com/pafoid/skate/editor/ui/imgui/menus/SettingsMenuBuilder.kt`
- `src/main/kotlin/com/pafoid/skate/editor/ui/imgui/menus/ViewMenuBuilder.kt`

**Effort:** 4 hours

**Action:**
```kotlin
class FileMenuBuilder(
    private val stringManager: StringManager,
    private val levelManager: LevelManager,
    private val sceneManager: SceneManager
) {
    fun render(currentScene: Scene) {
        if (ImGui.beginMenu(stringManager.getString("menu.file"))) {
            // File menu items
            ImGui.endMenu()
        }
    }
}
```

**Acceptance Criteria:**
- [ ] Each menu < 100 lines
- [ ] Dependencies injected
- [ ] Tested independently

---

### Task 4.2: Update EditorMenuBar to Use Builders
**File:** `src/main/kotlin/com/pafoid/skate/editor/imgui/EditorMenuBar.kt`  
**Effort:** 2 hours

**Action:**
```kotlin
class EditorMenuBar(
    private val fileMenu: FileMenuBuilder,
    private val editMenu: EditMenuBuilder,
    private val settingsMenu: SettingsMenuBuilder,
    private val viewMenu: ViewMenuBuilder,
    private val windowControls: WindowControlsRenderer,
    private val stringManager: StringManager
) {
    // Reduced from 13 to 6 parameters
}
```

**Acceptance Criteria:**
- [ ] Constructor params ≤ 6
- [ ] Delegates to builders
- [ ] < 150 lines total

---

### Task 4.3: Extract WindowControlsRenderer
**New File:** `src/main/kotlin/com/pafoid/skate/editor/ui/imgui/components/WindowControlsRenderer.kt`  
**Effort:** 2 hours

**Action:**
- Extract minimize/maximize/close button rendering
- Extract search button

**Acceptance Criteria:**
- [ ] Window controls encapsulated
- [ ] < 80 lines

---

## Phase 5: Command Pattern Completion (6-8 hours)

### Task 5.1: Implement ApplyTextureCommand
**File:** `src/main/kotlin/com/pafoid/skate/editor/commands/ApplyTextureCommand.kt`  
**Effort:** 2 hours

**Action:**
```kotlin
class ApplyTextureCommand(
    private val gameObject: GameObject,
    private val oldTexturePath: String?,
    private val newTexturePath: String,
    private val resourceManager: ResourceManager
) : Command {
    override fun execute() {
        val renderComponent = gameObject.getComponent<RenderComponent>()
        renderComponent?.let { component ->
            val texture = resourceManager.loadTexture(newTexturePath)
            val texturedModel = TexturedModel(
                component.model.baseModel.mesh[0].rawModel,
                texture
            )
            component.model = texturedModel
        }
        // Publish event for UI update
        eventSystem.publish(TextureApplied(gameObject, newTexturePath))
    }
    
    override fun undo() {
        // Restore old texture
        if (oldTexturePath != null) {
            execute() // Re-apply old texture
        }
    }
    
    override fun getDisplayName(): String = "Apply Texture"
    override fun getTargetName(): String? = gameObject.name
}
```

**Acceptance Criteria:**
- [ ] Texture actually applied
- [ ] Undo restores old texture
- [ ] Event published

---

### Task 5.2: Implement ApplyAnimationCommand
**File:** `src/main/kotlin/com/pafoid/skate/editor/commands/ApplyAnimationCommand.kt`  
**Effort:** 2 hours

**Action:**
```kotlin
class ApplyAnimationCommand(
    private val gameObject: GameObject,
    private val oldAnimationPath: String?,
    private val newAnimationPath: String,
    private val resourceManager: ResourceManager
) : Command {
    override fun execute() {
        val animator = gameObject.getComponent<Animator>()
        animator?.let { anim ->
            val animation = resourceManager.getAnimation(newAnimationPath)
            animation?.let { anim.addAnimation(it) }
        }
        eventSystem.publish(AnimationApplied(gameObject, newAnimationPath))
    }
    
    override fun undo() {
        // Remove animation or restore old
    }
    
    override fun getDisplayName(): String = "Apply Animation"
    override fun getTargetName(): String? = gameObject.name
}
```

**Acceptance Criteria:**
- [ ] Animation actually applied
- [ ] Undo works
- [ ] Event published

---

### Task 5.3: Add Event Publishing to All Commands
**Effort:** 2 hours

**Action:**
- Add `CommandExecuted` event to all commands
- Include command name, target, timestamp

**Acceptance Criteria:**
- [ ] All commands publish events
- [ ] Command history window uses events

---

## Phase 6: Dependency Injection Integration (5-7 hours)

### Task 6.1: Create WindowRegistry
**New File:** `src/main/kotlin/com/pafoid/skate/editor/ui/WindowRegistry.kt`  
**Effort:** 2 hours

**Action:**
```kotlin
class WindowRegistry @Inject constructor(
    private val hierarchyWindow: SceneHierarchyWindow,
    private val propertiesWindow: PropertiesWindow,
    private val gameViewWindow: GameViewWindow,
    // ... other windows
) {
    val windows: List<EditorWindow> = listOf(
        EditorWindow("window.hierarchy", hierarchyWindow),
        EditorWindow("window.properties", propertiesWindow),
        // ...
    )
    
    fun getWindow(key: String): IWindowLifecycle {
        return windows.find { it.nameKey == key }?.instance
            ?: throw IllegalArgumentException("Unknown window: $key")
    }
}
```

**Acceptance Criteria:**
- [ ] All windows registered
- [ ] Windows created via DI
- [ ] Koin module updated

---

### Task 6.2: Update ImGuiLayer to Use WindowRegistry
**File:** `src/main/kotlin/com/pafoid/skate/editor/imgui/ImGuiLayer.kt`  
**Effort:** 2 hours

**Action:**
```kotlin
class ImGuiLayer(
    private val windowRegistry: WindowRegistry,
    private val viewModelFactory: ViewModelFactory,
    // ... reduced params
) {
    // Remove direct instantiation
    // Use windowRegistry.windows instead
}
```

**Acceptance Criteria:**
- [ ] No direct window instantiation
- [ ] Windows from registry
- [ ] < 200 lines

---

### Task 6.3: Update Koin Modules
**File:** `src/main/kotlin/com/pafoid/skate/editor/di/EditorModule.kt` (new)  
**Effort:** 2 hours

**Action:**
```kotlin
val editorModule = module {
    // ViewModels
    factory { SelectionViewModel(get(), get()) }
    factory { SceneViewModel(get()) }
    
    // Window components
    factory { ViewportRenderer(get(), get()) }
    factory { ViewportToolbar(get(), get()) }
    factory { ViewportContextMenu(get(), get()) }
    
    // Windows
    factory { SceneHierarchyWindow() }
    factory { PropertiesWindow() }
    factory { GameViewWindow() }
    
    // Registry
    single { WindowRegistry(get(), get(), get(), get(), get()) }
}
```

**Acceptance Criteria:**
- [ ] All dependencies declared
- [ ] Proper scopes (single vs factory)
- [ ] Tests pass

---

## Phase 7: Performance Optimization (4-6 hours)

### Task 7.1: Cache GameObject Lookups
**File:** `src/main/kotlin/com/pafoid/skate/editor/windows/GameViewWindow.kt`  
**Effort:** 1 hour

**Action:**
```kotlin
class GameViewWindow {
    private var cachedSkateboard: GameObject? = null
    private var lastCacheTime = 0f
    
    private fun getSkateboard(scene: Scene, dt: Float): GameObject? {
        // Cache for 0.5 seconds
        if (cachedSkateboard == null || dt - lastCacheTime > 0.5f) {
            cachedSkateboard = scene.gameObjectManager.gameObjects.find { 
                it.name == "Skateboard" 
            }
            lastCacheTime = dt
        }
        return cachedSkateboard
    }
}
```

**Acceptance Criteria:**
- [ ] No per-frame linear searches
- [ ] Cache invalidation logic
- [ ] Measurable performance improvement

---

### Task 7.2: Eliminate toList() Calls
**Effort:** 2 hours

**Action:**
```kotlin
// Before (allocates new list every frame)
val gameObjects = scene.gameObjectManager.gameObjects.toList()
gameObjects.forEach { ... }

// After (iterates directly)
scene.gameObjectManager.gameObjects.forEach { ... }
```

**Acceptance Criteria:**
- [ ] No unnecessary allocations
- [ ] Profile shows reduced GC

---

### Task 7.3: Reuse ImVec2 and Vector3f Objects
**Effort:** 2 hours

**Action:**
```kotlin
class GameViewWindow {
    // Reusable buffer - allocate once
    private val tempVec2 = ImVec2()
    private val tempVec3 = Vector3f()
    
    fun render() {
        // Reuse instead of allocate
        ImGui.getContentRegionAvail(tempVec2)
        // Use tempVec2
    }
}
```

**Acceptance Criteria:**
- [ ] Temp buffers reused
- [ ] < 5 allocations per frame

---

## Phase 8: Cleanup & Documentation (4-5 hours)

### Task 8.1: Remove Legacy Files
**Effort:** 1 hour

**Action:**
- Remove old unused classes
- Clean up imports

**Acceptance Criteria:**
- [ ] No dead code
- [ ] Clean imports

---

### Task 8.2: Update Package Structure
**Effort:** 2 hours

**Action:**
```
editor/
├── ui/
│   ├── imgui/
│   │   ├── components/      # NEW
│   │   ├── windows/         # NEW - split components
│   │   └── menus/           # NEW - menu builders
│   ├── viewmodels/          # NEW
│   └── interfaces/          # NEW
├── events/                  # NEW - editor events
├── commands/                # REORGANIZED
└── systems/                 # REDUCED
```

**Acceptance Criteria:**
- [ ] All files moved
- [ ] Imports updated
- [ ] Build succeeds

---

### Task 8.3: Add KDoc Coverage
**Effort:** 2 hours

**Action:**
- Add KDoc to all public APIs
- Add usage examples

**Acceptance Criteria:**
- [ ] 80% KDoc coverage
- [ ] Examples for complex APIs

---

## Migration Guide

### For Window Authors

**Before:**
```kotlin
class MyWindow : IWindow, KoinComponent {
    private val sceneManager: SceneManager by inject()
    
    override fun imgui(pOpen: ImBoolean?) {
        val selected = sceneManager.currentScene?.getSelectedGameObject()
        // Render UI
    }
}
```

**After:**
```kotlin
class MyWindow @Inject constructor(
    private val selectionViewModel: SelectionViewModel
) : IWindowLifecycle {
    
    override fun onInit() { }
    
    override fun onRender() {
        val selected = selectionViewModel.selectedGameObject
        // Render UI
    }
    
    override fun onDestroy() { }
}
```

---

### For Command Authors

**Before:**
```kotlin
class ApplyTextureCommand(...) : Command {
    override fun execute() {
        // Empty - TODO
    }
    override fun undo() {
        // Empty - TODO
    }
}
```

**After:**
```kotlin
class ApplyTextureCommand(...) : Command {
    override fun execute() {
        // Actual implementation
        eventSystem.publish(TextureApplied(gameObject, path))
    }
    override fun undo() {
        // Actual undo
    }
    override fun getDisplayName(): String = "Apply Texture"
}
```

---

## Success Metrics

| Metric | Before | Target | Status |
|--------|--------|--------|--------|
| GameViewWindow lines | 776 | < 200 | ☐ |
| EditorMenuBar lines | 308 | < 200 | ☐ |
| ImGuiLayer lines | 312 | < 200 | ☐ |
| Constructor params (MenuBar) | 13 | ≤ 6 | ☐ |
| Per-frame allocations | ~50 | < 5 | ☐ |
| Empty Commands | 2 | 0 | ☐ |
| KoinComponent usage | 15+ | 0 | ☐ |
| Event decoupling | 0% | 80% | ☐ |
| KDoc coverage | 30% | 80% | ☐ |

---

## Risk Mitigation

| Risk | Impact | Mitigation |
|------|--------|------------|
| Breaking existing UI | High | Incremental phases, test each phase |
| Performance regression | Medium | Profile after each phase |
| Lost functionality | Medium | Feature parity tests |
| Team productivity dip | Low | Pair programming, documentation |

---

## Approval

**Tech Lead:** _________________ Date: _________  
**Project Manager:** _________________ Date: _________  
**Lead Developer:** _________________ Date: _________

---

## Appendix: File Checklist

### New Files to Create (23 total)
- [ ] `IWindowLifecycle.kt`
- [ ] `EditorEvents.kt`
- [ ] `SelectionEvents.kt`
- [ ] `SceneEvents.kt`
- [ ] `SelectionViewModel.kt`
- [ ] `SceneViewModel.kt`
- [ ] `EditorComponents.kt`
- [ ] `ViewportRenderer.kt`
- [ ] `ViewportToolbar.kt`
- [ ] `ViewportContextMenu.kt`
- [ ] `ViewportOverlays.kt`
- [ ] `FileMenuBuilder.kt`
- [ ] `EditMenuBuilder.kt`
- [ ] `SettingsMenuBuilder.kt`
- [ ] `ViewMenuBuilder.kt`
- [ ] `WindowControlsRenderer.kt`
- [ ] `ApplyTextureCommand.kt` (rewrite)
- [ ] `ApplyAnimationCommand.kt` (rewrite)
- [ ] `WindowRegistry.kt`
- [ ] `ViewModelFactory.kt`
- [ ] `EditorModule.kt`
- [ ] `CommandExecutedEvent.kt`
- [ ] `TextureAppliedEvent.kt`

### Files to Modify (15 total)
- [ ] `ImGuiLayer.kt`
- [ ] `GameViewWindow.kt`
- [ ] `EditorMenuBar.kt`
- [ ] `SceneHierarchyWindow.kt`
- [ ] `PropertiesWindow.kt`
- [ ] `SceneManager.kt`
- [ ] `EditorCommands.kt`
- [ ] `KoinModule.kt`
- [ ] All other windows (10 files)

---

**End of Refactoring Plan**
