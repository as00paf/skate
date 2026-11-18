# Assistant de projet

L'Assistant de projet crée un nouveau projet : un dossier avec tout ce dont un nouveau jeu a besoin pour démarrer, dont une première scène.

## Ce que c'est

Un **projet** est un dossier normal de votre ordinateur. L'assistant demande deux choses :

- **Nom du projet** : le nom de votre jeu. Certains caractères sont interdits dans les noms de dossiers : `< > : / \ | ? *`.
- **Emplacement** : le dossier dans lequel le dossier du projet est créé.

Il propose aussi **Copier les assets par défaut du moteur**, activé par défaut. Activé, les modèles, animations, textures et sons du moteur sont copiés dans les dossiers `Assets` du projet, où vous pouvez les modifier. Désactivé, le projet utilise les fichiers du moteur et reste léger ; seuls les shaders de base et les réglages du skateur sont copiés. Les deux sortes de projet se jouent et s'exportent de la même façon. Ce choix ne s'applique qu'à la création du projet et n'est pas enregistré avec lui.

Sous les champs, l'assistant indique si le nom et l'emplacement sont valides et montre les dossiers qu'il va créer.

## Comment l'utiliser

1. Choisissez **Fichier > Nouveau projet**, ou cliquez sur **Nouveau projet** dans [Changer de projet](project_switcher.md).
2. Tapez un **Nom du projet**.
3. Cliquez sur **Parcourir...** pour choisir l'**Emplacement**. Un message vert indique que le dossier convient.
4. Cliquez sur **Créer le projet**, ou appuyez sur **Entrée**. L'éditeur crée le dossier et ouvre le nouveau projet.
5. Cliquez sur **Annuler**, ou appuyez sur **Échap**, pour fermer l'assistant sans rien créer.
6. Si vous avez déjà un projet, cliquez plutôt sur **Ouvrir un projet**.

Une fois le projet créé, lisez [Prise en main](getting_started.md) pour vos premiers pas.

## Fenêtres liées

- [Changer de projet](project_switcher.md) : ouvrir un projet que vous avez déjà.
- [Projet](project.md) : les fichiers du nouveau projet.
- [Paramètres du projet](project_settings.md) : nom, version et réglages du jeu.
