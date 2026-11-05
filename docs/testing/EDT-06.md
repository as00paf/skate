# EDT-06: Complete translations

EDT-37 already gave every key a French entry, with a test for missing keys. This task removes the hardcoded English
that was left in editor code and adds a guard against new literals.

1. Start the editor, create a **new project**, and switch to **French** (Settings > Language).
2. **Ctrl+Shift+F** (Search Everywhere), type `scène`: the actions ("Enregistrer la scène", "Fermer la scène", …)
   and their descriptions are in French, with the subcategory "Action de l'éditeur". Typing English keywords
   (`save`) still finds them.
3. Search an object name: the subcategories read "Lumière", "Source audio" or "Maillage". Search a component: you
   see "Composant sur <objet>".
4. Create an empty object, rename it, then move it. **Command History** lists "Créer un GameObject",
   "Renommer (<nom>)" and "Transformation".
5. **Project switcher** (the project name button in the menu bar): "Dernière ouverture" shows "À l'instant" or
   "il y a N minutes".
6. **Key Bindings**: add a mouse binding. The menu shows "Souris gauche", "Souris droite", and so on. **Input
   Testing** with a gamepad shows axis and button rows in French ("Gâchette gauche (axe 4)", "Retour (bouton 6)").
7. In the **Asset Browser > Sounds** tab, drag a sound: the drag tooltip is in French. In the **Render Graph**,
   disable a pass: the status bar shows "(1 désactivée(s))".
8. In the Project Wizard, the name check shows "Valide" and the button reads "Ouvrir un projet".
