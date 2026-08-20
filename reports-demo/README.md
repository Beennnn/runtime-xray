# Sorties réellement produites

Ce dossier contient ce que les outils ont **effectivement écrit** en analysant
[`sample-app`](../sample-app/) — pas des captures d'écran, pas des exemples reconstitués.

**→ [Tout est navigable en ligne](https://beennnn.github.io/runtime-xray/)** sans rien cloner.

| Dossier | Outil | Ce qu'on y voit |
|---|---|---|
| `generated/jacoco/html/` | JaCoCo | Site de couverture, **code source colorié ligne à ligne**. Point d'entrée : `index.html` |
| `generated/jacoco-focused/html/` | JaCoCo (CLI) | Le même, **restreint aux classes réellement exécutées** — le code jamais atteint disparaît du rapport |
| `generated/async-profiler/` | async-profiler | `tree.html` (arbre d'appel dépliable) et `flamegraph.html` |
| `generated/jfr/` | JFR sous Java 21 | Résumé et échantillons d'exécution — **pas** de traçage de méthodes : il n'existe pas avant Java 25 |
| `generated/jfr-jdk25/` | JFR sous Java 25 | `method-timing.txt` (16 000 000 d'invocations comptées exactement) et un extrait de `MethodTrace` |

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

## Ce que ces sorties ne montrent pas

**Les valeurs passées en paramètres.** JFR affiche la signature `frequencyMinutes(Leg)` et
la pile d'appel complète, mais jamais la valeur du `Leg`. C'est le trou du socle gratuit,
vérifié et non supposé — voir [RESULTS.md](../docs/RESULTS.md).
