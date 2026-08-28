# Decoupling collection from display — the pivot formats

> Starting question: *can the result of the calls be separated from its display, through a
> standardised format, so as to look at it afterwards in IntelliJ or in a web tool?*

**Short answer: yes for the coverage, yes for the call tree, no for the parameter values.**
And for the first two, that decoupling is not something to build — it already exists, and it
was verified here.

## Coverage — the decoupling is native

JaCoCo already separates the two moments: the agent writes a file of **raw data**
(`jacoco.exec`), and the rendering is a distinct operation, replayable as many times as one
likes, with a different perimeter each time.

That is exactly what [`collect-focused.sh`](../../tools/jacoco/collect-focused.sh) does: same
`.exec`, second rendering restricted to the classes actually executed. **The data is
collected once, displayed several times.**

| Format | Written by | Read by |
|---|---|---|
| **`jacoco.exec`** (binary) | the JaCoCo agent | the JaCoCo CLI, IntelliJ *(import to be confirmed)* |
| **JaCoCo XML** | the JaCoCo rendering | **SonarQube**, Jenkins, most quality tools |
| **CSV** | the JaCoCo rendering | any script — it is the source of the [Markdown summary](../../reports-demo/executions/rapport.md) |
| **Cobertura XML** | conversion from the JaCoCo XML | **GitLab**, natively, in a merge request's diff |
| **LCOV** | mostly the JS world | VS Code (coverage extensions), Codecov |
| **IntelliJ `.ic`** | IDEA | IDEA alone — closed format, to be avoided as a pivot |

**The pivot to keep: the JaCoCo XML.** That is what the ecosystem consumes. The `.exec` stays
useful upstream, since it allows re-rendering differently without re-running.

## Call tree — two pivots, one of them verified here

async-profiler writes **seven formats**: `flat`, `traces`, `collapsed`, `flamegraph`, `tree`,
`jfr`, `otlp`. The last three are the ones that matter for decoupling.

### JFR — the rich pivot, and the demonstration

**Verified on this machine**: async-profiler writes a `.jfr`, and the JDK's `jfr` tool reads
it back knowing nothing of its origin.

```
$ jfr summary reports-demo/generated/formats/profil.jfr
 Event Type                          Count
 jdk.ExecutionSample                  1296
 jdk.NativeLibrary                     525
```

**This is the decoupling asked for, done**: a profile collected by a third-party tool, in a
JDK format, readable by the JDK's tools — hence by **JMC** and by **IntelliJ IDEA Ultimate**,
which open `.jfr` files natively.

### Collapsed — the universal flame-graph pivot

The "folded stacks" format (one line per call stack, followed by a count) is the lingua
franca of profiling:

```
lab/sample/Main.main;lab/sample/RoutePlanner.travelTimeMinutes;lab/sample/terrain/Terrain.slowdownFactor 63
```

Trivial to produce, trivial to read back — it is moreover from it that the method ranking of
the [Markdown summary](../../reports-demo/executions/rapport.md) is computed. It is read by
**speedscope** (a static web viewer, hence self-hostable offline), by the historic
`flamegraph.pl`, and by continuous-profiling backends. *(These three readings rest on the
documentation: not tested here.)*

### OTLP — the backends' pivot

`-o otlp` produces a profile in the OpenTelemetry format, meant for continuous-profile
backends. Relevant only if the need swings towards monitoring — ruled out by decision no. 4.

## Parameter values — no standard format

This is the weak point, and it has to be said plainly.

| Tool | What it produces | Standardised? |
|---|---|---|
| Arthas | console text, OGNL expressions rendered | ❌ ad hoc, with no schema |
| JProfiler / YourKit | snapshots | ❌ proprietary formats |
| **JMC Agent** | **JFR events with typed fields** | ✅ **JFR** |

**One single standardised way exists: JFR.** A JFR event carries typed fields, and that is
precisely what the [JMC Agent](../etude/fiches/jmc-agent.md) exploits — one declares in XML
the parameters to capture, they become JFR events, readable by any tool that reads JFR.

**Strategic consequence**: if decoupling the parameter values becomes an objective, the JMC
Agent track stops being a mere alternative to Arthas. It becomes the only one that produces
**structured and reusable** data, where Arthas produces text to be read back by eye. That is
an argument the capability table alone does not bring out.

## The three possible architectures

```
1.  JaCoCo agent ────► jacoco.exec ─┬─► annotated HTML     (browser, offline)
                                    ├─► XML ──► SonarQube  (self-hosted web portal)
                                    ├─► XML ──► Cobertura ──► GitLab (MR diff)
                                    └─► CSV ──► Markdown summary (rendered by the forge)

2.  async-profiler ──┬─► .jfr ──────► JMC / IntelliJ Ultimate
                     ├─► collapsed ─► speedscope (static web) / flamegraph.pl
                     └─► HTML ───────► browser, with nothing to install

3.  JMC Agent ──► JFR events ──► JMC / IntelliJ Ultimate   (parameter values)
```

## The limits, honestly

- **No format carries the three kinds of data together.** There will always be at least two
  files: one for the coverage, one for the profile. A "unified report" does not exist.
- **Web profile viewers do not show annotated source code.** speedscope displays a flame
  graph, not your files. To navigate the code, JaCoCo's HTML remains unavoidable.
- **The pivot towards GitLab demands a conversion** JaCoCo → Cobertura, to be tooled.
- **speedscope, Perfetto and the like are web applications**: static and therefore
  self-hostable offline, but to be installed and maintained — which is precisely what
  decisions no. 3 and 4 were trying to avoid.

## What it changes for the choice

Nothing about the tools retained: JaCoCo and async-profiler already produce the pivot
formats, with no extra effort. **The decoupling is an asset, not a project.**

But it opens a door: the day the display no longer suits — because a portal is needed,
because GitLab is needed, because IntelliJ is needed — **one changes the display without
changing the collection**. None of these tools locks you into its own viewer.

That is the door `--export` walks through: the measurements are rewritten there as `perf
script`, as `cpuprofile` and as LCOV, to be opened in Firefox Profiler, speedscope or an
editor — without anything about the collection changing. The census of the open formats,
those retained and those ruled out with the reason, is in [Taking the measurement into
another tool](../outil/exports.md).
