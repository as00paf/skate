# AST-24 manual test steps (OBJ files without normals)

Before this fix, an OBJ without `vn` lines loaded through the file path (the Project window, the async loader) had no normals and rendered black or flat, because the shader got a NaN normal.

1. Save this as `flat_quad.obj` in a project's Assets folder (no `vn` lines):
   ```
   v -1 0 -1
   v 1 0 -1
   v 1 0 1
   v -1 0 1
   f 1 4 3
   f 1 3 2
   ```
2. Start the editor (`.\gradlew.bat run`), open the project, and drag `flat_quad.obj` into the scene. It is lit like the floor: it brightens and darkens when you move the scene's directional light, and it is not black.
3. Make a copy whose normals are all zero: add the line `vn 0 0 0` and change the faces to `f 1//1 4//1 3//1` and `f 1//1 3//1 2//1`. Drag it in: it lights the same as the first quad.
4. Any OBJ with real normals (for example `assets/obj/sphere.obj` from a Create > Sphere) looks the same as before.
