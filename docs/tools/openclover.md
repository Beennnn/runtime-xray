# OpenClover

> **Statut : 📄 sur documentation** — non exécuté ici.

## Ce que c'est
Un outil de couverture Java, héritier de Clover (Atlassian), passé en open source.

## Sa philosophie
**Relier la couverture aux tests qui la produisent.** Là où JaCoCo répond « cette ligne
a-t-elle été exécutée ? », OpenClover répond « **par quel test** ? ». Il conserve le lien
entre chaque test et les lignes qu'il a touchées.

## Ce qu'il sait faire
Lignes et branches ✅ · **couverture par test** ✅ · couverture par méthode ✅ ·
arbre d'appel ❌ · valeurs des paramètres ❌.

## Son interface
Rapport HTML avec code source annoté, comparable à JaCoCo, enrichi de la vue par test.

## Comment on navigue dedans
Page web autonome ✅, comme JaCoCo. Plugins IDE historiques, à l'état de maintenance
incertain *(à vérifier)*.

## Mise en œuvre
Instrumentation à la **compilation** (et non à l'exécution comme JaCoCo) : il faut
brancher son plugin dans le build. Plus intrusif.

## Licence et coût
**Apache 2.0**, **0 €**. Hors ligne.

## Ce qu'on peut en espérer
Intéressant seulement si la question « quel test couvre quoi ? » devient centrale. Pour le
besoin décrit — la couverture d'une **exécution**, pas d'une suite de tests — JaCoCo est
plus direct et mieux soutenu.

## Comparable à

- **[JaCoCo](jacoco.md)** — **Le concurrent direct**, et celui qui est retenu ici : instrumentation à l'exécution plutôt qu'à la compilation, écosystème nettement plus vivant. OpenClover ne reprend l'avantage que si la question devient « quel test couvre quoi ? ».
- **[SonarQube](sonarqube.md)** — Même rôle d'afficheur pour l'un comme pour l'autre.

## Facile / moins facile

**Ce qui est facile.** Lire le rapport : même famille que JaCoCo, mêmes réflexes.

**Ce qui l'est moins.** **L'insérer dans le build** — l'instrumentation se fait à la compilation, donc elle touche le processus de construction. Et évaluer la vitalité du projet, moins soutenu que JaCoCo.
