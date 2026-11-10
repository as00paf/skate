# Shell

Le Shell exécute un programme en ligne de commande depuis l'éditeur et affiche ce qu'il écrit. Plus besoin d'ouvrir un terminal à part pour les tâches courantes comme compiler ou vérifier le projet.

## Ce que c'est

Une **commande** est une ligne de texte qui lance un programme, par exemple `gradlew build`. Le Shell exécute une commande à la fois dans le dossier du projet et affiche sa sortie en direct. La ligne d'état indique si la commande tourne, combien de temps elle a pris et son **code de sortie** (0 veut en général dire succès).

Le menu **Préréglages** contient des commandes toutes prêtes :

- compiler le moteur avec Gradle ;
- lancer tous les tests ;
- afficher l'aide de la CLI Skate ;
- valider, exécuter sans fenêtre ou empaqueter ce projet avec la CLI Skate.

## Comment l'utiliser

1. Tapez une commande dans le champ en bas, ou choisissez-en une dans **Préréglages**.
2. Appuyez sur **Entrée** ou cliquez sur **Exécuter**.
3. Lisez la sortie au fur et à mesure. Les erreurs du programme apparaissent ici, pas dans la Console.
4. Cliquez sur **Annuler** pour arrêter une commande trop longue.
5. Appuyez sur **Haut** et **Bas** dans le champ pour rappeler les commandes précédentes.
6. Cliquez sur **Effacer** pour vider la sortie.

Une seule commande tourne à la fois. Attendez qu'elle se termine, ou annulez-la, avant de lancer la suivante.

## Fenêtres liées

- [Console](console.md) : les messages de l'éditeur lui-même.
- [Exporter le projet](export_wizard.md) : construire une copie jouable du jeu sans taper de commande.
