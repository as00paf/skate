# REN-07: Shadows overhaul (human test steps)

Shadow quality is read from `ShadowSettings.quality`. Until the editor wires `GraphicsSettings.shadowQuality` to it, pick the tier at launch with the JVM property `skate.shadowQuality` (`LOW`, `MEDIUM` default, `HIGH`). In PowerShell: `$env:JAVA_TOOL_OPTIONS="-Dskate.shadowQuality=LOW"`. Clear it with `Remove-Item Env:JAVA_TOOL_OPTIONS`.

1. With no property set (MEDIUM), run `.\gradlew.bat run` and open the sample skate scene (floor, skater, board, props, directional light). The log shows `Shadow quality: MEDIUM (cascades 3x2048)`.
2. **Acne:** orbit the camera over the floor, ramps and character. Lit surfaces must show no stripes, moiré or speckles. Select the Directional Light and lower its direction until the sun is close to the horizon (for example `y = -0.15`). Surfaces must stay clean.
3. **Peter-panning:** look closely where the skater's feet, the board wheels and props touch the floor. Each shadow must meet its object with no visible gap or floating outline.
4. **Cascades:** walk or fly the camera away from a shadow caster up to the light's `Shadow Distance` (50 m by default). Shadows must stay put and must not shimmer while the camera moves or turns. Detail drops smoothly with distance, and shadows fade out near the shadow distance instead of ending in a hard line.
5. **Spot/point lights:** add a Spot Light above a prop, aimed down, and a Point Light near the floor next to the skater. Each one casts a correct shadow away from the light with no acne. The point light shadows in every direction, with no seams between cube faces.
6. **Quality levels:** close the editor and repeat steps 1–5 with `LOW` and then `HIGH`. LOW gives one cascade with harder, blockier edges. HIGH gives four cascades with the softest 5x5 PCF edges. Every level renders shadows with no acne or gaps and no GL errors in the log.
7. **Toggles:** in Window > Render Graph, disable `Shadow`. All shadows vanish on the next frame and come back when you re-enable it. Turning off `Cast Shadows` on the Directional Light removes only the sun shadows.
8. Automated: `.\gradlew.bat :test --tests "com.pafoid.skate.engine.render.shadow.*"` passes. It covers cascade split maths, stable fitting and texel snapping, bias, quality tiers, local shadow budgeting, cube faces and culling.
