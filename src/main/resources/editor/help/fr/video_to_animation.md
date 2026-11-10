# Vidéo vers animation

Vidéo vers animation transforme la vidéo d'une personne en animation pour votre personnage. Filmez-vous en train de faire un mouvement, et le personnage pourra le refaire. C'est ce qu'on appelle la **capture de mouvement**, ou **mocap**.

## Ce que c'est

La fenêtre regarde la vidéo image par image, trouve la pose de la personne (tête, bras, jambes) et construit un **clip d'animation** pour le squelette du personnage. Tout se passe sur votre ordinateur : rien n'est envoyé en ligne.

Vous pouvez aussi importer un fichier de mocap créé par un autre programme (BVH, FBX ou glTF).

La détection de pose utilise Python avec la bibliothèque MediaPipe. Vous la configurez une fois dans la section **Configuration** ; les étapes sont dans `docs/mocap-setup.md`.

## Comment l'utiliser

1. La première fois, ouvrez **Configuration**, choisissez votre programme **Python** et cliquez sur **Vérifier la configuration**. Attendez le message **Prêt**.
2. Cliquez sur **Choisir une vidéo...** et prenez une vidéo (mp4, mov, avi, mkv ou webm). Un plan net, en pied, avec une seule personne marche le mieux.
3. Déplacez le curseur **Image** pour vérifier que la pose détectée suit la personne.
4. Réglez les options :
   - **Personnage** : le skateur, ou l'objet sélectionné s'il a un squelette.
   - **Lissage** : enlève les tremblements. Trop de lissage peut adoucir les mouvements rapides.
   - **Déplacement de la racine** : fait avancer le personnage dans la scène comme la personne. Désactivé, le mouvement reste sur place.
   - **Bloquer les pieds** : garde les pieds plantés tant qu'ils touchent le sol.
5. Cliquez sur **Créer l'animation** et attendez. **Annuler** l'arrête.
6. Le clip est enregistré dans `Assets/Animations`. Cliquez sur **Afficher dans Projet** pour le trouver, puis faites-le glisser sur un objet qui a un **Animator**.

Si seule une petite partie du mouvement correspond aux os du personnage, la fenêtre vous prévient. Essayez une vidéo plus nette ou un autre personnage.

## Fenêtres liées

- [Navigateur de ressources](asset_browser.md) : les animations du projet.
- [Projet](project.md) : le nouveau clip dans `Assets/Animations`.
- [Propriétés](properties.md) : l'Animator qui joue le clip.
