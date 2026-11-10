# Navigateur de ressources

Le Navigateur de ressources est l'étagère des choses que vous pouvez mettre dans vos scènes : modèles 3D, textures, sons, animations et préfabs. Il les montre par type, avec un aperçu, pour que vous les trouviez sans savoir dans quel dossier ils se trouvent.

## Ce que c'est

Une **ressource** est un fichier utilisé par votre jeu. Le Navigateur de ressources liste les ressources du projet ouvert dans des onglets par type, avec un champ de recherche en haut. Contrairement à la fenêtre [Projet](project.md), il cache les fichiers qui ne sont pas des ressources, comme les fichiers de réglages.

Un **préfab** est un objet enregistré avec tous ses composants, que vous pouvez placer encore et encore.

## Comment l'utiliser

1. Choisissez un onglet, ou tapez une partie d'un nom dans le champ de recherche.
2. Faites glisser une ressource dans la [Vue du jeu](game_viewport.md) ou la [Hiérarchie de la scène](hierarchy.md) pour la placer dans la scène.
3. Faites glisser une texture, un son ou une animation sur un objet ou sur un champ de [Propriétés](properties.md) pour l'utiliser là. Une animation demande un objet qui a un **Animator**.
4. Faites un clic droit sur une ressource pour d'autres actions :
   - **Apparaître dans la scène** place un préfab dans la scène courante.
   - **Appliquer à la sélection** utilise la ressource sur l'objet sélectionné.
   - **Ajouter aux favoris** la garde sous la main.
   - **Afficher dans le dossier** ouvre son dossier dans l'explorateur de fichiers.
   - **Ouvrir dans l'éditeur externe** l'ouvre dans un autre programme, comme un logiciel de dessin.
   - **Renommer** et **Supprimer** modifient le fichier lui-même.
5. Chaque placement s'annule avec **Ctrl+Z**.

Pour obtenir de nouvelles ressources gratuites, utilisez la [Boutique de ressources](asset_store.md). Pour transformer une vidéo en animation, utilisez [Vidéo vers animation](video_to_animation.md).

## Fenêtres liées

- [Projet](project.md) : tous les fichiers du projet.
- [Boutique de ressources](asset_store.md) : télécharger des modèles, textures et autres gratuitement.
- [Propriétés](properties.md) : les champs qui utilisent des ressources.
