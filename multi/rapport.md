# Runtime analysis report

> Produced from the machine outputs of JaCoCo, async-profiler and Arthas.
> The same content, navigable, is in `index.html` beside this file:
> a forge shows an `.html` as source code, hence this version.

---

## Full scenario (Flight Recorder)

- **Command**: `java -jar sample-app.jar --iterations 8000000`
- **Root method**: `lab.sample.RoutePlanner::travelTimeMinutes`
- **Start**: `2026-09-09 13:37:26`
- **Duration**: `40.0 s`
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

4451 samples. Folding the JDK, the virtual machine's own machinery and the hidden packages attributes their time to the application method that called them.

| Method | Share |
|---|---:|
| `lab.sample.Main.main` | 61% |
| `lab.sample.RoutePlanner.travelTimeMinutes` | 61% |
| `lab.sample.RoutePlanner.legMinutes` | 8% |
| `lab.sample.terrain.Terrain.slowdownFactor` | 7% |
| `lab.sample.comfort.Breaks.totalMinutes` | 6% |
| `lab.sample.comfort.Breaks.count` | 6% |
| `lab.sample.terrain.Elevation.gradePercentAt` | 5% |
| `java.lang.invoke.LambdaForm$MH.0x00007f2400003000.invokeExact_MT` | 2% |
| `java.lang.invoke.LambdaForm$MH.0x00007f240000dc00.invoke` | 2% |
| `lab.sample.terrain.Terrain.penaltyFor` | 1% |
| `com.fasterxml.jackson.databind.json.JsonMapper.<init>` | 0% |
| `lab.sample.transfer.Connections.waitMinutes` | 0% |

### With which values

**`lab.sample.RoutePlanner.legMinutes`** — 7 calls observed

| # | Arguments received | Value returned |
|---:|---|---|
| 1 | `Leg[from=Albi, to=Montauban, distanceKm=15.0]`, `lab.sample.speed.BikeSpeed@71e9a896`, `BIKE` | `54.41360000000001` |
| 2 | `Leg[from=Montauban, to=Castres, distanceKm=22.0]`, `lab.sample.speed.BikeSpeed@71e9a896`, `BIKE` | `97.30547200000002` |
| 3 | `Leg[from=Albi, to=Montauban, distanceKm=9.0]`, `lab.sample.speed.WalkSpeed@6b9267b`, `WALK` | `113.29632` |
| 4 | `Leg[from=Montauban, to=Castres, distanceKm=16.0]`, `lab.sample.speed.WalkSpeed@6b9267b`, `WALK` | `203.27935999999997` |
| 5 | `Leg[from=Castres, to=Auch, distanceKm=23.0]`, `lab.sample.speed.WalkSpeed@6b9267b`, `WALK` | `291.55168` |
| 6 | `Leg[from=Auch, to=Foix, distanceKm=30.0]`, `lab.sample.speed.WalkSpeed@6b9267b`, `WALK` | `376.28159999999997` |
| 7 | `Leg[from=Montauban, to=Castres, distanceKm=10.0]`, `lab.sample.speed.CarSpeed@f8908f6`, `CAR` | `8.672137142857144` |

**`lab.sample.RoutePlanner.travelTimeMinutes`** — 2 calls observed

| # | Arguments received | Value returned |
|---:|---|---|
| 1 | `Trip[id=TRIP-67, mode=WALK, weather=SUNNY, timeOfDay=NIGHT, withLuggage=false, legs=[Le…` | `1074.40896` |
| 2 | `Trip[id=TRIP-68, mode=CAR, weather=RAIN, timeOfDay=NIGHT, withLuggage=false, legs=[Leg[…` | `8.672137142857144` |

---

## Full scenario

- **Command**: `java -jar sample-app.jar --iterations 8000000`
- **Root method**: `lab.sample.RoutePlanner::travelTimeMinutes`
- **Start**: `2026-09-09 13:36:44`
- **Duration**: `39.0 s`
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

9207 samples. Folding the JDK, the virtual machine's own machinery and the hidden packages attributes their time to the application method that called them.

| Method | Share |
|---|---:|
| `lab.sample.Main.main` | 100% |
| `lab.sample.RoutePlanner.travelTimeMinutes` | 99% |
| `lab.sample.comfort.Breaks.totalMinutes` | 26% |
| `lab.sample.comfort.Breaks.count` | 26% |
| `lab.sample.RoutePlanner.legMinutes` | 5% |
| `lab.sample.terrain.Terrain.slowdownFactor` | 4% |
| `lab.sample.terrain.Elevation.gradePercentAt` | 3% |
| `lab.sample.model.Leg.from` | 0% |
| `lab.sample.transfer.Connections.waitMinutes` | 0% |
| `lab.sample.transfer.Connections.transferMinutes` | 0% |
| `lab.sample.transfer.Timetable.frequencyMinutes` | 0% |
| `lab.sample.terrain.Terrain.penaltyFor` | 0% |

### With which values

**`lab.sample.RoutePlanner.legMinutes`** — 7 calls observed

| # | Arguments received | Value returned |
|---:|---|---|
| 1 | `Leg[from=Castres, to=Auch, distanceKm=47.0]`, `lab.sample.speed.BikeSpeed@253d9f73`, `BIKE` | `207.067712` |
| 2 | `Leg[from=Auch, to=Foix, distanceKm=54.0]`, `lab.sample.speed.BikeSpeed@253d9f73`, `BIKE` | `233.58412800000008` |
| 3 | `Leg[from=Castres, to=Auch, distanceKm=41.0]`, `lab.sample.speed.WalkSpeed@142269f2`, `WALK` | `519.7225599999999` |
| 4 | `Leg[from=Auch, to=Foix, distanceKm=48.0]`, `lab.sample.speed.WalkSpeed@142269f2`, `WALK` | `602.0505599999999` |
| 5 | `Leg[from=Foix, to=Toulouse, distanceKm=55.0]`, `lab.sample.speed.WalkSpeed@142269f2`, `WALK` | `688.5383999999998` |
| 6 | `Leg[from=Toulouse, to=Albi, distanceKm=2.0]`, `lab.sample.speed.WalkSpeed@142269f2`, `WALK` | `25.257599999999996` |
| 7 | `Leg[from=Auch, to=Foix, distanceKm=42.0]`, `lab.sample.speed.CarSpeed@55322aab`, `CAR` | `23.116311272727273` |

**`lab.sample.RoutePlanner.travelTimeMinutes`** — 2 calls observed

| # | Arguments received | Value returned |
|---:|---|---|
| 1 | `Trip[id=TRIP-99, mode=WALK, weather=SUNNY, timeOfDay=QUIET, withLuggage=false, legs=[Le…` | `1925.5691199999997` |
| 2 | `Trip[id=TRIP-100, mode=CAR, weather=RAIN, timeOfDay=QUIET, withLuggage=true, legs=[Leg[…` | `23.116311272727273` |

---

## Scenario 2

- **Command**: `java -jar sample-app.jar --iterations 4000000`
- **Root method**: `lab.sample.terrain.Terrain::slowdownFactor`
- **Start**: `2026-09-09 13:36:17`
- **Duration**: `24.0 s`
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

199 samples. Folding the JDK, the virtual machine's own machinery and the hidden packages attributes their time to the application method that called them.

| Method | Share |
|---|---:|
| `lab.sample.Main.main` | 100% |
| `lab.sample.RoutePlanner.travelTimeMinutes` | 100% |
| `lab.sample.RoutePlanner.legMinutes` | 100% |
| `lab.sample.terrain.Terrain.slowdownFactor` | 100% |
| `lab.sample.terrain.Elevation.gradePercentAt` | 55% |
| `lab.sample.model.Leg.from` | 6% |
| `lab.sample.terrain.Terrain.penaltyFor` | 5% |

---

## Scenario 1

- **Command**: `java -jar sample-app.jar --iterations 4000000`
- **Root method**: `lab.sample.RoutePlanner::travelTimeMinutes`
- **Start**: `2026-09-09 13:36:06`
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

1436 samples. Folding the JDK, the virtual machine's own machinery and the hidden packages attributes their time to the application method that called them.

| Method | Share |
|---|---:|
| `lab.sample.Main.main` | 100% |
| `lab.sample.RoutePlanner.travelTimeMinutes` | 99% |
| `lab.sample.comfort.Breaks.totalMinutes` | 78% |
| `lab.sample.comfort.Breaks.count` | 78% |
| `lab.sample.RoutePlanner.legMinutes` | 14% |
| `lab.sample.terrain.Terrain.slowdownFactor` | 12% |
| `lab.sample.terrain.Elevation.gradePercentAt` | 7% |
| `lab.sample.transfer.Connections.waitMinutes` | 6% |
| `lab.sample.transfer.Connections.transferMinutes` | 6% |
| `lab.sample.transfer.Timetable.frequencyMinutes` | 6% |
| `lab.sample.model.Leg.from` | 1% |
| `lab.sample.RoutePlanner.<clinit>` | 1% |
