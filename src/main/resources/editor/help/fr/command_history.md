# Historique des commandes

L'Historique des commandes est la liste des changements que vous avez faits dans l'éditeur, dans l'ordre. C'est la version longue d'**Annuler** : au lieu d'appuyer dix fois sur Ctrl+Z, vous cliquez sur le point où vous voulez revenir.

## Ce que c'est

Chaque changement, comme déplacer un objet, le renommer ou ajouter un composant, est enregistré comme une **commande**. La fenêtre a deux parties :

- **Commandes exécutées** : les changements actuellement appliqués, du plus ancien au plus récent.
- **Commandes annulées** : les changements que vous avez annulés. Ils restent ici pour être rétablis, jusqu'à votre prochain changement.

En haut se trouvent les boutons **Annuler**, **Rétablir** et **Effacer l'historique**.

## Comment l'utiliser

1. Faites quelques changements dans la scène et regardez-les apparaître dans la liste.
2. Cliquez sur une commande, ou faites un clic droit et choisissez **Annuler jusqu'ici**, pour annuler tous les changements faits après elle.
3. Faites un clic droit sur une commande annulée et choisissez **Rétablir jusqu'ici** pour réappliquer les changements jusqu'à celle-ci.
4. Utilisez **Ctrl+Z** et **Ctrl+Y** pour avancer d'un pas à la fois.
5. Cliquez sur **Effacer l'historique** pour vider la liste. Les changements effacés ne peuvent plus être annulés : ne le faites que si vous êtes sûr.

Faire un nouveau changement après une annulation supprime les commandes annulées : l'historique suit un seul chemin.

## Fenêtres liées

- [Hiérarchie de la scène](hierarchy.md) et [Propriétés](properties.md) : là où se font la plupart des changements.
- [Console](console.md) : les lignes Action notent les mêmes changements.
