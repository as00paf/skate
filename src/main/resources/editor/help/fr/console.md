# Console

La Console est l'endroit où l'éditeur et le moteur notent ce qu'ils font. Quand quelque chose ne marche pas, c'est le premier endroit où regarder.

## Ce que c'est

Chaque message tient sur une ligne et a un niveau :

- **Info** : une nouvelle normale, comme « scène chargée ».
- **Avert.** : quelque chose semble anormal, mais l'éditeur continue.
- **Erreur** : quelque chose a échoué. Lisez celles-ci en premier.
- **Action** : un changement que vous avez fait dans l'éditeur, comme créer ou supprimer un objet.

Les onglets **Moteur** et **Éditeur** séparent les messages du moteur de jeu de ceux de l'éditeur lui-même. Un compteur en haut indique combien il y a de messages de chaque niveau.

## Comment l'utiliser

1. Cliquez sur **Tous**, **Info**, **Avert.**, **Erreur** ou **Action** pour n'afficher qu'un niveau.
2. Tapez dans le champ de recherche pour ne garder que les lignes qui contiennent ce texte.
3. Cliquez sur une ligne pour la sélectionner. Maintenez **Ctrl** pour ajouter des lignes une à une, ou **Maj** pour sélectionner une plage.
4. Appuyez sur **Ctrl+C**, ou faites un clic droit et choisissez **Copier la sélection**, pour copier les lignes. **Tout copier** copie tout le panneau. Collez-les dans un rapport de bug ou un message.
5. Faites un clic droit et choisissez **Effacer tous les logs** pour repartir d'une console vide.
6. Activez **Défilement auto** pour toujours voir le dernier message, ou désactivez-le pour lire les anciens pendant que de nouveaux arrivent.

Les erreurs de script qui surviennent pendant le jeu apparaissent aussi ici, avec le nom du script et la ligne.

## Fenêtres liées

- [Historique des commandes](command_history.md) : les actions de l'éditeur derrière les lignes Action.
- [Profileur](profiler.md) : vérifier si l'éditeur est lent.
- [Shell](shell.md) : exécuter une commande et lire sa propre sortie.
- [Éditeur de scripts](script_editor.md) : corriger les scripts qui signalent des erreurs.
