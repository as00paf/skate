# Résoudre les conflits

Quand le même fichier a été modifié sur deux branches, Git ne peut pas choisir quelle version garder : le fichier est **en conflit**. Cette fenêtre vous laisse choisir, fichier par fichier. Elle s'ouvre seule quand des conflits apparaissent, et le bouton de branche de la barre d'état reste rouge tant qu'ils ne sont pas résolus.

## Ce que c'est

- La **liste** à gauche contient chaque fichier en conflit.
- La **comparaison** à droite montre votre version (la branche actuelle) contre la leur (l'autre branche).
- **Garder la mienne** garde votre version ; **Prendre la leur** la remplace par celle de l'autre branche. Dans les deux cas, c'est le fichier entier qui est choisi : il n'y a pas de fusion ligne par ligne.

Les scènes en conflit ne s'ouvrent pas dans l'éditeur tant qu'elles ne sont pas résolues.

## Comment l'utiliser

1. Cliquez sur un fichier pour comparer les deux versions.
2. Cochez un ou plusieurs fichiers (ou cliquez sur un seul), puis cliquez sur **Garder la mienne** ou **Prendre la leur**.
3. Quand le dernier conflit a disparu, cliquez sur **Valider...** pour valider la fusion dans la fenêtre [Valider](vcs_commit.md).

## Fenêtres liées

- [Valider les modifications](vcs_commit.md) : valider la fusion.
- [Gestion de versions](version_control.md) : le journal Git.
