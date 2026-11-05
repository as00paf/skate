# REN-23 test steps: day/night time scale survives Play/Stop

Stopping Play used to reset the scene's saved `DayNightCycleComponent.timeScale` to 1.0. The toolbar's play-mode
speed now lives in a separate field that is never saved.

1. `.\gradlew.bat run`, then create a NEW project.
2. Select the scene in the Hierarchy and find the Day/Night Cycle component. If it is missing, add it (Add Component >
   Day Night Cycle). Set Time Scale to 2.5.
3. Press Play, then Stop → the inspector still shows Time Scale 2.5.
4. Press Play, then Pause and Resume from the toolbar, then Stop → Time Scale is still 2.5, and the toolbar showed
   Pause while running.
5. Save the scene (Ctrl+S), close and reopen the project → Time Scale is 2.5.
