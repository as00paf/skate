# Generic inspector fields (EDT-36)

Components without a custom inspector are drawn by `reflectedImgui` from their declared fields:
numbers, booleans, enums, vectors and **Strings**.

## String fields
- Only `String` fields the scene file saves are shown, i.e. elements of the component's kotlinx serializer.
  `kotlinx.serialization.Transient` runtime state (e.g. `PlayerController.state`) is hidden.
- Edits apply when the field loses focus or Enter is pressed, as one undoable command
  (`ComponentFieldAction.Set` → `SetComponentFieldCommand`). Emptying a nullable field that was null keeps it null.

## Object references: `@GameObjectRef`
A String field that holds the **name** of a GameObject is marked with the engine annotation
`com.pafoid.skate.engine.ecs.components.GameObjectRef`:

```kotlin
class FollowCameraComponent(@GameObjectRef var targetName: String = "Skater", ...)
```

The inspector then adds an object picker next to the text field:
- a dropdown of the scene's object names (any depth, hierarchy order, duplicates listed once);
- a drop target: drag an object from the Scene Hierarchy onto the text field to use its name;
- a warning when no object in the scene has that name.

We use an annotation, not a naming convention (`*Name`, `target*`), so the intent is explicit, greppable and
cannot match unrelated fields (e.g. `Component.name`). Runtime code resolves the name with
`Scene.findFirstByName(name, exclude)`, which searches the whole hierarchy; names are not unique, so the first match
in hierarchy order wins. Annotated today: `FollowCameraComponent.targetName`, `EnvironmentComponent.fogCycleButton`.

`reflectedImgui(skip = ...)` hides fields drawn by a custom inspector (e.g. `ParticleEmitter.texturePath`).
