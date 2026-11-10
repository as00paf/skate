# Inspecteur audio

L'Inspecteur audio est la table de mixage de l'éditeur. Il montre le son de l'objet sélectionné et les réglages de volume de tout le projet.

## Ce que c'est

Un son dans une scène vient d'un objet qui a un composant **Source audio**. La partie haute de la fenêtre modifie la Source audio de l'objet sélectionné : quel son jouer, à quel volume, s'il boucle et s'il est en **3D**. Un son 3D baisse avec la distance et vient de la gauche ou de la droite selon sa position.

Le **Mixeur** regroupe les sons en **bus** : **Principal**, **Musique**, **Effets sonores** et **Interface**. Chaque son joue sur un bus : vous pouvez baisser toute la musique d'un coup sans toucher à chaque objet.

## Comment l'utiliser

1. Sélectionnez un objet dans la [Hiérarchie de la scène](hierarchy.md).
2. S'il n'a pas encore de son, cliquez sur **Ajouter une source audio**.
3. Choisissez un fichier son, ou déposez-en un depuis le [Navigateur de ressources](asset_browser.md). WAV, OGG et MP3 fonctionnent.
4. Cliquez sur **Lire** pour l'écouter dans l'éditeur et sur **Arrêter** pour finir l'écoute. L'écoute n'est pas enregistrée avec la scène.
5. Réglez **Boucle**, **Lire au démarrage** et le bus. Activez **3D (positionnel)** pour les sons qui viennent d'un endroit du monde, et réglez les distances minimale et maximale.
6. Ouvrez **Mixeur** pour régler, pour chaque bus, le **Volume**, **Muet**, le **Passe-bas** (un son plus sourd, comme à travers un mur) et l'**Envoi réverbération** (la quantité d'écho dans une zone de réverbération). Les réglages du mixeur sont enregistrés avec le projet.
7. Cliquez sur **Retirer la source audio** pour retirer le son. Cela s'annule.

## Fenêtres liées

- [Propriétés](properties.md) : la même Source audio avec les autres composants.
- [Navigateur de ressources](asset_browser.md) : les sons du projet.
- [Hiérarchie de la scène](hierarchy.md) : choisir l'objet qui joue le son.
