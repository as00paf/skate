# Valider les modifications

La fenêtre Valider liste ce qui a changé dans le projet depuis la dernière validation et enregistre les fichiers choisis dans l'historique. **Modifications...** dans le bouton de branche ouvre la même fenêtre.

## Ce que c'est

- La **liste** à gauche regroupe les fichiers modifiés en Scènes, Préfabriqués, Scripts, Ressources, Réglages et Autres. `M` veut dire modifié, `A` ajouté, `D` supprimé. Le fichier `.meta` d'un fichier l'accompagne. Les nouveaux fichiers sont cochés d'office.
- Les **différences** à droite montrent ce qui a changé dans le fichier cliqué.
- La zone **message** en bas dit ce que vous avez fait.

Si une scène a des modifications non enregistrées, la fenêtre la nomme : cliquez d'abord sur **Tout enregistrer**, pour que la validation contienne votre dernier travail. Tant que des fichiers sont en conflit, résolvez-les d'abord dans la fenêtre des [conflits](vcs_conflicts.md).

## Comment l'utiliser

1. Cliquez sur le bouton de branche à droite de la barre d'état, puis sur **Valider...**, ou appuyez sur **Ctrl+K**.
2. Cochez les fichiers à valider (**Tout** les coche tous). Cliquez sur un fichier pour voir ses différences.
3. Écrivez un message et cliquez sur **Valider**, ou appuyez sur **Ctrl+Entrée** dans le message. La barre d'état confirme la validation et la fenêtre reste ouverte pour la suivante.
4. Pour jeter des modifications, cochez les fichiers et cliquez sur **Annuler les modifications**. Les fichiers reviennent à la dernière validation et les nouveaux fichiers sont supprimés : lisez bien la confirmation.

Arrêtez la lecture (Play) avant de valider. Une validation ne s'annule pas avec **Ctrl+Z**.

## Fenêtres liées

- [Gestion de versions](version_control.md) : le journal Git avec toutes les validations.
- [Résoudre les conflits](vcs_conflicts.md) : réparer les fichiers en conflit avant de valider.
