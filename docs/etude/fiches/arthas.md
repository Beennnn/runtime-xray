# Arthas

> **Status: ✅ tested here, offline** — outputs in
> [`reports-demo/generated/arthas/`](../../../reports-demo/generated/arthas)
>
> Its launcher downloads its modules at first startup — which is **not an obstacle**, since the
> criterion bears on the run and not on the installation. The complete package is published on
> Maven Central; once laid down, `--arthas-home` means nothing goes out on the network any
> more. Verified with the HTTP traffic cut.

> ℹ️ **A precision about the name**: Arthas is an **Alibaba** tool (the open-source project
> `alibaba/arthas`). The server its launcher contacts by default is `arthas.aliyun.com` —
> Alibaba's cloud service. No connection with any AI service whatsoever.

## The offline point: verified, not supposed

This is the question that decides everything for this project, so it was **tested**.

**The network dependency exists, but it is in the launcher, at installation time.**
`arthas-boot` downloads Arthas's modules at first startup. The project's criterion bearing on
the **run** being offline, that step is not disqualifying: it happens once, in advance. What
remains is making sure that afterwards **nothing goes out** — and that is what was tested.

**It is worked around in two gestures**: fetch the complete package
`com.taobao.arthas:arthas-packaging:<version>:zip:bin` (~17 MB, published on **Maven Central**
— hence served by any internal Maven mirror), unzip it into `~/.arthas/lib/<version>/arthas`,
and launch with `--arthas-home`.

**The proof**: the capture below was replayed with **all the JVM's HTTP and HTTPS traffic
routed to a dead port** (`-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=1`, likewise for HTTPS).
Result:

```
[INFO] arthas home: /Users/.../.arthas/lib/4.3.4/arthas
[INFO] Attach process 70723 success.
    @String[TRIP-188], @Mode[CAR], @Weather[RAIN], ...
```

No attempt to go out, no failure: attaching and capturing work **with no network at all**. In a
genuinely cut-off environment, the only extra gesture is carrying the zip across once.

## What it is

A diagnostic console that **attaches to an already running JVM** and allows querying it live,
by commands: which methods are called, with which arguments, what result, what stack.

## Its philosophy

**Asking questions of a living application**, without restarting or modifying it. Where a
profiler records and then presents a report, Arthas is interactive: one types `watch`, one sees
values go by, one refines, one stops. It is a *diagnostic* tool in the medical sense, not a
tool for global measurement.

## What it can do

| Capability | |
|---|---|
| **Parameter values** | ✅ — `watch` with OGNL expressions on `params`, `returnObj`, `throwExp` |
| Call tree | ✅ — `trace` (path + time per level), `stack` (who called me?) |
| Executed lines | ❌ |
| Hot decompilation, class reloading | ✅ |

It is **the only free tool surveyed that answers the study's third need directly** — seeing the
values passed — with no instrumentation script to write.

## Its interface

A **console** (terminal or browser at `http://<host>:8563`), drivable by a command file — hence
scriptable and replayable.

### `watch` — the parameter values

```
watch lab.sample.RoutePlanner travelTimeMinutes \
  '{params[0].id(), params[0].mode(), params[0].weather(), params[0].timeOfDay(), returnObj}' -n 5 -x 2
```

Real output, under Java 21:

```
method=lab.sample.RoutePlanner.travelTimeMinutes location=AtExit
ts=2026-08-20 20:08:02.619; [cost=5.866458ms] result=@ArrayList[
    @String[TRIP-56],
    @Mode[CAR],
    @Weather[SUNNY],
    @TimeOfDay[RUSH_HOUR],
    @Boolean[false],
    @Double[52.00806981818182],
]
```

**The values, not the types.** That is very exactly what JFR does not give: where the recorder
displays `travelTimeMinutes(Trip)`, Arthas says *which* trip — a trip by car, at rush hour, in
fine weather — and *what it answered*.

### `trace` — the call tree of ONE call, with the line numbers

```
trace lab.sample.RoutePlanner travelTimeMinutes -n 2
```

```
`---[0.416459ms] lab.sample.RoutePlanner:travelTimeMinutes()
    +---[4,08% 0.017ms ] lab.sample.model.Trip:mode() #43
    +---[2,57% 0.010709ms ] lab.sample.speed.Speeds:forMode() #43
    +---[13,35% 0.055583ms ] lab.sample.RoutePlanner:legMinutes() #50
    +---[2,60% 0.010834ms ] lab.sample.traffic.Traffic:applies() #53
    +---[3,06% 0.01275ms ] lab.sample.weather.WeatherPenalty:applies() #57
    `---[2,06% 0.008583ms ] lab.sample.comfort.Breaks:totalMinutes() #67
```

Two things neither JaCoCo nor async-profiler gives:

1. **It is the tree of ONE call**, not a statistical aggregate. One sees the path really taken
   by that particular trip.
2. **The line numbers** (`#43`, `#53`, `#57`) — enough to go straight back into the source,
   which answers the navigation need.

And the conditional branches appear or not according to the context: `Traffic:applies()` is
there only for a trip by car, `WeatherPenalty:applies()` only for cycling or walking.

## How one navigates in it

- **Web console** ✅ — reachable with nothing to install on the reader's side.
- **No report to send**: the output is a stream of text. For a non-technician, it has to be
  taken up and formatted.
- No IDE or platform integration.

## Setting up

```bash
./tools/arthas/install-offline.sh   # once: fetches the package and lays it down locally
./tools/arthas/collect.sh           # attaches, captures, writes into reports-demo/
```

**The offline installation comes down to two gestures**: fetch
`com.taobao.arthas:arthas-packaging:<version>:zip:bin` (~17 MB, on Maven Central, hence
available from an internal Maven mirror), unzip it into `~/.arthas/lib/<version>/arthas`, and
launch with `--arthas-home`. The launcher never contacts `arthas.aliyun.com` again.

In a genuinely cut-off environment: one carries the zip across once, and that is all.

> ⚠️ Three traps met, all fixed in the scripts:
> - an `.as` command file **accepts no comment at all** — a `#` line is sent as a command and
>   comes back with `#: command not found`;
> - a `stop` command placed after a `trace` **closes the session before the collection**;
> - with no valid PID, `arthas-boot` waits for **interactive input indefinitely** — hence the
>   time guard in the collector.

## Licence and cost

**Apache 2.0**, open source, **€0**. An Alibaba project, widely used.

## What one can hope for from it

**It fills the hole left by JaCoCo and async-profiler, and that is verified.** With it, the
free base covers the study's three needs:

| Need | Tool | |
|---|---|---|
| Executed lines | JaCoCo | ✅ |
| Call tree (cumulative, visual) | async-profiler | ✅ |
| Call tree (one call, with lines) + **parameter values** | **Arthas** | ✅ |

What that changes: **the commercial licence question no longer bears on a missing capability,
but on comfort**. JProfiler and YourKit would bring everything together in a single interface;
the free trio demands three tools and a console.

## Comparable to

- **[BTrace](btrace-byteman.md) · [Byteman](btrace-byteman.md)** — **The same family**: injecting observation into a living application. The difference is ergonomic and decisive — Arthas answers a command, the other two demand writing a script or a rule per question.
- **[JMC Agent](jmc-agent.md)** — The same objective, the opposite philosophy: **declarative** (an XML of probes, structured data out) against **interactive** (a command, text out). The JMC Agent produces reusable data, Arthas an immediate answer.
- **[JProfiler](jprofiler.md) · [YourKit](yourkit.md)** — What they add is precise: **aggregating** the call tree by argument value over millions of calls. Arthas shows a few individual calls — useful for understanding one case, insufficient for reasoning about a set.
- **[IntelliJ IDEA](intellij.md)** — The non-suspending breakpoint that logs an expression does the same thing, free, in the IDE — but only during a debugging session.

## Easy / less easy

**What is easy.** Asking a question of a living application: `watch`, and the values scroll past. `trace`, and one call's tree appears with its line numbers. It is the most direct gesture of the whole panel for the third need — and the offline installation turned out to be trivial (a zip to unpack).

**What is less so.** **Handing the result on**: the output is a stream of console text, not a navigable report — that is its real weakness against criterion no. 2. One also has to **know what to ask**: unlike a coverage report that shows everything, Arthas answers only the questions one puts to it, method by method. And it demands a **living JVM**, hence a run one can hold open.
