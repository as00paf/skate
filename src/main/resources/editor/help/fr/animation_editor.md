# Éditeur d'animation

L'Éditeur d'animation modifie un clip d'animation : vous pouvez le lire sur un personnage, corriger une pose, déplacer des clés dans le temps, ajouter des événements et changer sa durée. Utilisez-le pour retoucher une animation plutôt que de la refaire.

## Ce que c'est

Un **clip d'animation** est un court mouvement, comme une marche ou un saut, enregistré dans un fichier `.skanim`. Il fait bouger les os du **squelette** d'un personnage au fil du temps. Quelques mots :

- **Image** : un pas de temps. La **Cadence** dit combien d'images font une seconde.
- **Clé** : une valeur enregistrée d'un os à une image. Entre deux clés, le moteur calcule le mouvement.
- **Tête de lecture** : l'image courante, affichée sur la règle de la ligne de temps.
- **Événement** : une marque nommée à une image, comme `footstep`, qui dit au jeu de faire quelque chose à ce moment.

La fenêtre montre un aperçu du clip sur un personnage, la liste des os, la ligne de temps avec les clés, et les événements.

## Comment l'utiliser

1. Ouvrez-la avec **Vue > Fenêtres > Ressources**. Choisissez un clip dans la liste **Clip**, ou cliquez sur **Ouvrir...** pour choisir un fichier d'animation. Un clip FBX ou glTF s'ouvre en copie `.skanim` modifiable.
2. Choisissez le **Squelette** de l'aperçu : le skateur, ou l'objet sélectionné s'il a un squelette.
3. Cliquez sur la règle pour déplacer la tête de lecture. Utilisez **Lecture** (ou **Espace**), **Image précédente** et **Image suivante** (**Gauche** et **Droite**), et **Au début** et **À la fin** (**Origine** et **Fin**). **Boucle**, **Vitesse** et **Magnétisme** changent la lecture.
4. Cliquez sur une clé pour la sélectionner ; **Ctrl**+clic ajoute des clés ; tracez un cadre pour en sélectionner plusieurs. Faites glisser les clés sélectionnées pour les déplacer dans le temps. **Ctrl+A** sélectionne toutes les clés.
5. Avec des clés sélectionnées, utilisez **Échelle** avec un **Facteur**, **Copier** (**Ctrl+C**), **Coller** (**Ctrl+V**) et **Supprimer les clés** (**Suppr**).
6. Pour changer une pose, sélectionnez un os dans la liste (tapez dans **Filtrer les os** pour le trouver) ou cliquez dessus dans l'aperçu, puis cliquez sur **Clés sur os** (**K**) pour enregistrer sa pose à la tête de lecture.
7. Tapez un **Nom de l'événement** et cliquez sur **Ajouter un événement** pour placer un événement à la tête de lecture. **Renommer** et **Supprimer l'événement** le modifient.
8. Ouvrez l'onglet **Courbes** pour façonner le mouvement entre les clés. Choisissez la piste dans la liste (les rotations sont en degrés) et les courbes **X**, **Y** et **Z** à afficher. Faites glisser une clé pour changer sa valeur et son temps ; maintenez **Maj** pour rester dans une seule direction. Donnez une tangente aux clés sélectionnées : **Auto**, **Linéaire**, **Plate**, **Libre**, **Cassée** ou **Palier**. Sur les courbes de position et d'échelle, faites glisser les poignées rondes pour régler la pente. La molette zoome les valeurs, **Ctrl**+molette zoome le temps.
9. Utilisez **Nettoyage** pour corriger un clip de mocap ou de vidéo. **Rééchantillonner** met une clé à chaque image, **Réduire les clés** supprime les clés inutiles à la courbe, et **Lisser** retire le tremblement. Réglez une **Plage** sur la règle (**Début à la tête de lecture**, **Fin à la tête de lecture**) pour choisir les images de **Lisser**, **Bloquer le pied gauche** ou **Bloquer le pied droit** (empêche les pieds de glisser), et **Couper à la plage**. **Miroir** échange gauche et droite, et **Corriger la boucle** évite qu'une boucle saute à la fin. Sélectionnez d'abord des os pour limiter Réduire et Lisser à ces os.
10. Réglez la **Durée (s)**, puis **Réajuster** pour étirer tout le clip à cette durée, ou **Couper** pour le raccourcir. Changez la **Cadence** si besoin.
11. Chaque changement s'annule avec **Ctrl+Z**. Cliquez sur **Enregistrer** pour écrire le fichier.

Pour jouer le clip dans le jeu, faites-le glisser sur un objet qui a un **Animator**.

## Fenêtres liées

- [Vidéo vers animation](video_to_animation.md) : créer un nouveau clip à partir d'une vidéo.
- [Navigateur de ressources](asset_browser.md) : les animations du projet.
- [Propriétés](properties.md) : l'Animator qui joue le clip.
