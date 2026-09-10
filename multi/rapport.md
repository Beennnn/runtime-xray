# Runtime analysis report

> Produced from the machine outputs of JaCoCo, async-profiler and Arthas.
> The same content, navigable, is in `index.html` beside this file:
> a forge shows an `.html` as source code, hence this version.

---

## Full scenario (Windows)

- **Command**: `java -jar sample-app/target/sample-app.jar --iterations 8000000`
- **Root method**: `lab.sample.RoutePlanner::travelTimeMinutes`
- **Start**: `2026-09-10 18:08:08`
- **Duration**: `25.0 s`
- **Java**: `Eclipse Adoptium 21.0.12.1`
- **Machine**: `Windows Server 2025 10.0 (amd64)`

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

---

## Full scenario (Windows, Flight Recorder)

- **Command**: `java -jar sample-app/target/sample-app.jar --iterations 8000000`
- **Root method**: `lab.sample.RoutePlanner::travelTimeMinutes`
- **Start**: `2026-09-10 18:08:37`
- **Duration**: `31.0 s`
- **Java**: `Eclipse Adoptium 21.0.12.1`
- **Machine**: `Windows Server 2025 10.0 (amd64)`

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

622 samples. Folding the JDK, the virtual machine's own machinery and the hidden packages attributes their time to the application method that called them.

| Method | Share |
|---|---:|
| `lab.sample.Main.main` | 64% |
| `lab.sample.RoutePlanner.travelTimeMinutes` | 63% |
| `lab.sample.comfort.Breaks.totalMinutes` | 38% |
| `lab.sample.comfort.Breaks.count` | 38% |
| `lab.sample.RoutePlanner.legMinutes` | 21% |
| `lab.sample.terrain.Terrain.slowdownFactor` | 19% |
| `lab.sample.terrain.Elevation.gradePercentAt` | 10% |
| `java.lang.invoke.LambdaForm$MH.0x00000273bc003000.invokeExact_MT` | 7% |
| `java.lang.invoke.LambdaForm$MH.0x00000273bc00dc00.invoke` | 7% |
| `jdk.jfr.internal.PlatformRecorder$$Lambda.0x00000273bc05dd28.run` | 4% |
| `lab.sample.terrain.Terrain.penaltyFor` | 3% |
| `lab.sample.transfer.Connections.waitMinutes` | 3% |

### With which values

**`lab.sample.RoutePlanner.legMinutes`** — 7 calls observed

| # | Arguments received | Value returned |
|---:|---|---|
| 1 | `Leg[from=Toulouse, to=Albi, distanceKm=38.0]`, `lab.sample.speed.WalkSpeed@41200e0c`, `WALK` | `479.8943999999999` |
| 2 | `Leg[from=Auch, to=Foix, distanceKm=18.0]`, `lab.sample.speed.CarSpeed@40f33492`, `CAR` | `15.568127999999998` |
| 3 | `Leg[from=Foix, to=Toulouse, distanceKm=19.0]`, `lab.sample.speed.TrainSpeed@649f2009`, `TRAIN` | `12.666666666666666` |
| 4 | `Leg[from=Toulouse, to=Albi, distanceKm=26.0]`, `lab.sample.speed.TrainSpeed@649f2009`, `TRAIN` | `17.333333333333332` |
| 5 | `Leg[from=Toulouse, to=Albi, distanceKm=20.0]`, `lab.sample.speed.BikeSpeed@14bb2297`, `BIKE` | `72.95466666666667` |
| 6 | `Leg[from=Albi, to=Montauban, distanceKm=27.0]`, `lab.sample.speed.BikeSpeed@14bb2297`, `BIKE` | `117.53337600000002` |
| 7 | `Leg[from=Montauban, to=Castres, distanceKm=34.0]`, `lab.sample.speed.BikeSpeed@14bb2297`, `BIKE` | `150.38118400000005` |

**`lab.sample.RoutePlanner.travelTimeMinutes`** — 2 calls observed

| # | Arguments received | Value returned |
|---:|---|---|
| 1 | `Trip[id=TRIP-196, mode=CAR, weather=RAIN, timeOfDay=QUIET, withLuggage=false, legs=[Leg…` | `15.568127999999998` |
| 2 | `Trip[id=TRIP-197, mode=TRAIN, weather=RAIN, timeOfDay=QUIET, withLuggage=false, legs=[L…` | `56.0` |

---

## Full scenario (Flight Recorder)

- **Command**: `java -jar sample-app/target/sample-app.jar --iterations 8000000`
- **Root method**: `lab.sample.RoutePlanner::travelTimeMinutes`
- **Start**: `2026-09-10 18:08:23`
- **Duration**: `28.0 s`
- **Java**: `Eclipse Adoptium 21.0.12.1`
- **Machine**: `Linux 6.17.0-1022-azure (amd64)`

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

3098 samples. Folding the JDK, the virtual machine's own machinery and the hidden packages attributes their time to the application method that called them.

| Method | Share |
|---|---:|
| `lab.sample.Main.main` | 61% |
| `lab.sample.RoutePlanner.travelTimeMinutes` | 61% |
| `lab.sample.comfort.Breaks.totalMinutes` | 13% |
| `lab.sample.comfort.Breaks.count` | 13% |
| `lab.sample.RoutePlanner.legMinutes` | 5% |
| `lab.sample.terrain.Terrain.slowdownFactor` | 5% |
| `java.lang.invoke.LambdaForm$MH.0x00007fdbdc003000.invokeExact_MT` | 3% |
| `java.lang.invoke.LambdaForm$MH.0x00007fdbdc00dc00.invoke` | 3% |
| `lab.sample.terrain.Elevation.gradePercentAt` | 3% |
| `lab.sample.transfer.Connections.waitMinutes` | 0% |
| `com.fasterxml.jackson.databind.json.JsonMapper.<init>` | 0% |
| `lab.sample.transfer.Connections.transferMinutes` | 0% |

### With which values

**`lab.sample.RoutePlanner.legMinutes`** — 7 calls observed

| # | Arguments received | Value returned |
|---:|---|---|
| 1 | `Leg[from=Foix, to=Toulouse, distanceKm=31.0]`, `lab.sample.speed.WalkSpeed@50305a`, `WALK` | `388.0852799999999` |
| 2 | `Leg[from=Toulouse, to=Albi, distanceKm=38.0]`, `lab.sample.speed.WalkSpeed@50305a`, `WALK` | `479.8943999999999` |
| 3 | `Leg[from=Auch, to=Foix, distanceKm=18.0]`, `lab.sample.speed.CarSpeed@72efb5c1`, `CAR` | `15.568127999999998` |
| 4 | `Leg[from=Foix, to=Toulouse, distanceKm=19.0]`, `lab.sample.speed.TrainSpeed@43c67247`, `TRAIN` | `12.666666666666666` |
| 5 | `Leg[from=Toulouse, to=Albi, distanceKm=26.0]`, `lab.sample.speed.TrainSpeed@43c67247`, `TRAIN` | `17.333333333333332` |
| 6 | `Leg[from=Toulouse, to=Albi, distanceKm=20.0]`, `lab.sample.speed.BikeSpeed@fac80`, `BIKE` | `72.95466666666667` |
| 7 | `Leg[from=Albi, to=Montauban, distanceKm=27.0]`, `lab.sample.speed.BikeSpeed@fac80`, `BIKE` | `117.53337600000002` |

**`lab.sample.RoutePlanner.travelTimeMinutes`** — 2 calls observed

| # | Arguments received | Value returned |
|---:|---|---|
| 1 | `Trip[id=TRIP-16, mode=CAR, weather=SUNNY, timeOfDay=NIGHT, withLuggage=false, legs=[Leg…` | `15.568127999999998` |
| 2 | `Trip[id=TRIP-17, mode=TRAIN, weather=SUNNY, timeOfDay=NIGHT, withLuggage=false, legs=[L…` | `56.0` |

---

## Scenario 2

- **Command**: `java -jar sample-app/target/sample-app.jar --iterations 4000000`
- **Root method**: `lab.sample.terrain.Terrain::slowdownFactor`
- **Start**: `2026-09-10 18:07:49`
- **Duration**: `7.0 s`
- **Java**: `Eclipse Adoptium 21.0.12.1`
- **Machine**: `Linux 6.17.0-1022-azure (amd64)`

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

626 samples. Folding the JDK, the virtual machine's own machinery and the hidden packages attributes their time to the application method that called them.

| Method | Share |
|---|---:|
| `lab.sample.Main.main` | 100% |
| `lab.sample.RoutePlanner.travelTimeMinutes` | 100% |
| `lab.sample.RoutePlanner.legMinutes` | 100% |
| `lab.sample.terrain.Terrain.slowdownFactor` | 100% |
| `lab.sample.terrain.Elevation.gradePercentAt` | 60% |
| `lab.sample.model.Leg.from` | 10% |
| `lab.sample.terrain.Terrain.penaltyFor` | 7% |
| `lab.sample.terrain.Terrain.applies` | 0% |

---

## Full scenario

- **Command**: `java -jar sample-app/target/sample-app.jar --iterations 8000000`
- **Root method**: `lab.sample.RoutePlanner::travelTimeMinutes`
- **Start**: `2026-09-10 18:07:59`
- **Duration**: `21.0 s`
- **Java**: `Eclipse Adoptium 21.0.12.1`
- **Machine**: `Linux 6.17.0-1022-azure (amd64)`

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

17798 samples. Folding the JDK, the virtual machine's own machinery and the hidden packages attributes their time to the application method that called them.

| Method | Share |
|---|---:|
| `lab.sample.Main.main` | 100% |
| `lab.sample.RoutePlanner.travelTimeMinutes` | 99% |
| `lab.sample.comfort.Breaks.totalMinutes` | 42% |
| `lab.sample.comfort.Breaks.count` | 42% |
| `lab.sample.RoutePlanner.legMinutes` | 9% |
| `lab.sample.terrain.Terrain.slowdownFactor` | 7% |
| `lab.sample.terrain.Elevation.gradePercentAt` | 5% |
| `lab.sample.transfer.Connections.waitMinutes` | 1% |
| `lab.sample.transfer.Connections.transferMinutes` | 1% |
| `lab.sample.transfer.Timetable.frequencyMinutes` | 1% |
| `lab.sample.model.Leg.from` | 0% |
| `lab.sample.terrain.Terrain.penaltyFor` | 0% |

### With which values

**`lab.sample.RoutePlanner.legMinutes`** — 7 calls observed

| # | Arguments received | Value returned |
|---:|---|---|
| 1 | `Leg[from=Toulouse, to=Albi, distanceKm=26.0]`, `lab.sample.speed.BikeSpeed@7e3181aa`, `BIKE` | `113.80928` |
| 2 | `Leg[from=Foix, to=Toulouse, distanceKm=13.0]`, `lab.sample.speed.WalkSpeed@253d9f73`, `WALK` | `162.74543999999995` |
| 3 | `Leg[from=Toulouse, to=Albi, distanceKm=20.0]`, `lab.sample.speed.WalkSpeed@253d9f73`, `WALK` | `252.57599999999994` |
| 4 | `Leg[from=Albi, to=Montauban, distanceKm=27.0]`, `lab.sample.speed.WalkSpeed@253d9f73`, `WALK` | `339.88896` |
| 5 | `Leg[from=Montauban, to=Castres, distanceKm=34.0]`, `lab.sample.speed.WalkSpeed@253d9f73`, `WALK` | `431.96863999999994` |
| 6 | `Leg[from=Toulouse, to=Albi, distanceKm=14.0]`, `lab.sample.speed.CarSpeed@49cb9cb5`, `CAR` | `12.125760000000007` |
| 7 | `Leg[from=Albi, to=Montauban, distanceKm=15.0]`, `lab.sample.speed.TrainSpeed@55322aab`, `TRAIN` | `10.0` |

**`lab.sample.RoutePlanner.travelTimeMinutes`** — 2 calls observed

| # | Arguments received | Value returned |
|---:|---|---|
| 1 | `Trip[id=TRIP-11, mode=WALK, weather=SUNNY, timeOfDay=RUSH_HOUR, withLuggage=false, legs…` | `1277.17904` |
| 2 | `Trip[id=TRIP-12, mode=CAR, weather=RAIN, timeOfDay=RUSH_HOUR, withLuggage=false, legs=[…` | `27.125760000000007` |

---

## Scenario 1

- **Command**: `java -jar sample-app/target/sample-app.jar --iterations 4000000`
- **Root method**: `lab.sample.RoutePlanner::travelTimeMinutes`
- **Start**: `2026-09-10 18:07:39`
- **Duration**: `8.0 s`
- **Java**: `Eclipse Adoptium 21.0.12.1`
- **Machine**: `Linux 6.17.0-1022-azure (amd64)`

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

4570 samples. Folding the JDK, the virtual machine's own machinery and the hidden packages attributes their time to the application method that called them.

| Method | Share |
|---|---:|
| `lab.sample.Main.main` | 100% |
| `lab.sample.RoutePlanner.travelTimeMinutes` | 99% |
| `lab.sample.comfort.Breaks.totalMinutes` | 81% |
| `lab.sample.comfort.Breaks.count` | 81% |
| `lab.sample.RoutePlanner.legMinutes` | 16% |
| `lab.sample.terrain.Terrain.slowdownFactor` | 14% |
| `lab.sample.terrain.Elevation.gradePercentAt` | 9% |
| `lab.sample.transfer.Connections.waitMinutes` | 1% |
| `lab.sample.transfer.Connections.transferMinutes` | 1% |
| `lab.sample.model.Leg.from` | 1% |
| `lab.sample.transfer.Timetable.frequencyMinutes` | 1% |
| `org.slf4j.LoggerFactory.getLogger` | 1% |
