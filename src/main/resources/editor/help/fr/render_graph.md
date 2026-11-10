# Graphe de rendu

Le Graphe de rendu montre comment le moteur dessine une image de votre scène. C'est une fenêtre avancée : vous n'en avez pas besoin pour faire un jeu, mais elle aide à comprendre l'image et à l'accélérer.

## Ce que c'est

Le moteur ne dessine pas une image d'un seul coup. Il exécute plusieurs **passes**, l'une après l'autre, et chacune ajoute quelque chose :

- **Ombres** : calcule où les lumières projettent des ombres.
- **Sélection** : trouve l'objet sous la souris quand vous cliquez.
- **Géométrie** : dessine le ciel, les objets 3D et les sprites.
- **Particules** : dessine la fumée, les étincelles et autres effets de particules.
- **Post-traitement** : applique des effets comme le bloom ou l'étalonnage des couleurs à toute l'image.
- **Débogage** : dessine la grille, les formes physiques, les gizmos et les icônes de l'éditeur.
- **Interface du jeu** : dessine les menus et l'affichage tête haute du jeu par-dessus.

La fenêtre dessine les passes sous forme de boîtes reliées par les **ressources** (images et tampons) qu'elles lisent et écrivent. Chaque boîte indique le temps que prend la passe sur la carte graphique (GPU) et sur le processeur (CPU).

## Comment l'utiliser

1. Ouvrez une scène. Le graphe reste vide tant que le moteur de rendu n'a pas construit ses passes.
2. Cliquez sur une passe pour voir ses détails et une courbe de son temps sur les dernières images.
3. Décochez la case d'une passe pour la désactiver pendant cette session de l'éditeur et voir ce qu'elle apporte. Un cadenas marque les passes qui doivent toujours tourner. Les jeux exportés exécutent toujours toutes les passes. **Tout activer** remet tout en marche.
4. Dépliez **Post-traitement** pour activer ou désactiver chaque effet.
5. Faites glisser avec le bouton du milieu ou le clic droit pour vous déplacer, utilisez la molette pour zoomer, et cliquez sur **Ajuster** pour voir tout le graphe. **1:1** l'affiche à sa taille réelle.
6. Cliquez sur une ressource pour mettre en évidence les passes qui y sont liées.

Si cliquer sur les objets dans la vue ne marche plus, vérifiez que **Sélection** est activée.

## Fenêtres liées

- [Profileur](profiler.md) : le temps total d'une image.
- [Paramètres de l'éditeur](editor_settings.md) : la qualité graphique de l'éditeur.
- [Vue du jeu](game_viewport.md) : l'image produite par les passes.
