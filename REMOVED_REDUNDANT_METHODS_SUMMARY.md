# Removal of Redundant setSelectedGameObject/getSelectedGameObject Methods

## Objective
Remove redundant setSelectedGameObject and getSelectedGameObject methods from the Scene class since they were just delegating to the GameObjectManager.

## Changes Made

### 1. Removed Methods from Scene Class
- Removed `setSelectedGameObject()` method from Scene.kt
- Removed `getSelectedGameObject()` method from Scene.kt

### 2. Updated All References
Updated all classes that were using the Scene's methods to directly use the GameObjectManager:

- **SceneManager.kt**: 3 references updated
- **GizmoSystem.kt**: 1 reference updated  
- **Gizmo.kt**: 1 reference updated
- **ImGuiLayer.kt**: 2 references updated
- **SelectionGizmo.kt**: 1 reference updated
- **MouseControls.kt**: 4 references updated
- **SceneHierarchyWindow.kt**: 3 references updated
- **PropertiesWindow.kt**: 1 reference updated
- **EditorCommands.kt**: 4 references updated (2 in CreateGameObjectCommand, 2 in DeleteGameObjectCommand)
- **BoneTreeWindow.kt**: 1 reference updated

### Example of Changes
**Before:**
```kotlin
scene.setSelectedGameObject(gameObject)
val selected = scene.getSelectedGameObject()
```

**After:**
```kotlin
scene.gameObjectManager.setSelectedGameObject(gameObject)
val selected = scene.gameObjectManager.getSelectedGameObject()
```

## Benefits Achieved

1. **Reduced Scene Class Clutter**: Removed 2 redundant methods that were just delegating functionality
2. **Clearer Architecture**: Direct access to GameObjectManager makes the architecture clearer
3. **Consistency**: All GameObject management now goes through GameObjectManager consistently
4. **Maintainability**: Fewer methods to maintain in the Scene class

## Verification
- All compilation errors resolved
- No new test failures introduced (same pre-existing failures remain)
- Same functionality preserved across the entire codebase
- Code follows project conventions

## Files Modified
- Modified: `src/main/kotlin/com/pafoid/skate/engine/scenes/Scene.kt`
- Modified: 10 other files that had references to the Scene's setSelectedGameObject/getSelectedGameObject methods