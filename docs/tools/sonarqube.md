# SonarQube

> **Statut : 📄 sur documentation** — non exécuté ici.

## Ce que c'est
Une plateforme d'analyse de qualité de code. Elle n'est **pas** un outil d'analyse
dynamique : elle ne mesure rien à l'exécution. Elle figure ici pour une seule raison —
c'est **le meilleur afficheur de la couverture produite par JaCoCo**.

## Sa philosophie
**Rendre la qualité lisible par ceux qui ne lisent pas le code.** Tableaux de bord,
tendances, seuils, historique : Sonar s'adresse autant à un responsable qu'à un développeur.
C'est exactement le public visé par le critère n° 2.

## Ce qu'il sait faire
Affiche la couverture ✅ (en consommant le XML JaCoCo) · historique et tendances ✅ ·
analyse statique ✅ · arbre d'appel ❌ · valeurs des paramètres ❌.

## Son interface
Interface web complète : navigation par projet, package, fichier, jusqu'au **code source
annoté**. Plus soignée que le HTML brut de JaCoCo, et avec l'historique en plus.

## Comment on navigue dedans
Web ✅ · décoration native des merge requests GitLab et pull requests GitHub ✅ ·
SonarLint dans l'IDE ✅.

## Mise en œuvre
⚠️ Un **serveur à installer et maintenir**. L'édition Community s'auto-héberge, donc
**compatible hors ligne** — contrairement à SonarCloud, éliminé par la contrainte.

## Licence et coût
**Community Edition** : gratuite, auto-hébergée. Éditions supérieures : commerciales.

## Ce qu'on peut en espérer
Un **canal de diffusion** pour la couverture, pas une source de données supplémentaire.
Pertinent seulement si l'on veut un historique et un portail permanent — sinon le rapport
HTML de JaCoCo suffit et ne demande aucune infrastructure. C'est l'arbitrage n° 3.
