# Hiérarchie de la scène

La Hiérarchie de la scène est la liste de tout ce qui existe dans la scène courante. Voyez-la comme la table des matières de votre niveau : si un objet n'est pas dans cette liste, le jeu ne le connaît pas.

## Ce que c'est

La fenêtre montre un arbre. En haut se trouve la scène elle-même, et en dessous les objets. Un objet peut en contenir d'autres : une planche peut contenir ses roues, un personnage un chapeau, et ainsi de suite. Déplacer un parent déplace aussi ses enfants.

Chaque ligne a trois parties :

- le **nom** de l'objet ;
- une icône **œil** pour montrer ou cacher l'objet dans la vue ;
- une icône **cadenas** pour verrouiller l'objet et éviter de le déplacer par erreur.

En haut, vous voyez aussi le nom de la scène avec un petit bouton crayon pour la renommer, un champ de recherche, un bouton **+** pour créer un objet vide et un bouton **lien** qui garde la sélection de la hiérarchie et celle de la vue synchronisées.

## Comment l'utiliser

1. Cliquez sur un objet pour le sélectionner. Ses composants apparaissent dans [Propriétés](properties.md).
2. Tapez dans le champ de recherche pour ne garder que les objets dont le nom contient ce texte.
3. Appuyez sur **+** ou **Inser** pour créer un objet vide. Renommez-le avec **F2**.
4. Faites glisser un objet sur un autre pour en faire un enfant. Déposez-le sur le nom de la scène pour qu'il redevienne un objet de premier niveau.
5. Faites un clic droit sur un objet pour **Dupliquer**, **Copier**, **Couper**, **Coller comme enfant**, **Supprimer**, **Renommer**, **Cadrer dans la vue**, **Verrouiller** et **Visible**. Un clic droit dans le vide crée un objet vide.
6. Utilisez **Haut** et **Bas** pour parcourir la liste, **Origine** et **Fin** pour aller au premier ou au dernier objet, **Suppr** pour supprimer la sélection et **Ctrl+D** pour la dupliquer.
7. Les icônes en haut de la liste permettent aussi de déplier ou replier tout l'arbre.

La suppression d'un objet peut être annulée : appuyez sur **Ctrl+Z** si vous avez supprimé le mauvais. Si un objet est caché ou verrouillé, l'œil et le cadenas l'indiquent d'un coup d'œil.

## Fenêtres liées

- [Propriétés](properties.md) : modifier l'objet sélectionné.
- [Vue du jeu](game_viewport.md) : voir et déplacer l'objet en 3D.
- [Projet](project.md) : les fichiers et préfabs que l'on peut placer dans la scène.
