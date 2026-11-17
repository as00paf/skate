# Animator

La fenêtre Animator crée et modifie les **contrôleurs d'animation** : les fichiers qui décident quelle animation joue un personnage et quand il passe à une autre. Vous n'avez jamais besoin de modifier le fichier du contrôleur à la main.

## Ce que c'est

Un **contrôleur** (un fichier `.controller.json`) est un graphe :

- Un **état** est une chose que le personnage peut faire, comme `idle`, `walk` ou `jump`. Chaque état joue un **clip** (une animation). L'**état par défaut** (marqué ▶) est celui où le personnage démarre.
- Une **transition** est une flèche d'un état vers un autre. Elle a lieu quand toutes ses **conditions** sont vraies, ou quand le clip a été joué jusqu'au bout si **Attendre la fin du clip** est coché. La **durée du fondu** mélange les deux clips pour que le passage soit doux.
- Un **paramètre** est une valeur que le gameplay ou les scripts règlent et que les conditions lisent. Un **Nombre** est une valeur (`speed`), un **Booléen** est activé ou non, un **Déclencheur** est activé une fois et consommé par la transition qu'il déclenche.
- **N'importe quel état** est une boîte spéciale : ses transitions sont testées quel que soit l'état du personnage.

Appeler `play("jump")` depuis le gameplay ou un script active le paramètre booléen `jump` (une **demande**). Clic droit sur un état > **Aller ici sur demande** ajoute la transition correspondante pour vous.

## Comment l'utiliser

1. Ouvrez-la avec **Vue > Fenêtres > Ressources**, ou cliquez sur **Modifier** à côté du contrôleur d'un **Animator** dans Propriétés. Cliquez sur **Nouveau** pour créer un contrôleur dans `Assets/Animations`.
2. Les contrôleurs intégrés, comme celui du skateur, sont en lecture seule : cliquez sur **Faire une copie dans le projet**, puis sur **Utiliser sur …** pour donner la copie au personnage.
3. Clic droit sur le graphe > **Ajouter un état**, ou glissez une animation depuis la fenêtre Projet sur le graphe. Glissez les boîtes pour les déplacer ; glissez dans le vide pour en sélectionner plusieurs. Double-cliquez sur un état pour le renommer.
4. Sélectionnez un état pour choisir son **Clip**, sa **Vitesse**, sa **Boucle** et ses **Événements** à droite. Un état sans clip garde la pose de repos.
5. Clic droit sur un état > **Créer une transition**, puis cliquez sur l'état d'arrivée. Sélectionnez la flèche pour ajouter des **Conditions** (« speed est supérieur à 2 », « jump est déclenché »), la durée du fondu et l'attente de la fin du clip. Les flèches à côté des transitions d'un état règlent laquelle est testée en premier.
6. Ajoutez des paramètres avec **+ Nombre**, **+ Booléen** et **+ Déclencheur**. Renommer un paramètre met à jour chaque condition.
7. **Ctrl+D** duplique, **Ctrl+C** / **Ctrl+V** copient et collent des états, **Suppr** supprime, **F** affiche tout le graphe. Tout peut être annulé avec **Ctrl+Z**.
8. Cliquez sur **Enregistrer** : les personnages qui utilisent le contrôleur sont mis à jour, même pendant le jeu.

Pendant le jeu, le graphe suit l'objet **Aperçu sur** : l'état actif est vert, un fondu en cours s'affiche sur sa flèche, les événements clignotent, et vous pouvez changer les valeurs des paramètres pour essayer les transitions. Ces valeurs en direct ne sont pas enregistrées.

## Fenêtres liées

- [Éditeur d'animation](animation_editor.md) : modifier un clip que joue un état.
- [Propriétés](properties.md) : l'Animator qui utilise le contrôleur.
- [Projet](project.md) : les contrôleurs et les clips du projet.
