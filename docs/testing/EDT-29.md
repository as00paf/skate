# EDT-29: Shadow quality setting

1. Start the editor and create a **new project**. The default scene opens with a sun casting shadows (skateboard, skater, park pieces).
2. Open **Settings > Editor Settings > Graphics**. **Shadow Quality** shows **Medium**.
3. Pick **Low**, then click **Apply**. Without reloading, shadow edges in the viewport get harder and blockier, and distant shadows lose detail (one cascade).
4. Pick **High**, then click **OK**. Shadows get softer and sharper far from the camera.
5. Open **View > Windows > Render Graph** and expand **ShadowPass**. Its **Quality** combo shows **High**, and Execution Time / GPU Time are still listed under Show Performance.
6. In the Render Graph, switch **Quality** to **Low**. The viewport updates at once, and Editor Settings > Graphics now also shows **Low**.
7. Press **Play**, switch Quality to **Medium** in the Render Graph, and check that the running game's shadows change. Stop.
8. Close and restart the editor. Shadow Quality is still **Medium** (saved in `editor.config` under `graphics.shadowQuality`), and shadows render at that tier.
