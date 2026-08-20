# Rapport d'analyse dynamique

> Généré depuis les sorties machine des outils par `tools/summary/build-markdown.py`.
> **Ce fichier existe parce que GitHub affiche un `.html` comme du code source, pas comme
> une page.** Le Markdown, lui, est rendu nativement — c'est le seul format qui se lit
> directement dans la forge.
>
> Version navigable : [GitHub Pages](https://beennnn.github.io/runtime-xray/) ·
> Version hors ligne : [le rapport en un fichier](https://github.com/Beennnn/runtime-xray/releases/latest)

## 1. Par où le code est passé — JaCoCo

**91.1 % des instructions** et **81.8 % des branches** ont été exécutées.

| Package | Instructions | Branches |
|---|---:|---:|
| `lab.sample` | 92 % | 83 % |
| `lab.sample.comfort` | 100 % | 100 % |
| `lab.sample.model` | 84 % | 0 % |
| `lab.sample.speed` | 82 % | 73 % |
| `lab.sample.terrain` | 97 % | 82 % |
| `lab.sample.traffic` | 100 % | 100 % |
| `lab.sample.transfer` | 95 % | 75 % |
| `lab.sample.weather` | 96 % | 89 % |

**Jamais exécuté** — `lab.sample.speed.PlaneSpeed`

## 2. Où le temps est passé — async-profiler

578 échantillons. Méthodes où le temps est réellement passé :

| Méthode | Part |
|---|---:|
| `lab.sample.terrain.Elevation.gradePercentAt` | 20.8 % |
| `lab.sample.terrain.Terrain.slowdownFactor` | 14.2 % |
| `java.lang.StringConcatHelper.newString` | 11.8 % |
| `stub:fmod` | 8.5 % |
| `jdk.internal.misc.Unsafe.allocateUninitializedArray` | 7.8 % |
| `jbyte_disjoint_arraycopy` | 6.4 % |
| `lab.sample.terrain.Terrain.penaltyFor` | 4.8 % |
| `__psynch_cvbroad` | 3.3 % |
| `lab.sample.RoutePlanner.travelTimeMinutes` | 2.9 % |
| `lab.sample.weather.WeatherPenalty.slowdownFactor` | 1.6 % |
| `lab.sample.RoutePlanner.legMinutes` | 1.4 % |
| `lab.sample.model.Trip.legs` | 1.4 % |

## 3. Avec quelles valeurs — Arthas

Valeurs réellement passées à `RoutePlanner.travelTimeMinutes`, telles qu'Arthas les a lues :

```
ts=2026-08-20 20:08:02.619; [cost=5.866458ms] result=@ArrayList[
    @String[TRIP-56],
    @Mode[CAR],
    @Weather[SUNNY],
    @TimeOfDay[RUSH_HOUR],
    @Boolean[false],
    @Double[52.00806981818182],
ts=2026-08-20 20:08:02.621; [cost=0.031375ms] result=@ArrayList[
    @String[TRIP-57],
    @Mode[TRAIN],
    @Weather[SUNNY],
    @TimeOfDay[RUSH_HOUR],
    @Boolean[false],
    @Double[56.833333333333336],
ts=2026-08-20 20:08:02.622; [cost=0.019334ms] result=@ArrayList[
    @String[TRIP-58],
    @Mode[BIKE],
    @Weather[SUNNY],
    @TimeOfDay[RUSH_HOUR],
    @Boolean[false],
    @Double[365.7556000000001],
```

---

Les trois outils sont gratuits, open source et fonctionnent hors ligne.
Méthode : [docs/METHODOLOGY.md](../docs/METHODOLOGY.md).
