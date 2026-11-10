# Exporter le projet

Exporter le projet construit une copie de votre jeu que d'autres peuvent jouer sans l'éditeur. C'est l'étape entre « ça marche chez moi » et « je l'envoie à un ami ».

## Ce que c'est

L'export rassemble les scènes et ressources dont votre jeu a besoin, les met dans un seul fichier (`game.bin` avec un `manifest.json`) et, pour un build Windows, ajoute le programme du jeu et un lanceur `run-game.bat`. Le résultat va dans un dossier de sortie que vous choisissez.

Il y a deux cibles :

- **Build Windows** : un dossier de jeu complet que vous pouvez zipper et partager. Il a besoin du programme du jeu (le **jar du jeu**). Si la fenêtre ne le trouve pas, lancez `gradlew gameJar` (par exemple depuis le [Shell](shell.md)) ou choisissez un jar vous-même.
- **Pack de ressources seul** : seulement `game.bin` et `manifest.json`, pour un programme de jeu existant.

## Comment l'utiliser

1. Enregistrez votre travail et choisissez **Fichier > Exporter le projet**.
2. Choisissez la **Cible**.
3. Cochez les **Scènes** à inclure. La scène de démarrage est toujours incluse.
4. Réglez les options :
   - **Compresser les ressources** rend le build plus petit mais un peu plus lent à charger.
   - **Ressources référencées seulement** laisse de côté les fichiers qu'aucune scène, aucun préfab ni aucun réglage n'utilise.
5. Choisissez le **Dossier de sortie**, ou cliquez sur **Par défaut**. Ne choisissez pas le dossier du projet lui-même.
6. Cliquez sur **Exporter** et attendez la fin des étapes : enregistrement, collecte, empaquetage, vérification et copie.
7. Une fois fini, cliquez sur **Ouvrir le dossier** pour voir le résultat. Double-cliquez sur `run-game.bat` pour jouer.

Le jeu démarre avec le moteur graphique choisi dans les [Paramètres du projet](project_settings.md). Vulkan est expérimental : testez le jeu exporté avant de le partager.

Si l'export échoue, la fenêtre explique pourquoi et quoi faire. **Retour aux options** permet de changer un réglage et de réessayer.

## Fenêtres liées

- [Paramètres du projet](project_settings.md) : scène de démarrage, moteur graphique et qualité.
- [Shell](shell.md) : lancer `gradlew gameJar` ou la CLI Skate.
- [Console](console.md) : les détails d'un export qui a échoué.
