# EDT-52C: Project and Asset Browser follow the window standard, manual test

Run `.\gradlew.bat run` and open a project.

1. **Project** and **Asset Browser** titles start with an icon (folder, image file). Each has one header strip: the search box and a Refresh button on the left, `?` on the right. The old toolbars above each Asset Browser tab are gone.
2. Click `?`, then right-click each tab and choose **Help**. Both open the same popover (What / How / Related), with buttons that open the related windows.
3. Project: right-click empty space under the tree. The menu shows New Folder and New File, then Refresh and Show folder, then Copy folder path. Right-click a file, then empty space again: the empty-space menu appears, not the file's menu.
4. Project: right-click a `.scene` file and choose **Open**. The scene opens, as with a double-click.
5. Project: type a name that matches nothing in the search box. "No results" shows with a **Clear search** button, which empties the box. Close the project: "No project open" shows with New and Open Project.
6. Asset Browser: in the Animations, Textures and Sounds tabs, right-click empty space. The menu shows Refresh, Show folder (opens the assets folder) and Copy folder path. The Prefabs tab has no empty-space menu.
7. Asset Browser: search for something that matches nothing. Every tab, Prefabs included, shows "No results" with **Clear search**. The search is shared across tabs.
8. Switch the editor to French. The header tooltips, menus, empty states and help text are in French.

Automated: `.\gradlew.bat :test --tests "*BrowserMenusTest" --tests "*WindowStandardGuardTest"`.
