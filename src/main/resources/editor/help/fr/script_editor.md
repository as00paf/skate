# Éditeur de scripts

L'Éditeur de scripts est un éditeur de texte pour les scripts de votre jeu. Un script est un petit programme qui donne à un objet son propre comportement, par exemple « tourner lentement » ou « ouvrir la porte quand le joueur approche ».

## Ce que c'est

Les scripts Skate sont des fichiers Kotlin script (`.kts`) rangés dans le projet, en général dans `Assets/Scripts`. L'éditeur ouvre chaque script dans un onglet, colore le code, propose des noms pendant que vous tapez et vérifie les erreurs du script.

Sous le texte se trouve la liste des erreurs. Les erreurs de **Compilation** sont des fautes trouvées avant que le script tourne, comme une faute de frappe. Les erreurs d'**Exécution** arrivent pendant le jeu. Chaque erreur indique sa ligne ; cliquez dessus pour y aller.

## Comment l'utiliser

1. Double-cliquez sur un fichier `.kts` dans la fenêtre [Projet](project.md) pour l'ouvrir ici.
2. Pour créer un script, tapez un nom dans le champ en haut et cliquez sur **Nouveau**. Le script part d'un modèle qui compile déjà.
3. Écrivez votre code. Appuyez sur **Ctrl+Espace** pour avoir des suggestions.
4. Cliquez sur **Vérifier** pour chercher les erreurs sans enregistrer.
5. Appuyez sur **Ctrl+S** ou cliquez sur **Enregistrer** pour enregistrer. Si le jeu tourne, le script est rechargé tout de suite : vous voyez le changement sans arrêter.
6. Utilisez **Ctrl+F** pour chercher du texte, **Ctrl+H** pour le remplacer et **Ctrl+G** pour aller à une ligne.
7. Fermer un onglet qui a des changements non enregistrés demande confirmation. **Abandonner les modifications** les jette.

Pour qu'un script tourne, ajoutez un composant **Script** à un objet dans [Propriétés](properties.md) et choisissez le script.

## Fenêtres liées

- [Projet](project.md) : trouver et ranger vos scripts.
- [Propriétés](properties.md) : attacher un script à un objet.
- [Console](console.md) : les messages et erreurs écrits par les scripts.
