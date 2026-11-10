# Vue du jeu

La Vue du jeu est la vue 3D principale de l'éditeur. C'est là que votre scène devient un lieu où l'on peut se promener, regarder et faire des changements.

## Ce que c'est

La vue dessine la scène courante à travers la caméra de l'éditeur. Ce n'est pas la caméra du jeu : vous pouvez voler n'importe où sans déplacer le joueur. Au-dessus de l'image 3D, une barre d'outils contient de quoi sélectionner, déplacer, tourner, mettre à l'échelle et mesurer les objets.

La barre contient aussi les commandes de jeu. **Jouer** lance la scène, **Pause** la fige en gardant son état, **Arrêter** termine le jeu et remet la scène comme avant, et **Réinitialiser** remet chaque scène ouverte dans son état d'avant le jeu tout en continuant à jouer. Un bouton de débogage physique dessine les colliders en fil de fer, et un bouton appareil photo prend une capture d'écran.

Les onglets au-dessus de la vue 3D sont les scènes ouvertes. Chaque scène retient la position et le mode de la caméra de l'éditeur : en revenant à une scène, vous la retrouvez là où vous l'aviez laissée.

## Comment l'utiliser

1. Cliquez sur un objet pour le sélectionner. Le même objet est sélectionné dans la [Hiérarchie de la scène](hierarchy.md).
2. Maintenez le **bouton du milieu** de la souris et faites glisser pour regarder autour de vous. Utilisez **W**, **A**, **S** et **D** pour voler, **Espace** pour monter, **Maj gauche** pour descendre et la **molette** pour zoomer. Appuyez sur **Origine** (Home) pour revenir à la vue de départ et sur **F** pour cadrer l'objet sélectionné.
3. Choisissez l'outil voulu : **1** sélection, **2** déplacement, **3** rotation, **4** échelle, **M** mesure. La barre d'outils montre les mêmes outils.
4. Utilisez **Local/Monde** pour choisir les axes du gizmo, et **Aimantation** pour avancer par pas fixes.
5. Faites un clic droit dans la vue pour créer un objet vide, un cube, une sphère, un cylindre, un plan, une lumière ou une caméra, ou pour faire apparaître un préfab. Le menu propose aussi **Dupliquer**, **Supprimer**, **Cadrer la sélection** et **Réinitialiser la caméra**.
6. Faites glisser des fichiers depuis le [Navigateur de ressources](asset_browser.md) vers la vue pour placer des modèles, des préfabs et d'autres ressources.
7. Appuyez sur **F5** pour jouer. Pendant le jeu, la vue montre le jeu tel que le joueur le voit. Appuyez de nouveau sur **F5** pour arrêter et retrouver la vue de l'éditeur.

Si la vue est vide, vérifiez qu'un projet et une scène sont ouverts. La [Console](console.md) montre les erreurs de chargement, et le [Profileur](profiler.md) indique si le nombre d'images par seconde est en cause.

## Fenêtres liées

- [Hiérarchie de la scène](hierarchy.md) : la liste des objets de la scène.
- [Propriétés](properties.md) : modifier l'objet sélectionné.
- [Console](console.md) : lire les erreurs et les avertissements.
- [Profileur](profiler.md) : mesurer les images par seconde et le temps pris par chaque système.
- [Graphe de rendu](render_graph.md) : voir et régler les passes de rendu derrière l'image.
