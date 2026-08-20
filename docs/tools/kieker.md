# Kieker

> **Statut : 📄 sur documentation** — non exécuté ici.

## Ce que c'est
Un cadre de **surveillance et d'analyse d'architecture** issu de la recherche académique
(université de Kiel). Il enregistre des traces d'exécution puis les analyse hors ligne pour
en **reconstruire la structure** du logiciel.

## Sa philosophie
**La trace comme matériau d'analyse, pas comme rapport.** Les autres outils présentent ce
qu'ils ont mesuré ; Kieker produit un flux de traces destiné à être *traité* — pour en
dériver des diagrammes de dépendances, des diagrammes de séquence, des vues de composants.

**C'est le seul outil recensé dont l'objectif déclaré coïncide avec le « but final » du
brief** : analyser, restructurer et redéfinir le code à partir des données d'exécution.

## Ce qu'il sait faire
Traces d'exécution complètes ✅ · arbre d'appel ✅ · valeurs des paramètres ✅ (sondes
paramétrables) · **reconstruction de diagrammes d'architecture** ✅ · couverture de lignes
⚠️ indirecte.

## Son interface
Pas d'interface interactive : des **artefacts générés** (diagrammes de dépendances et de
séquence) et des outils d'analyse en ligne de commande.

## Comment on navigue dedans
Les diagrammes se lisent hors IDE ✅, mais ce n'est pas de la navigation dans le code : on
regarde une vue d'ensemble, on ne clique pas jusqu'à la ligne.

## Mise en œuvre
⚠️ **Le plus lourd du panel.** Instrumentation à configurer, chaîne d'analyse à monter.
C'est un cadre de recherche, pas un outil qu'on lance en trois clics — lourdement pénalisé
par le critère n° 1.

## Licence et coût
**Apache 2.0**, **0 €**. Hors ligne.

## Ce qu'on peut en espérer
À considérer **en second temps**, une fois le diagnostic de base outillé, si l'objectif de
restructuration devient prioritaire. C'est un projet en soi, pas un complément.

## Facile / moins facile

**Ce qui est facile.** Rien, honnêtement. C'est le prix de ce qu'il vise.

**Ce qui l'est moins.** **Tout** : instrumentation à configurer, chaîne d'analyse à monter, résultats à interpréter. En échange, c'est le seul qui adresse le « but final » du brief.
