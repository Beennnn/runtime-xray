# Technical details

> A page for whoever is going to **replay** or **extend** the evaluation. A decision-maker
> does not need it: the results are in [the results](../resultat/resultats.md).

## The analysed program

[`sample-app/`](../../sample-app) — 27 classes, 9 packages. The **business code** has no
dependency: what a tool shows comes from that code, not from a framework's noise.

Three dependencies exist nonetheless, each to produce a case the local code cannot
manufacture:

| Dependency | What it demonstrates |
|---|---|
| `commons-lang3` | Code to analyse that comes not from a directory of classes but from a **jar**, like an internal dependency delivered compiled. Only one of its classes is called (`Range`, in `Breaks`) |
| `slf4j-api` + `slf4j-simple` | A library **outside the JDK**, visible in the call tree since nothing folds it away by default, and therefore hideable by configuration — the call is left in the hot loop on purpose |

The dependency jars are copied into `target/libs/` rather than melted into a single jar: the
study needs a **stable path to a dependency's jar** in order to hand it over for analysis,
and an uber-jar would erase precisely the distinction one wants to show.

The function under analysis is
[`RoutePlanner.travelTimeMinutes(Trip)`](../../sample-app/src/main/java/lab/sample/RoutePlanner.java) :
"how long does it take to get from A to B?". It is designed to **tell the tools apart**,
not to be realistic.

### The call graph depends on the context

That is the central point: depending on the trip, it is not the same functions that run.

| Trip context | What executes |
|---|---|
| Car **and** rush hour | `traffic.Traffic` → `RushHourDelay` — otherwise this whole sub-tree is absent |
| Train | `transfer.Connections` (recursive) → `Timetable`, the only **blocking** operation |
| Bike or walking | `weather.WeatherPenalty` then `LuggagePenalty` |
| Everything but the train | `terrain.Terrain` → `Elevation`, 24 sub-segments — the computational core |
| Duration > 2 h | `comfort.Breaks`, decided on a **computed value**, not on an argument received |

Two calls of the same method therefore walk different trees. A tool that gives only a global
total does not let one see it; a tool that traces per invocation does.

### The coverage holes are deliberate

| Item | Why |
|---|---|
| The whole `export` package | `ItineraryExporter` and `CsvExporter` compile, look useful, and are **never called**. It is the most interesting case when taking over code: a whole package in red makes one ask "who calls this?" |
| `speed.PlaneSpeed` | Never instantiated — a **whole class** must come out red |
| The `Weather.SNOW` branch | Never taken, **inside** a method executed millions of times: a percentage per class hides it, a line-by-line report shows it |
| The `Mode.PLANE` branch of `Speeds` | Deliberately instantiated inside the `switch` and not as a `static final` field, otherwise the class would be loaded and would count as partly covered |

### Measurement points

The application prints its own time per stage and the memory in use
([`Metrics`](../../sample-app/src/main/java/lab/sample/Metrics.java)) :

```
  [JVM started                 ] stage        0 ms | total        0 ms | memory ~2 MB / 8192 MB max
  [trip set built              ] stage        8 ms | total        8 ms | memory ~1 MB / 8192 MB max
  [computation finished        ] stage     9927 ms | total     9935 ms | memory ~1 MB / 8192 MB max
```

It is an **independent reference**: when a tool announces a time, one can confront it with a
measurement that does not come from it, and note how much the tool itself slows the run down.

⚠️ These figures are a bonus, **not a selection criterion** — and they say nothing about the
code's performance. **Optimisation will be studied separately, later**: once the code has
been understood thanks to these tools, then redesigned with its own performance constraints.
Here the only question asked is: *does the analysis tool slow things down little enough to
stay usable?*

### Calibrated to ~10 seconds

`DEFAULT_ITERATIONS = 16_000_000`, calibrated so the computation lasts **~10 s**. That is the
minimum for a tool attaching to a live process (VisualVM, JMC, JProfiler) to have time to be
launched, to select the JVM and to record something. The `--hold-seconds` option additionally
keeps the JVM alive after the computation.

### Refinements decided by measurement, not by guesswork

Three changes to the program come from an observed profile, each commented in the code:

1. **Building the test set moved out of the measured loop** — it captured ~70 % of the
   samples; the tree showed `StringConcatHelper` instead of the route computation.
2. **Speed models as shared instances** — allocating them on every call accounted for ~25 %
   of the samples, pure allocation noise.
3. **An indexed loop instead of `for-each` over an immutable list** — the iterator allocated
   per leg accounted for half the samples.

Result: **71 % of the samples have a leaf in `lab.sample`**, and the profile's first five
leaves are business methods.

## How each tool integrates

A structuring point: **these tools are not Maven dependencies.**

| Mode | Tools | What it implies |
|---|---|---|
| **JVM agent** (`-javaagent`, `-agentpath`) | JaCoCo, async-profiler, JProfiler, YourKit, Glowroot, OpenTelemetry, Kieker | One flag at launch; nothing to change in the code |
| **Native JDK option** | JFR | Nothing to install at all |
| **Attaching to a live process** | VisualVM, JMC, Arthas, JProfiler, YourKit | Requires the JVM to still be running — hence the 10 s calibration |
| **Build plugin** | JaCoCo (test mode), OpenClover | Measures only what the tests cover — not the same question |

## Replaying the evaluation

```bash
mvn -q clean package
java -jar sample-app/target/sample-app.jar

./tools/jacoco/collect.sh          # coverage, annotated HTML report
./tools/async-profiler/collect.sh  # flame graph + HTML call tree
./tools/jfr/collect.sh             # JFR recording
```

[`tools/java-env.sh`](../../tools/java-env.sh) forces **Java 21** for every collector:
without that, one would be documenting capabilities the target platform does not have.

## Traps met, and their fix

| Trap | Fix applied |
|---|---|
| `-Djacoco.outputDirectory` **is not honoured** by the `report` goal: the report lands in `target/site/jacoco` | An explicit copy rather than trust in a phantom property |
| Without `-XX:+DebugNonSafepoints`, async-profiler attributes frames to the **wrong line number** | Flag added in the collector |
| Without `include=lab/sample/*`, 60 % of the samples are the JIT compiler's threads | Filter added — true, but unreadable for whoever is looking for their own code |
| Tracing a hot-path method with JFR: **129 MB for 10 s** of run | `maxsize` + tracing targeted on the rare method |
| The binary `.jfr` **trips GitHub's secret scanner** (false positive "Grafana token") | Binary excluded from the repository; the text extracts are versioned |
| On macOS, `perf_events` does not exist | `event=itimer` for async-profiler |
