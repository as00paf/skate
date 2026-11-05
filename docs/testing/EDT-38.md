# EDT-38: Switching the editor language keeps the layout

Windows, tabs, tree nodes, headers, menus and popups now use `translated text###stable_id`, so their ImGui ID no
longer depends on the language. An `imgui.ini` saved before this change has no stable IDs and is ignored once: the
default layout is rebuilt.

1. Start the editor and create a **new project**. Dock a few windows somewhere unusual: drag **Console** next to
   **Hierarchy**, and float **Render Graph**. Open **Asset Browser** on its **Sounds** tab, expand a component header
   in **Properties** and a tree in **Hierarchy**, and expand **ShadowPass** in the Render Graph.
2. **Settings > Language > Français** (or Editor Settings > General > French, then Apply).
3. Every window keeps its dock slot or floating position and its size, with French titles. No duplicate empty
   windows appear.
4. The Asset Browser stays on its tab (now "Sons"), and the expanded headers and trees stay open. The Render Graph
   ShadowPass "Qualité" combo still opens.
5. Right-click a scene tab and close a modified scene: the confirmation popup opens, in French. In Properties,
   remove a component: its confirmation popup opens too. Every menu in the menu bar opens.
6. Switch back to **English**: same checks, nothing moves.
7. Restart the editor while in French: the layout from step 1 is restored.
8. (Old layout) Replace `imgui.ini` with a copy from before this change and start the editor: the default layout
   appears instead of a pile of undocked windows.
