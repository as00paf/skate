# Gestion de versions

La Gestion de versions garde l'historique de votre projet avec **Git**. Cette fenêtre est le **journal Git** du projet : chaque validation de chaque branche locale, de la plus récente à la plus ancienne. Inutile d'installer Git : l'éditeur l'intègre.

## Ce que c'est

Quelques mots :

- **Dépôt** : le dossier du projet une fois que Git garde son historique.
- **Validation** (commit) : un instantané enregistré des fichiers choisis, avec un court message qui dit ce que vous avez fait.
- **Branche** : une ligne d'historique séparée. Créez une branche pour essayer une idée ; revenez en arrière si elle ne marche pas.
- **Conflit** : le même fichier a été modifié sur deux branches et Git ne peut pas choisir. C'est vous qui décidez quelle version garder.

La fenêtre montre un tableau des validations :

- **Graphe** : une ligne de couleur par ligne d'historique. Un point est une validation ; les lignes qui se séparent sont des branches, celles qui se rejoignent des fusions. `[main]` marque la validation où pointe une branche ; la branche actuelle est en doré.
- **Message**, **Auteur** et **Date** de chaque validation.

À droite, le message et les fichiers de la validation sélectionnée. Cliquez sur un fichier pour voir ce que cette validation y a changé.

## Comment l'utiliser

1. Ouvrez-la depuis **Affichage > Fenêtres > Projet**, ou par **Historique...** dans le bouton de branche à droite de la barre d'état. Si le projet n'a pas encore d'historique, cliquez sur **Initialiser**.
2. Cliquez sur une validation pour voir ses fichiers, puis sur un fichier pour voir ses différences.
3. Choisissez une branche dans **Branche** pour ne voir que ses validations, ou tapez dans le filtre pour trouver une validation par message, auteur ou identifiant. Le graphe est masqué tant qu'un filtre de texte est actif.
4. Le travail courant se fait depuis le **bouton de branche** de la barre d'état, qui montre la branche actuelle et le nombre de fichiers modifiés :
   - **Valider...** (Ctrl+K) et **Modifications...** ouvrent la fenêtre [Valider](vcs_commit.md).
   - **Nouvelle branche...** crée une branche. Chaque branche de la liste a **Extraire**, **Nouvelle branche depuis...** et **Supprimer...**.
   - **Résoudre les conflits...** ouvre la fenêtre des [conflits](vcs_conflicts.md).
   - **Mettre à jour le projet** nécessite un dépôt distant, qui viendra plus tard.

Les actions Git ne s'annulent pas avec **Ctrl+Z** : une validation reste dans l'historique. Pour tout ce que l'éditeur ne fait pas, lancez `git` dans le [Terminal](shell.md).

## Fenêtres liées

- [Valider les modifications](vcs_commit.md) : choisir des fichiers, écrire un message, valider.
- [Résoudre les conflits](vcs_conflicts.md) : garder votre version ou prendre la leur.
- [Projet](project.md) : les fichiers sous gestion de versions.
- [Terminal](shell.md) : lancer d'autres commandes Git.
