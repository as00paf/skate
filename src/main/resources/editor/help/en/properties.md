# Properties

The Properties window is the inspector of the selected object. If the Scene Hierarchy tells you what exists, Properties tells you what it is made of and lets you change it.

## What it is

An object is built from **components**. The Transform component is always present: it holds the position, rotation and scale of the object. Other components add abilities:

- a **Mesh** component makes the object visible;
- a **Light** component turns it into a lamp;
- a **Collider** and a **Rigid Body** make it solid and let physics move it;
- an **Audio Source** makes it play a sound;
- a **Script** component runs your own code.

Properties shows the name of the object, its enabled state, and one foldable section per component. Each field has a tooltip that explains what it does. Values can be typed, dragged or reset to their default.

## How to use it

1. Select an object in the [Scene Hierarchy](hierarchy.md) or click it in the [Game Viewport](game_viewport.md).
2. Expand the **Transform** section to set the position, rotation and scale. You can also use the move, rotate and scale gizmos in the viewport.
3. Click **Add Component** to attach a new component. Type in the search field of the popup to find one quickly.
4. Change a value and look at the viewport: the result appears immediately.
5. Use the trash button on a component header to remove it. Removing a component can be undone with **Ctrl+Z**.
6. Right-click a component header for **Copy**, **Reset** and **Remove**.
7. If nothing is selected, the window shows a short message instead of an empty list.

Some components are drawn by their own window. For example, audio sources also appear in the [Audio Inspector](audio_inspector.md), and materials and lights have detailed sections here in Properties.

## Related windows

- [Scene Hierarchy](hierarchy.md): choose the object to inspect.
- [Game Viewport](game_viewport.md): see the result in 3D.
- [Asset Browser](asset_browser.md): drag assets such as models, textures and sounds onto component fields.
- [Audio Inspector](audio_inspector.md): the sound side of the selected object and the project mixer.
