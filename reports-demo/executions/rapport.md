# Rapport d'analyse dynamique

> Produit depuis les sorties machine de JaCoCo, async-profiler et Arthas.
> Le même contenu, navigable, est dans `index.html` à côté de ce fichier :
> une forge affiche un `.html` comme du code source, d'où cette version.

---

## Scénario complet

- **Commande** : `java -jar /Users/benoitbesson/dev/pro/runtime-xray/sample-app/target/sample-app.jar --iterations 24000000`
- **Méthode racine** : `lab.sample.RoutePlanner::travelTimeMinutes`
- **Paquets masqués** : `org.slf4j, org.apache.commons`
- **Début** : `21/08/2026 00:41:21`
- **Durée** : `62.0 s`
- **Java** : `Eclipse Adoptium 25.0.2`
- **Machine** : `Mac OS X 26.5.2 (aarch64)`

### Par où le code est passé

**82 % des instructions analysées** ont été exécutées. Le dénominateur est tout le bytecode donné à analyser : si l'analyse porte sur une dépendance entière, la plus grande partie n'a par construction aucune raison de tourner, et ce taux dit alors surtout la taille de cette dépendance.

| Paquet | Instructions couvertes |
|---|---:|
| `lab.sample` | 95 % |
| `lab.sample.comfort` | 100 % |
| `lab.sample.model` | 84 % |
| `lab.sample.speed` | 82 % |
| `lab.sample.terrain` | 97 % |
| `lab.sample.traffic` | 100 % |
| `lab.sample.transfer` | 95 % |
| `lab.sample.weather` | 96 % |

1 paquet n'a pas été touché du tout.

**Jamais exécuté** (4) — `lab.sample.export.CsvExporter`, `lab.sample.export.ItineraryExporter`, `lab.sample.speed.PlaneSpeed`, `lab.sample.speed.SpeedModel`

### Où le temps est passé

22975 relevés. Le repli du JDK, des rouages de la machine virtuelle et des paquets masqués rattache leur temps à la méthode applicative qui les a appelés.

| Méthode | Part |
|---|---:|
| `lab.sample.Main.main` | 100 % |
| `lab.sample.RoutePlanner.travelTimeMinutes` | 100 % |
| `lab.sample.RoutePlanner.legMinutes` | 9 % |
| `lab.sample.transfer.Connections.waitMinutes` | 9 % |
| `lab.sample.transfer.Connections.transferMinutes` | 9 % |
| `lab.sample.transfer.Timetable.frequencyMinutes` | 9 % |
| `lab.sample.terrain.Terrain.slowdownFactor` | 8 % |
| `lab.sample.comfort.Breaks.totalMinutes` | 7 % |
| `lab.sample.comfort.Breaks.count` | 7 % |
| `lab.sample.terrain.Terrain.penaltyFor` | 3 % |
| `lab.sample.terrain.Elevation.gradePercentAt` | 2 % |
| `lab.sample.model.Leg.from` | 1 % |

### Avec quelles valeurs

**`lab.sample.RoutePlanner.legMinutes`** — 7 appels observés

| # | Paramètres reçus | Valeur renvoyée |
|---:|---|---|
| 1 | `Leg[from=Albi, to=Montauban, distanceKm=57.0]`, `lab.sample.speed.WalkSpeed@72437d8d`, `WALK` | `717.54336` |
| 2 | `Leg[from=Montauban, to=Castres, distanceKm=4.0]`, `lab.sample.speed.WalkSpeed@72437d8d`, `WALK` | `50.81983999999999` |
| 3 | `Leg[from=Castres, to=Auch, distanceKm=11.0]`, `lab.sample.speed.WalkSpeed@72437d8d`, `WALK` | `139.43776` |
| 4 | `Leg[from=Auch, to=Foix, distanceKm=18.0]`, `lab.sample.speed.WalkSpeed@72437d8d`, `WALK` | `225.76895999999996` |
| 5 | `Leg[from=Montauban, to=Castres, distanceKm=58.0]`, `lab.sample.speed.CarSpeed@1921ad94`, `CAR` | `32.00806981818182` |
| 6 | `Leg[from=Castres, to=Auch, distanceKm=59.0]`, `lab.sample.speed.TrainSpeed@ee86bcb`, `TRAIN` | `39.333333333333336` |
| 7 | `Leg[from=Auch, to=Foix, distanceKm=6.0]`, `lab.sample.speed.TrainSpeed@ee86bcb`, `TRAIN` | `4.0` |

**`lab.sample.RoutePlanner.travelTimeMinutes`** — 2 appels observés

| # | Paramètres reçus | Valeur renvoyée |
|---:|---|---|
| 1 | `Trip[id=TRIP-175, mode=WALK, weather=RAIN, timeOfDay=QUIET, withLuggage=true, legs=[Leg…` | `1523.9659488` |
| 2 | `Trip[id=TRIP-176, mode=CAR, weather=SUNNY, timeOfDay=RUSH_HOUR, withLuggage=false, legs…` | `52.00806981818182` |

---

## Scénario 2

- **Commande** : `java -jar /Users/benoitbesson/dev/pro/runtime-xray/sample-app/target/sample-app.jar --iterations 24000000`
- **Méthode racine** : `lab.sample.terrain.Terrain::slowdownFactor`
- **Paquets masqués** : `org.slf4j, org.apache.commons`
- **Début** : `21/08/2026 00:37:47`
- **Durée** : `211.0 s`
- **Java** : `Eclipse Adoptium 25.0.2`
- **Machine** : `Mac OS X 26.5.2 (aarch64)`

### Par où le code est passé

**82 % des instructions analysées** ont été exécutées. Le dénominateur est tout le bytecode donné à analyser : si l'analyse porte sur une dépendance entière, la plus grande partie n'a par construction aucune raison de tourner, et ce taux dit alors surtout la taille de cette dépendance.

| Paquet | Instructions couvertes |
|---|---:|
| `lab.sample` | 95 % |
| `lab.sample.comfort` | 100 % |
| `lab.sample.model` | 84 % |
| `lab.sample.speed` | 82 % |
| `lab.sample.terrain` | 97 % |
| `lab.sample.traffic` | 100 % |
| `lab.sample.transfer` | 95 % |
| `lab.sample.weather` | 96 % |

1 paquet n'a pas été touché du tout.

**Jamais exécuté** (4) — `lab.sample.export.CsvExporter`, `lab.sample.export.ItineraryExporter`, `lab.sample.speed.PlaneSpeed`, `lab.sample.speed.SpeedModel`

### Où le temps est passé

82665 relevés. Le repli du JDK, des rouages de la machine virtuelle et des paquets masqués rattache leur temps à la méthode applicative qui les a appelés.

| Méthode | Part |
|---|---:|
| `lab.sample.Main.main` | 100 % |
| `lab.sample.RoutePlanner.travelTimeMinutes` | 100 % |
| `lab.sample.RoutePlanner.legMinutes` | 100 % |
| `lab.sample.terrain.Terrain.slowdownFactor` | 100 % |
| `lab.sample.terrain.Terrain.penaltyFor` | 2 % |
| `lab.sample.terrain.Elevation.gradePercentAt` | 1 % |
| `lab.sample.comfort.Breaks.totalMinutes` | 0 % |
| `lab.sample.comfort.Breaks.count` | 0 % |
| `lab.sample.model.Leg.from` | 0 % |
| `lab.sample.terrain.Terrain.applies` | 0 % |
| `ognl.Ognl.getValue` | 0 % |
| `ognl.OgnlRuntime.<clinit>` | 0 % |

### Avec quelles valeurs

**`lab.sample.terrain.Terrain.applies`** — 1 appel observé

| # | Paramètres reçus | Valeur renvoyée |
|---:|---|---|
| 1 | `BIKE` | `true` |

**`lab.sample.terrain.Terrain.penaltyFor`** — 8 appels observés

| # | Paramètres reçus | Valeur renvoyée |
|---:|---|---|
| 1 | `-4.800000000000001`, `BIKE` | `0.8704` |
| 2 | `3.6480000000000006`, `BIKE` | `1.3283200000000002` |
| 3 | `0.768`, `BIKE` | `1.06912` |
| 4 | `-2.688`, `BIKE` | `0.927424` |
| 5 | `-4.608`, `BIKE` | `0.875584` |
| 6 | `4.032`, `BIKE` | `1.36288` |
| 7 | `1.4720000000000002`, `BIKE` | `1.13248` |
| 8 | `-2.112`, `BIKE` | `0.942976` |

---

## Scénario 1

- **Commande** : `java -jar /Users/benoitbesson/dev/pro/runtime-xray/sample-app/target/sample-app.jar --iterations 3 --hold-seconds 18`
- **Méthode racine** : `lab.sample.RoutePlanner::travelTimeMinutes`
- **Paquets masqués** : `org.slf4j, org.apache.commons`
- **Début** : `21/08/2026 00:37:08`
- **Durée** : `35.0 s`
- **Java** : `Eclipse Adoptium 25.0.2`
- **Machine** : `Mac OS X 26.5.2 (aarch64)`

### Par où le code est passé

**78 % des instructions analysées** ont été exécutées. Le dénominateur est tout le bytecode donné à analyser : si l'analyse porte sur une dépendance entière, la plus grande partie n'a par construction aucune raison de tourner, et ce taux dit alors surtout la taille de cette dépendance.

| Paquet | Instructions couvertes |
|---|---:|
| `lab.sample` | 98 % |
| `lab.sample.comfort` | 100 % |
| `lab.sample.model` | 84 % |
| `lab.sample.speed` | 66 % |
| `lab.sample.terrain` | 95 % |
| `lab.sample.traffic` | 17 % |
| `lab.sample.transfer` | 95 % |
| `lab.sample.weather` | 81 % |

1 paquet n'a pas été touché du tout.

**Jamais exécuté** (5) — `lab.sample.export.CsvExporter`, `lab.sample.export.ItineraryExporter`, `lab.sample.speed.PlaneSpeed`, `lab.sample.speed.SpeedModel`, `lab.sample.traffic.RushHourDelay`

### Où le temps est passé

64 relevés. Le repli du JDK, des rouages de la machine virtuelle et des paquets masqués rattache leur temps à la méthode applicative qui les a appelés.

| Méthode | Part |
|---|---:|
| `lab.sample.Main.main` | 100 % |
| `lab.sample.RoutePlanner.<clinit>` | 41 % |
| `lab.sample.RoutePlanner.travelTimeMinutes` | 20 % |
| `lab.sample.transfer.Connections.waitMinutes` | 6 % |
| `lab.sample.transfer.Connections.transferMinutes` | 6 % |
| `lab.sample.Metrics.stage` | 6 % |
| `lab.sample.Scenarios.<clinit>` | 6 % |
| `lab.sample.transfer.Timetable.frequencyMinutes` | 3 % |
| `lab.sample.weather.WeatherPenalty.slowdownFactor` | 3 % |
| `lab.sample.comfort.Breaks.<clinit>` | 3 % |
| `lab.sample.weather.WeatherPenalty$1.<clinit>` | 2 % |
| `lab.sample.speed.Speeds.<clinit>` | 2 % |
