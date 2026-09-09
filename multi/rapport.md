# Runtime analysis report

> Produced from the machine outputs of JaCoCo, async-profiler and Arthas.
> The same content, navigable, is in `index.html` beside this file:
> a forge shows an `.html` as source code, hence this version.

---

## Full scenario

- **Command**: `java -jar sample-app.jar --iterations 8000000`
- **Root method**: `lab.sample.RoutePlanner::travelTimeMinutes`
- **Start**: `2026-09-09 13:23:11`
- **Duration**: `33.0 s`
- **Java**: `Ubuntu 21.0.10`
- **Machine**: `Linux 6.18.44-fc-v24 (amd64)`

### Where the code went

**82% of the analysed instructions** ran. The denominator is all the bytecode given to be analysed: if the analysis covers a whole dependency, most of it has by construction no reason to run, and this rate then says mostly how big that dependency is.

| Package | Instructions covered |
|---|---:|
| `lab.sample` | 95% |
| `lab.sample.comfort` | 100% |
| `lab.sample.model` | 84% |
| `lab.sample.speed` | 82% |
| `lab.sample.terrain` | 97% |
| `lab.sample.traffic` | 100% |
| `lab.sample.transfer` | 95% |
| `lab.sample.weather` | 96% |

1 package was not touched at all.

**Never executed** (4) — `lab.sample.export.CsvExporter`, `lab.sample.export.ItineraryExporter`, `lab.sample.speed.PlaneSpeed`, `lab.sample.speed.SpeedModel`

### Where the time went

7735 samples. Folding the JDK, the virtual machine's own machinery and the hidden packages attributes their time to the application method that called them.

| Method | Share |
|---|---:|
| `lab.sample.Main.main` | 100% |
| `lab.sample.RoutePlanner.travelTimeMinutes` | 99% |
| `lab.sample.comfort.Breaks.totalMinutes` | 21% |
| `lab.sample.comfort.Breaks.count` | 21% |
| `lab.sample.RoutePlanner.legMinutes` | 15% |
| `lab.sample.terrain.Terrain.slowdownFactor` | 14% |
| `lab.sample.terrain.Elevation.gradePercentAt` | 10% |
| `lab.sample.terrain.Terrain.penaltyFor` | 2% |
| `lab.sample.model.Leg.from` | 0% |
| `lab.sample.transfer.Connections.waitMinutes` | 0% |
| `lab.sample.transfer.Connections.transferMinutes` | 0% |
| `lab.sample.transfer.Timetable.frequencyMinutes` | 0% |

### With which values

**`lab.sample.RoutePlanner.legMinutes`** — 7 calls observed

| # | Arguments received | Value returned |
|---:|---|---|
| 1 | `Leg[from=Auch, to=Foix, distanceKm=60.0]`, `lab.sample.speed.WalkSpeed@253d9f73`, `WALK` | `752.5631999999999` |
| 2 | `Leg[from=Foix, to=Toulouse, distanceKm=7.0]`, `lab.sample.speed.WalkSpeed@253d9f73`, `WALK` | `87.63215999999997` |
| 3 | `Leg[from=Toulouse, to=Albi, distanceKm=14.0]`, `lab.sample.speed.WalkSpeed@253d9f73`, `WALK` | `176.80319999999998` |
| 4 | `Leg[from=Auch, to=Foix, distanceKm=54.0]`, `lab.sample.speed.CarSpeed@142269f2`, `CAR` | `29.720971636363636` |
| 5 | `Leg[from=Foix, to=Toulouse, distanceKm=55.0]`, `lab.sample.speed.TrainSpeed@55322aab`, `TRAIN` | `36.66666666666667` |
| 6 | `Leg[from=Toulouse, to=Albi, distanceKm=2.0]`, `lab.sample.speed.TrainSpeed@55322aab`, `TRAIN` | `1.3333333333333335` |
| 7 | `Leg[from=Toulouse, to=Albi, distanceKm=56.0]`, `lab.sample.speed.BikeSpeed@2b4c1d96`, `BIKE` | `245.12768` |

**`lab.sample.RoutePlanner.travelTimeMinutes`** — 2 calls observed

| # | Arguments received | Value returned |
|---:|---|---|
| 1 | `Trip[id=TRIP-172, mode=CAR, weather=RAIN, timeOfDay=QUIET, withLuggage=false, legs=[Leg…` | `29.720971636363636` |
| 2 | `Trip[id=TRIP-173, mode=TRAIN, weather=RAIN, timeOfDay=QUIET, withLuggage=false, legs=[L…` | `64.0` |

---

## Scenario 2

- **Command**: `java -jar sample-app.jar --iterations 4000000`
- **Root method**: `lab.sample.terrain.Terrain::slowdownFactor`
- **Start**: `2026-09-09 13:22:59`
- **Duration**: `9.0 s`
- **Java**: `Ubuntu 21.0.10`
- **Machine**: `Linux 6.18.44-fc-v24 (amd64)`

### Where the code went

**82% of the analysed instructions** ran. The denominator is all the bytecode given to be analysed: if the analysis covers a whole dependency, most of it has by construction no reason to run, and this rate then says mostly how big that dependency is.

| Package | Instructions covered |
|---|---:|
| `lab.sample` | 95% |
| `lab.sample.comfort` | 100% |
| `lab.sample.model` | 84% |
| `lab.sample.speed` | 82% |
| `lab.sample.terrain` | 97% |
| `lab.sample.traffic` | 100% |
| `lab.sample.transfer` | 95% |
| `lab.sample.weather` | 96% |

1 package was not touched at all.

**Never executed** (4) — `lab.sample.export.CsvExporter`, `lab.sample.export.ItineraryExporter`, `lab.sample.speed.PlaneSpeed`, `lab.sample.speed.SpeedModel`

### Where the time went

195 samples. Folding the JDK, the virtual machine's own machinery and the hidden packages attributes their time to the application method that called them.

| Method | Share |
|---|---:|
| `lab.sample.Main.main` | 100% |
| `lab.sample.RoutePlanner.travelTimeMinutes` | 100% |
| `lab.sample.RoutePlanner.legMinutes` | 100% |
| `lab.sample.terrain.Terrain.slowdownFactor` | 100% |
| `lab.sample.terrain.Elevation.gradePercentAt` | 53% |
| `lab.sample.model.Leg.from` | 9% |
| `lab.sample.terrain.Terrain.penaltyFor` | 4% |

---

## Scenario 1

- **Command**: `java -jar sample-app.jar --iterations 4000000`
- **Root method**: `lab.sample.RoutePlanner::travelTimeMinutes`
- **Start**: `2026-09-09 13:22:47`
- **Duration**: `8.0 s`
- **Java**: `Ubuntu 21.0.10`
- **Machine**: `Linux 6.18.44-fc-v24 (amd64)`

### Where the code went

**82% of the analysed instructions** ran. The denominator is all the bytecode given to be analysed: if the analysis covers a whole dependency, most of it has by construction no reason to run, and this rate then says mostly how big that dependency is.

| Package | Instructions covered |
|---|---:|
| `lab.sample` | 95% |
| `lab.sample.comfort` | 100% |
| `lab.sample.model` | 84% |
| `lab.sample.speed` | 82% |
| `lab.sample.terrain` | 97% |
| `lab.sample.traffic` | 100% |
| `lab.sample.transfer` | 95% |
| `lab.sample.weather` | 96% |

1 package was not touched at all.

**Never executed** (4) — `lab.sample.export.CsvExporter`, `lab.sample.export.ItineraryExporter`, `lab.sample.speed.PlaneSpeed`, `lab.sample.speed.SpeedModel`

### Where the time went

1418 samples. Folding the JDK, the virtual machine's own machinery and the hidden packages attributes their time to the application method that called them.

| Method | Share |
|---|---:|
| `lab.sample.Main.main` | 100% |
| `lab.sample.RoutePlanner.travelTimeMinutes` | 98% |
| `lab.sample.comfort.Breaks.totalMinutes` | 81% |
| `lab.sample.comfort.Breaks.count` | 81% |
| `lab.sample.RoutePlanner.legMinutes` | 14% |
| `lab.sample.terrain.Terrain.slowdownFactor` | 13% |
| `lab.sample.terrain.Elevation.gradePercentAt` | 8% |
| `lab.sample.transfer.Connections.waitMinutes` | 2% |
| `lab.sample.transfer.Connections.transferMinutes` | 2% |
| `lab.sample.transfer.Timetable.frequencyMinutes` | 2% |
| `lab.sample.model.Leg.from` | 1% |
| `lab.sample.RoutePlanner.<clinit>` | 1% |
