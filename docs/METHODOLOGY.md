# Méthodologie

Objectif : pouvoir comparer des outils hétérogènes sur une base commune, sans biais
de familiarité et sans dépendre du contexte d'un projet particulier.

## 1. Cas d'usage de référence

Avant d'évaluer un outil, fixe un **cas d'usage minimal mais représentatif** que
chaque module `tools/<nom-outil>` devra reproduire à l'identique, par exemple :
« générer un rapport tabulaire d'une dizaine de lignes, avec un total et un
graphique simple ». Le but est que le code dummy de chaque outil résolve
exactement le même problème — c'est ce qui rend la comparaison honnête.

## 2. Axes d'évaluation

Pour chaque outil, renseigner dans `docs/COMPARISON.md` :

- **Features** — ce que l'outil sait faire nativement (formats de sortie,
  templating, graphiques, pagination, export, i18n...)
- **Contraintes** — dépendances, licence, empreinte mémoire/temps de build,
  compatibilité de version Java, verrouillage propriétaire, maintenance/activité
  du projet
- **Coûts** — gratuit / open-source / licence payante, coût à l'usage
  (ex. par serveur, par utilisateur), coût caché (formation, support)

## 3. Code dummy

Chaque module `tools/<nom-outil>` doit :
- être **autonome** (buildable indépendamment via son propre `pom.xml`)
- contenir le **minimum de code** nécessaire pour produire un rapport avec l'outil
- écrire sa sortie dans `reports-demo/generated/<nom-outil>/`
- documenter dans son propre `README.md` local les étapes pour l'exécuter

## 4. Démo des rapports

Pour chaque rapport généré, noter dans `reports-demo/README.md` (ou dans un
fichier dédié `reports-demo/generated/<nom-outil>/NOTES.md`) :
- ce que montre concrètement le rapport (aperçu / capture)
- s'il est **statique** (HTML figé, PDF, image) ou **interactif**
  (tri de colonnes, filtres, drill-down, zoom sur graphique...)
- les limites observées pendant la génération

## 5. Neutralité

Le tableau comparatif doit rester factuel et vérifiable : chaque affirmation
(licence, coût, feature) doit pouvoir être reliée à une source (documentation
officielle, code testé). Éviter les jugements de valeur non étayés.
