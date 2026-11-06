# REN-26: Ambient light in the Environment; default Sun linked to day/night

1. Create a new project. In MainScene's Hierarchy there is a **Sun** object; the scene has no Ambient Light component.
2. Select the scene and open its **Environment** component: it shows **Ambient Color** and **Ambient Intensity**. With the Day/Night cycle's Auto Ambient off, changing them brightens or darkens the shadowed sides of objects.
3. In the viewport, nothing is drawn at the origin (0,0,0). The Sun's light icon and direction arrow are drawn at the Sun object (10 m above the origin); select the Sun to highlight them.
4. In the scene's **Day Night Cycle**, drag **Time Of Day** (e.g. 8, 12, 17): the Sun's arrow, the shadows and the sky light change while editing. At night (e.g. 23) the sun is off and the scene turns dark blue.
5. Press **Play**: time advances and the sun and shadows move slowly.
6. Open the **FeatureShowcase** scene: it also has a Sun object; the Shadows area shows shadows falling back-left.
7. Open a project saved before this change: it opens, and an error dialog says MainScene "was saved by an older Skate version ... Recreate it" (no crash). Create a new project to get the current scenes.
8. Add a light with Create > Directional Light: it appears as its own object with an arrow (it used to fail silently). Only the first directional light in the hierarchy (the Sun) lights the scene.
