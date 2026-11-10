# Prise en main de Skate

Skate est un moteur de jeu 3D avec son propre éditeur. Vous créez un jeu en plaçant des objets dans une scène, en leur ajoutant des composants et en appuyant sur Jouer. Inutile de savoir utiliser un logiciel 3D pour commencer : cette page explique les mots et les premiers pas.

## L'éditeur en un coup d'œil

L'éditeur est fait de fenêtres que l'on peut ancrer. La disposition par défaut montre :

- la **Hiérarchie de la scène** à gauche : la liste des objets de la scène courante ;
- la **Vue du jeu** au centre : la vue 3D où vous placez et déplacez les objets ;
- les **Propriétés** à droite : les réglages de l'objet sélectionné ;
- la fenêtre **Projet** et le **Navigateur de ressources** pour les fichiers et les ressources ;
- la **Console**, le **Profileur**, l'**Historique des commandes**, le **Graphe de rendu** et l'**Éditeur de scripts** en bas.

Chaque fenêtre a un onglet. Faites glisser un onglet pour déplacer une fenêtre, fermez-la avec le X et rouvrez-la depuis **Vue > Fenêtres**. Le bouton **?** dans l'en-tête d'une fenêtre ouvre l'article d'aide de cette fenêtre.

## Les mots que vous verrez

- **Projet** : le dossier qui contient votre jeu, ses scènes, ses scripts et ses ressources.
- **Scène** : un niveau ou une étape de votre jeu. Une scène contient des objets. Vous pouvez avoir plusieurs scènes et passer de l'une à l'autre avec les onglets au-dessus de la vue.
- **GameObject** : un objet de la scène. Ce peut être un simple repère, un modèle 3D, une lumière, une caméra ou une source sonore.
- **Composant** : une capacité ajoutée à un GameObject, comme sa position (**Transform**), un **Mesh**, une **Lumière**, un **Collider** ou un **Script**.
- **Ressource** : un fichier utilisé par le jeu, comme une texture, un modèle, un son, une animation ou un préfab.
- **Préfab** : un GameObject enregistré avec ses composants, que vous pouvez réutiliser dans d'autres scènes ou plusieurs fois dans la même scène.
- **Gizmo** : les poignées colorées qui apparaissent sur l'objet sélectionné pour le déplacer, le tourner ou le mettre à l'échelle.

## Vos cinq premières minutes

1. Lancez l'éditeur. Si aucun projet n'est ouvert, la fenêtre **Changer de projet** apparaît. Choisissez **Nouveau projet** et suivez l'[Assistant de projet](project_wizard.md) : donnez un nom et un dossier au projet.
2. Regardez la [Hiérarchie de la scène](hierarchy.md). Elle liste les objets de la scène. Cliquez sur un objet pour le sélectionner.
3. Regardez le panneau [Propriétés](properties.md). Il montre les composants de l'objet sélectionné. Changez une valeur et observez la vue.
4. Utilisez la [Vue du jeu](game_viewport.md) pour regarder autour de vous : maintenez le bouton du milieu de la souris et faites glisser, déplacez-vous avec **W**, **A**, **S** et **D**, et appuyez sur **Origine** (Home) pour revenir à la vue de départ.
5. Ajoutez un objet avec le bouton **+** de la Hiérarchie. Déplacez-le avec le gizmo de déplacement (**2**), tournez-le avec **3** et mettez-le à l'échelle avec **4**.
6. Appuyez sur **F5** pour jouer la scène. Appuyez de nouveau sur **F5** pour arrêter. Le mode Jeu est temporaire : les changements faits pendant le jeu ne sont pas gardés à l'arrêt.
7. Enregistrez avec **Ctrl+S**. La scène courante est enregistrée dans le projet.

## Quand quelque chose ne va pas

Ouvrez la [Console](console.md) et lisez d'abord les lignes rouges **Erreur**. Le [Profileur](profiler.md) indique si l'éditeur tourne lentement. Si vous ne trouvez pas une fenêtre, utilisez la [Recherche globale](search.md) (**Ctrl+P**) ou le menu **Vue > Fenêtres**.

## Pour aller plus loin

- [Raccourcis clavier](shortcuts.md)
- [Hiérarchie de la scène](hierarchy.md)
- [Vue du jeu](game_viewport.md)
- [Projet](project.md)
