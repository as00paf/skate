# Profileur

Le Profileur vous dit à quelle vitesse tournent l'éditeur et votre jeu, et où part le temps. Utilisez-le quand la vue semble lente ou saccadée.

## Ce que c'est

Un jeu dessine de nombreuses images par seconde. Chaque image est une **trame**. Le Profileur mesure :

- **Temps de trame** : combien de millisecondes prend une trame. Plus c'est bas, mieux c'est. 16,7 ms correspond à 60 images par seconde.
- **FPS** : images par seconde. Plus c'est haut, mieux c'est.
- **Appels de dessin** : combien de fois par trame le moteur demande à la carte graphique de dessiner quelque chose. Beaucoup d'objets donne en général beaucoup d'appels.
- **Pas de physique** : le temps passé à déplacer les corps physiques et à tester les collisions.
- **Utilisation RAM** : la mémoire utilisée par l'éditeur.
- **Threads** : le temps processeur de chaque fil d'exécution du programme.

Avec **Afficher les Graphiques**, chaque valeur est dessinée en courbe sur les dernières secondes, ce qui fait ressortir les pics.

## Comment l'utiliser

1. Laissez le Profileur ouvert pendant que vous travaillez ou jouez la scène.
2. Regardez la courbe du temps de trame. Une ligne plate est bon signe ; les grands pics sont les moments où le jeu saccade.
3. Cliquez sur **Figer** pour arrêter les courbes et les lire calmement. Cliquez sur **Reprendre** pour revenir au direct.
4. Cliquez sur **Réinitialiser les stats** pour effacer l'historique, par exemple avant de tester un changement.
5. Activez **Afficher les statistiques mémoire** pour surveiller la mémoire. Une courbe qui ne fait que monter peut signifier que quelque chose n'est jamais libéré.

Si le temps de trame est élevé, ouvrez le [Graphe de rendu](render_graph.md) pour voir quelle passe de rendu coûte le plus.

## Fenêtres liées

- [Graphe de rendu](render_graph.md) : le temps de chaque passe de rendu.
- [Console](console.md) : des avertissements qui expliquent souvent un ralentissement.
- [Vue du jeu](game_viewport.md) : la scène mesurée.
