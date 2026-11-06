# AST-23: Asset Browser tabs no longer freeze the editor

1. Open a project whose `Assets/` holds many textures (the default project, plus a few large 4k images copied in).
2. Open the Asset Browser and click **Textures**: the tab appears immediately with placeholder tiles, which fill in with thumbnails over the next frames. The viewport keeps rendering smoothly (no freeze).
3. Switch to **Sounds**: the list shows at once; durations show `...` and fill in a moment later (the long music file too). Play/Stop still works.
4. Switch to **Prefabs**: placeholder icons appear first, then thumbnails fill in one by one with their surface materials.
5. Switch back and forth between the tabs: nothing reloads, and switching is instant.
6. Drag a texture onto an object and right-click > Properties on a texture: the size shown is the image's real size (e.g. 2048x2048).
7. Change the Prefabs **Material** combo: the affected thumbnails re-render a few per frame with no freeze.
