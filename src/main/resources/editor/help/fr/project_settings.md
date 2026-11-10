# Paramètres du projet

Les Paramètres du projet contiennent les réglages de votre jeu. Ils sont enregistrés dans le projet : toutes les personnes qui ouvrent le projet, ainsi que le jeu exporté, utilisent les mêmes valeurs.

## Ce que c'est

Comparez avec les [Paramètres de l'éditeur](editor_settings.md) : ceux-ci concernent **vous** et votre éditeur ; les Paramètres du projet concernent **le jeu**. Les catégories sont :

- **Métadonnées du projet** : le nom, la version, la description et le dossier du projet, ainsi que vos projets récents.
- **Jeu** : la **Scène de démarrage**, qui s'ouvre avec le projet et lance le jeu exporté, et la langue dans laquelle démarrent les textes du jeu.
- **Graphismes** : le rendu du jeu exporté : moteur graphique (OpenGL ou Vulkan), qualité globale, ombres, textures, MSAA, plein écran et synchro verticale. L'éditeur, lui, dessine toujours avec OpenGL.
- **Physique** : combien de pas de physique sont calculés par seconde.
- **Aléatoire** : la **Graine** des nombres aléatoires. La même graine donne les mêmes résultats « aléatoires » à chaque partie, ce qui aide à traquer un bug. **Tirer au hasard** en choisit une nouvelle.
- **Entrées** : les réglages des commandes du jeu.

## Comment l'utiliser

1. Ouvrez un projet, puis choisissez **Paramètres > Paramètres du projet...**.
2. Cliquez sur une catégorie à gauche et changez des valeurs.
3. Cliquez sur **OK** ou **Appliquer** pour enregistrer le projet. **Annuler** jette vos changements.
4. Avant d'exporter, vérifiez la **Scène de démarrage** et la catégorie **Graphismes** : la fenêtre [Exporter le projet](export_wizard.md) les utilise.

## Fenêtres liées

- [Paramètres de l'éditeur](editor_settings.md) : vos préférences personnelles.
- [Exporter le projet](export_wizard.md) : construire le jeu avec ces réglages.
- [Raccourcis clavier](keybindings.md) : les commandes du jeu.
