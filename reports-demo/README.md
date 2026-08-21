# Sorties réellement produites

Ce dossier contient ce que les outils ont **effectivement écrit** en analysant
[`sample-app`](../sample-app/) — pas des captures d'écran, pas des exemples reconstitués.

**→ [Tout est navigable en ligne](https://beennnn.github.io/runtime-xray/)** sans rien cloner.

Les exécutions de [`executions/`](executions/) portent en plus un dossier `exports/` : les
mêmes mesures réécrites pour d'autres outils — `cpuprofile` (speedscope), LCOV (éditeurs,
suivi de couverture), et les valeurs capturées en JSON. Voir
[Reprendre le résultat dans un autre outil](../docs/outil/exports.md).

| Dossier | Outil | Ce qu'on y voit |
|---|---|---|
| `generated/jacoco/html/` | JaCoCo | Site de couverture, **code source colorié ligne à ligne**. Point d'entrée : `index.html` |
| `generated/jacoco-focused/html/` | JaCoCo (CLI) | Le même, **restreint aux classes réellement exécutées** — le code jamais atteint disparaît du rapport |
| `generated/async-profiler/` | async-profiler | `tree.html` (arbre d'appel dépliable) et `flamegraph.html` |
| `generated/jfr/` | JFR sous Java 21 | Résumé et échantillons d'exécution — **pas** de traçage de méthodes : il n'existe pas avant Java 25 |
| `generated/jfr-jdk25/` | JFR sous Java 25 | `method-timing.txt` (16 000 000 d'invocations comptées exactement) et un extrait de `MethodTrace` |
| `generated/arthas/` | Arthas | `watch-params.txt` : **les valeurs réellement passées** · `trace-calltree.txt` : l'arbre d'**un** appel avec les numéros de ligne |

## Comment les regarder

```bash
open reports-demo/generated/jacoco-focused/html/index.html
open reports-demo/generated/async-profiler/tree.html
```

Dans le rapport de couverture, le fichier le plus parlant est
`lab.sample.weather/WeatherPenalty.java.html` : une branche rouge à l'intérieur d'une
méthode exécutée des millions de fois — le cas qu'un pourcentage par classe masque et
qu'un rapport ligne à ligne révèle.

## Les régénérer

```bash
./tools/jacoco/collect.sh && ./tools/jacoco/collect-focused.sh
./tools/async-profiler/collect.sh
./tools/jfr/collect.sh
JAVA_TARGET=25 ./tools/jfr/collect.sh
```

## Le contraste à regarder

Ouvrir `generated/jfr/` puis `generated/arthas/watch-params.txt` : JFR affiche la signature
`travelTimeMinutes(Trip)` et la pile complète, **mais jamais la valeur du `Trip`**. Arthas,
lui, dit `@Mode[CAR] @Weather[SUNNY] @TimeOfDay[RUSH_HOUR]`. C'est toute la différence entre
savoir *qu'un* appel a eu lieu et savoir *lequel*.
