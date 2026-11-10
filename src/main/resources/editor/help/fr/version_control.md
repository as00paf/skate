# Gestion de versions

La Gestion de versions garde l'historique de votre projet. Chaque fois que votre travail vous convient, vous en enregistrez un instantané. Plus tard, vous pouvez voir ce qui a changé, jeter un changement raté ou essayer une idée à part sans casser la version principale.

## Ce que c'est

La fenêtre utilise **Git**, l'outil de gestion de versions le plus répandu. Inutile d'installer Git : l'éditeur l'intègre. Quelques mots :

- **Dépôt** : le dossier du projet une fois que Git garde son historique.
- **Validation** (commit) : un instantané enregistré des fichiers choisis, avec un court message qui dit ce que vous avez fait.
- **Branche** : une ligne d'historique séparée. Créez une branche pour essayer une idée ; revenez en arrière si elle ne marche pas.
- **Conflit** : le même fichier a été modifié à deux endroits et Git ne peut pas choisir. C'est vous qui décidez quelle version garder.

La fenêtre a deux onglets :

- **Modifications** : les fichiers modifiés depuis la dernière validation, regroupés en Scènes, Préfabriqués, Scripts, Ressources, Paramètres et Autres. Les conflits sont listés en premier. Le fichier `.meta` d'un fichier l'accompagne.
- **Historique** : les validations précédentes, de la plus récente à la plus ancienne.

Le menu des branches, en haut, montre la branche courante.

## Comment l'utiliser

1. Ouvrez-la avec **Vue > Fenêtres > Projet**. Si le projet n'a pas encore d'historique, cliquez sur **Initialiser** : l'éditeur crée le dépôt et fait une première validation.
2. Travaillez normalement. Ouvrez **Modifications** pour voir les fichiers modifiés. Cliquez sur un fichier pour voir ce qui a changé.
3. Cochez les fichiers à garder (**Tout** les coche tous), écrivez un **Message de validation** et appuyez sur **Valider**. Arrêtez le mode Jeu et enregistrez vos scènes avant.
4. Pour jeter des modifications, cochez les fichiers et cliquez sur **Annuler les modifications**. Les fichiers reviennent à la dernière validation et les nouveaux fichiers sont supprimés : lisez bien la confirmation.
5. Ouvrez **Historique** et cliquez sur une validation pour voir ses fichiers. Cliquez sur un fichier pour voir ce qui a changé dans cette validation.
6. Utilisez le menu des branches pour changer de branche, ou **Nouvelle branche** pour en créer une depuis la validation actuelle. **Supprimer la branche** retire une branche devenue inutile. Validez ou annulez vos modifications avant de changer de branche.
7. Si un fichier est en conflit, choisissez **Garder la mienne** ou **Prendre la leur**, puis validez.

Les actions Git ne s'annulent pas avec **Ctrl+Z** : une validation reste dans l'historique, et les modifications annulées sont perdues. Pour tout ce que cette fenêtre ne fait pas, lancez `git` dans le [Shell](shell.md).

## Fenêtres liées

- [Projet](project.md) : les fichiers sous gestion de versions.
- [Shell](shell.md) : lancer d'autres commandes Git.
- [Historique des commandes](command_history.md) : annuler les changements de l'éditeur pendant la session.
