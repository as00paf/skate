# EDT-56: loading screens for long operations

Creating or opening a project, opening a scene, and exporting now show a modal with a progress bar, the current step and the elapsed time. File work runs in the background, so the editor keeps drawing. Operations shorter than 0.2 s show no modal.

1. Project wizard: create a new project. The "Creating project" modal shows the project name, "Copying the engine assets" and then "Creating the default scenes". The bar fills, and the modal closes with the new project open.
2. While step 1 runs, move the mouse over the editor. The window keeps redrawing (no "not responding"), and clicks on the editor behind the modal do nothing.
3. Hamburger menu > recent project (or Switch Project): open a large project (MainScene + Feature Showcase). "Opening project" shows "Reading the project and its scenes" and then "Opening the scenes", and the project opens as before.
4. Open a project file that is broken or missing (rename it on disk first). The loading modal closes and the usual error dialog explains why. The editor stays usable.
5. Project window: double-click a `.scene` file. For a big scene, "Opening scene" appears briefly and the scene opens in a new tab. For a small one, no modal flashes.
6. File > Export: start an export. The "Exporting" modal follows the export steps (Collecting, Packing with the file name, …) and closes at the end. The export wizard then shows its result as before.
7. Ask for a second operation while one runs (for example, double-click a scene during an export). It is ignored with a warning in the Console, and nothing breaks.
8. Switch the editor to French. Every modal title and step is translated.
