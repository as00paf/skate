# REN-06 — Light buffer: test steps

1. `.\gradlew.bat run`; File > New Project `LightTest`; open it.
2. Add a large plane and a few cubes; add 3 point lights and 1 spot light near them. Each lights the nearby surfaces; move a light and the lit area follows.
3. Duplicate a point light ~60 times in a grid (total > 64 lights). Editor stays smooth; objects are lit by the nearest/strongest lights only (max 64 uploaded, 16 per object) and nothing crashes or flickers.
4. Spot light: its cone lights a spot on the plane (cone angles are degrees in the inspector); narrowing the outer angle shrinks it.
5. Run `.\gradlew.bat :test --tests "com.pafoid.skate.engine.render.lighting.*"`.
