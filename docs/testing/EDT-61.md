# EDT-61: Search Anywhere finds asset files

1. Open a project that contains `james.glb` (e.g. under `Assets/models`).
2. Open Search Anywhere and type `james`: `james.glb` appears under Models, with its project-relative path.
3. Press Enter (or click it): the Project window comes to the front, its folders open down to `james.glb`, the file is selected and scrolled into view.
4. Type a filter in the Project window that hides the file (e.g. `png`), then reveal it again from Search Anywhere: the filter is cleared and the file is shown.
5. Search for a texture, a sound, an `.fbx` animation, a `.prefab` and a `.kts` script by part of their file name: each is found in its category (Textures, Sounds, Animations, Prefabs, Scripts) and opening it reveals it the same way.
6. With the Files filter on in Search Anywhere, the same results are listed.
7. Files inside `build/` or `.git/` are not listed.
