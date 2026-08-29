# Runtime analysis report

> Produced from the machine outputs of JaCoCo, async-profiler and Arthas.
> The same content, navigable, is in `index.html` beside this file:
> a forge shows an `.html` as source code, hence this version.

---

## Full scenario

- **Command**: `java -jar /home/user/runtime-xray/sample-app/target/sample-app.jar --iterations 24000000`
- **Root method**: `lab.sample.RoutePlanner::travelTimeMinutes`
- **Hidden packages**: `org.slf4j, org.apache.commons`
- **Start**: `2026-08-29 06:11:11`
- **Duration**: `155.0 s`
- **Java**: `Ubuntu 21.0.10`
- **Machine**: `Linux 6.18.44-fc-v22 (amd64)`

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

37695 samples. Folding the JDK, the virtual machine's own machinery and the hidden packages attributes their time to the application method that called them.

| Method | Share |
|---|---:|
| `lab.sample.Main.main` | 100% |
| `lab.sample.RoutePlanner.travelTimeMinutes` | 100% |
| `lab.sample.comfort.Breaks.totalMinutes` | 20% |
| `lab.sample.comfort.Breaks.count` | 19% |
| `lab.sample.RoutePlanner.legMinutes` | 4% |
| `lab.sample.terrain.Terrain.slowdownFactor` | 3% |
| `lab.sample.terrain.Elevation.gradePercentAt` | 2% |
| `lab.sample.transfer.Connections.waitMinutes` | 0% |
| `lab.sample.transfer.Connections.transferMinutes` | 0% |
| `lab.sample.transfer.Timetable.frequencyMinutes` | 0% |
| `lab.sample.model.Leg.from` | 0% |
| `lab.sample.terrain.Terrain.penaltyFor` | 0% |

### With which values

**`lab.sample.RoutePlanner.legMinutes`** — 7 calls observed

| # | Arguments received | Value returned |
|---:|---|---|
| 1 | `Leg[from=Auch, to=Foix, distanceKm=54.0]`, `lab.sample.speed.BikeSpeed@253d9f73`, `BIKE` | `233.58412800000008` |
| 2 | `Leg[from=Castres, to=Auch, distanceKm=41.0]`, `lab.sample.speed.WalkSpeed@142269f2`, `WALK` | `519.7225599999999` |
| 3 | `Leg[from=Auch, to=Foix, distanceKm=48.0]`, `lab.sample.speed.WalkSpeed@142269f2`, `WALK` | `602.0505599999999` |
| 4 | `Leg[from=Foix, to=Toulouse, distanceKm=55.0]`, `lab.sample.speed.WalkSpeed@142269f2`, `WALK` | `688.5383999999998` |
| 5 | `Leg[from=Toulouse, to=Albi, distanceKm=2.0]`, `lab.sample.speed.WalkSpeed@142269f2`, `WALK` | `25.257599999999996` |
| 6 | `Leg[from=Auch, to=Foix, distanceKm=42.0]`, `lab.sample.speed.CarSpeed@55322aab`, `CAR` | `23.116311272727273` |
| 7 | `Leg[from=Foix, to=Toulouse, distanceKm=43.0]`, `lab.sample.speed.TrainSpeed@2b4c1d96`, `TRAIN` | `28.666666666666668` |

**`lab.sample.RoutePlanner.travelTimeMinutes`** — 2 calls observed

| # | Arguments received | Value returned |
|---:|---|---|
| 1 | `Trip[id=TRIP-219, mode=WALK, weather=SUNNY, timeOfDay=QUIET, withLuggage=false, legs=[L…` | `1925.5691199999997` |
| 2 | `Trip[id=TRIP-220, mode=CAR, weather=RAIN, timeOfDay=QUIET, withLuggage=true, legs=[Leg[…` | `23.116311272727273` |

---

## Scenario 2

- **Command**: `java -jar /home/user/runtime-xray/sample-app/target/sample-app.jar --iterations 24000000`
- **Root method**: `lab.sample.terrain.Terrain::slowdownFactor`
- **Hidden packages**: `org.slf4j, org.apache.commons`
- **Start**: `2026-08-29 06:02:49`
- **Duration**: `498.0 s`
- **Java**: `Ubuntu 21.0.10`
- **Machine**: `Linux 6.18.44-fc-v22 (amd64)`

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

119225 samples. Folding the JDK, the virtual machine's own machinery and the hidden packages attributes their time to the application method that called them.

| Method | Share |
|---|---:|
| `lab.sample.Main.main` | 100% |
| `lab.sample.RoutePlanner.travelTimeMinutes` | 100% |
| `lab.sample.RoutePlanner.legMinutes` | 100% |
| `lab.sample.terrain.Terrain.slowdownFactor` | 100% |
| `lab.sample.terrain.Elevation.gradePercentAt` | 1% |
| `lab.sample.terrain.Terrain.penaltyFor` | 0% |
| `lab.sample.model.Leg.from` | 0% |
| `__pthread_mutex_unlock@GLIBC_2.2.5` | 0% |
| `__pthread_mutex_trylock@GLIBC_2.2.5` | 0% |
| `lab.sample.terrain.Terrain.applies` | 0% |
| `ognl.Ognl.getValue` | 0% |
| `ognl.Ognl.parseExpression` | 0% |

### With which values

**`lab.sample.terrain.Terrain.applies`** — 1 call observed

| # | Arguments received | Value returned |
|---:|---|---|
| 1 | `CAR` | `true` |

**`lab.sample.terrain.Terrain.penaltyFor`** — 8 calls observed

| # | Arguments received | Value returned |
|---:|---|---|
| 1 | `1.4720000000000002`, `CAR` | `1.01472` |
| 2 | `-2.112`, `CAR` | `0.993664` |
| 3 | `-4.351999999999999`, `CAR` | `0.986944` |
| 4 | `4.351999999999999`, `CAR` | `1.04352` |
| 5 | `2.112`, `CAR` | `1.02112` |
| 6 | `-1.4720000000000002`, `CAR` | `0.995584` |
| 7 | `-4.032`, `CAR` | `0.987904` |
| 8 | `4.608`, `CAR` | `1.04608` |

---

## Scenario 1

- **Command**: `java -jar /home/user/runtime-xray/sample-app/target/sample-app.jar --iterations 3 --hold-seconds 18`
- **Root method**: `lab.sample.RoutePlanner::travelTimeMinutes`
- **Hidden packages**: `org.slf4j, org.apache.commons`
- **Start**: `2026-08-29 06:02:16`
- **Duration**: `30.0 s`
- **Java**: `Ubuntu 21.0.10`
- **Machine**: `Linux 6.18.44-fc-v22 (amd64)`

### Where the code went

**78% of the analysed instructions** ran. The denominator is all the bytecode given to be analysed: if the analysis covers a whole dependency, most of it has by construction no reason to run, and this rate then says mostly how big that dependency is.

| Package | Instructions covered |
|---|---:|
| `lab.sample` | 98% |
| `lab.sample.comfort` | 100% |
| `lab.sample.model` | 84% |
| `lab.sample.speed` | 66% |
| `lab.sample.terrain` | 95% |
| `lab.sample.traffic` | 17% |
| `lab.sample.transfer` | 95% |
| `lab.sample.weather` | 81% |

1 package was not touched at all.

**Never executed** (5) — `lab.sample.export.CsvExporter`, `lab.sample.export.ItineraryExporter`, `lab.sample.speed.PlaneSpeed`, `lab.sample.speed.SpeedModel`, `lab.sample.traffic.RushHourDelay`

### Where the time went

18 samples. Folding the JDK, the virtual machine's own machinery and the hidden packages attributes their time to the application method that called them.

| Method | Share |
|---|---:|
| `lab.sample.Main.main` | 100% |
| `lab.sample.RoutePlanner.<clinit>` | 56% |
| `lab.sample.Metrics.stage` | 11% |
| `lab.sample.Scenarios.at` | 11% |
| `lab.sample.RoutePlanner.travelTimeMinutes` | 11% |
| `lab.sample.comfort.Breaks.<clinit>` | 6% |
| `lab.sample.weather.LuggagePenalty.slowdownFactor` | 6% |
