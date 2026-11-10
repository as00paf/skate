# Propriétés

La fenêtre Propriétés est l'inspecteur de l'objet sélectionné. Si la Hiérarchie de la scène vous dit ce qui existe, Propriétés vous dit de quoi c'est fait et vous permet de le changer.

## Ce que c'est

Un objet est construit à partir de **composants**. Le composant Transform est toujours présent : il contient la position, la rotation et l'échelle de l'objet. D'autres composants ajoutent des capacités :

- un composant **Mesh** rend l'objet visible ;
- un composant **Lumière** en fait une lampe ;
- un **Collider** et un **Rigid Body** le rendent solide et laissent la physique le déplacer ;
- une **Source audio** lui fait jouer un son ;
- un composant **Script** exécute votre propre code.

Propriétés montre le nom de l'objet, son état activé, et une section repliable par composant. Chaque champ a une infobulle qui explique son rôle. Les valeurs peuvent être tapées, glissées ou remises à leur valeur par défaut.

## Comment l'utiliser

1. Sélectionnez un objet dans la [Hiérarchie de la scène](hierarchy.md) ou cliquez dessus dans la [Vue du jeu](game_viewport.md).
2. Dépliez la section **Transform** pour régler la position, la rotation et l'échelle. Vous pouvez aussi utiliser les gizmos de déplacement, rotation et échelle dans la vue.
3. Cliquez sur **Ajouter un composant** pour attacher un nouveau composant. Tapez dans le champ de recherche de la fenêtre qui s'ouvre pour le trouver vite.
4. Changez une valeur et regardez la vue : le résultat apparaît tout de suite.
5. Utilisez le bouton corbeille de l'en-tête d'un composant pour le retirer. Ce retrait s'annule avec **Ctrl+Z**.
6. Un clic droit sur l'en-tête d'un composant propose **Copier**, **Réinitialiser** et **Retirer**.
7. Si rien n'est sélectionné, la fenêtre affiche un court message au lieu d'une liste vide.

Certains composants ont aussi leur propre fenêtre. Par exemple, les sources audio apparaissent aussi dans l'[Inspecteur audio](audio_inspector.md), et les matériaux et lumières ont des sections détaillées ici, dans Propriétés.

## Fenêtres liées

- [Hiérarchie de la scène](hierarchy.md) : choisir l'objet à inspecter.
- [Vue du jeu](game_viewport.md) : voir le résultat en 3D.
- [Navigateur de ressources](asset_browser.md) : faire glisser des ressources comme des modèles, textures et sons sur les champs des composants.
- [Inspecteur audio](audio_inspector.md) : le côté sonore de l'objet sélectionné et le mixeur du projet.
