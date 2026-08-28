# Results — solutions retained and trade-offs still to settle

> Summary page. The detail tool by tool is in [the comparison](../etude/comparatif.md) and in
> the [individual sheets](../etude/fiches). The protocol is in [the method](../etude/methode.md).

## In one sentence

**No tool covers the three needs on its own.** Line coverage and the capture of parameter
values belong to two different technical families — one instruments all the bytecode
permanently, the other grafts probes onto a few targeted methods. The answer will therefore
be **a combination**, and it is the second tool of that combination that has to be settled.

## What is acquired, and verified by running it

Three tools really ran on the target application. All three are **free** and **work offline**
once installed.

### 1. "Where the code went" → **JaCoCo**. Question settled.

This is the most important point of the study, and it is the best resolved.

- Output: an **HTML site** where one descends from the overview down to the **source code
  coloured line by line** — green executed, red never reached, yellow partial branch.
- Measured on `sample-app`: **91.1 % of instructions**, **81.8 % of branches**.
- No server, no account, no connection: a folder of files one opens or sends as it is.

![Source code annotated by JaCoCo](../assets/shots/jacoco-source-weather.png)

*The `SNOW` branch in red was never executed; the yellow diamond signals a partially covered
condition. A non-technical reader immediately sees what did not run, knowing nothing of
Java.*

→ [JaCoCo sheet](../etude/fiches/jacoco.md)

### 2. "The call tree" → **async-profiler**. Best effort-to-result ratio.

- Output: **one self-contained HTML file** — flame graph and tree view — unfoldable,
  searchable, with the share of time per branch.
- Setting up: one installation, then **one single JVM flag**.
- After refining the example code, **71 % of the samples fall on business methods**; the
  profile's top five leaves are all application classes.

![async-profiler call tree](../assets/shots/async-tree.png)

→ [async-profiler sheet](../etude/fiches/async-profiler.md)

### 3. The parameter values → **Arthas**. The hole is filled.

This was the missing point, and it is missing no longer. Arthas attaches to the running JVM
and answers the two remaining questions, **free of charge and offline**:

```
watch lab.sample.RoutePlanner travelTimeMinutes '{params[0].id(), params[0].mode(), ...}'

ts=2026-08-20 20:08:02.619; [cost=5.866458ms] result=@ArrayList[
    @String[TRIP-56], @Mode[CAR], @Weather[SUNNY], @TimeOfDay[RUSH_HOUR],
    @Boolean[false], @Double[52.00806981818182],
]
```

Where JFR displays `travelTimeMinutes(Trip)`, Arthas says **which** trip and **what it
answered**. And its `trace` command gives the call tree of **one single** call, with the
**line numbers**:

```
`---[0.416459ms] lab.sample.RoutePlanner:travelTimeMinutes()
    +---[13,35% 0.055583ms ] lab.sample.RoutePlanner:legMinutes() #50
    +---[2,60%  0.010834ms ] lab.sample.traffic.Traffic:applies() #53
    +---[3,06%  0.01275ms  ] lab.sample.weather.WeatherPenalty:applies() #57
```

**Offline installation was the only obstacle, and it is lifted**: the complete package is on
Maven Central, one unzips it into `~/.arthas/`, and `--arthas-home` makes the launcher never
contact the publisher again.

→ [Arthas sheet](../etude/fiches/arthas.md)

## And JFR in all this: it is NOT a third pillar

It figured higher up in an earlier version of this page as a point in its own right. **That
was misleading, and the question deserves to be settled plainly.**

**Under Java 21, JFR brings nothing async-profiler does not already do — and it does it less
well.** Both sample the call stacks; it is the same nature of data. But async-profiler
produces a self-contained HTML one opens and sends, where JFR produces a binary that demands
JDK Mission Control, a desktop application. On our criteria no. 1 (setting up) and no. 2
(readability), **it is dominated**.

It nevertheless keeps three real interests, none of which is "one more capability today":

1. **It is already there.** It is the only tool available when one can install **nothing** on
   the target machine — a locked-down environment, a production machine one has no hand on.
   It is a **safety net**, not a pillar.
2. **It changes nature in Java 25.** With method tracing (JEP 520), it no longer samples: it
   **counts exactly** — 16,000,000 invocations recorded, not estimated. Neither
   async-profiler nor Arthas can do that. It is the only serious technical argument in favour
   of a port, and it is detailed
   [further down](#gains-from-a-port-to-java-25-no-gain-visible-in-the-report).
3. **It is the only standard format able to carry parameter values.** A JFR event has typed
   fields; that is what the [JMC Agent](../etude/fiches/jmc-agent.md) exploits. Arthas, for
   its part, produces console text. If the need to **reuse** the captured values appears one
   day — replaying them, comparing them, feeding them into another tool — it is through JFR
   that it will go, not through Arthas. See [the formats](formats.md).

**In short**: today, under Java 21, we do not need it. Tomorrow, under Java 25 or if the
values have to become structured data, it becomes central again.

## The free base covers both priorities — and the option

| Priority | Need | Tool | Status |
|---|---|---|---|
| **1** | Executed lines | JaCoCo | ✅ verified |
| **1** | Cumulative call tree, visual | async-profiler | ✅ verified |
| **2 — option** | One call's call tree + the parameter values | Arthas | ✅ verified |

**Both priority objectives are reached with two tools only.** Arthas comes on top, on the
second priority — which means that even if it were a problem (installation, readability of
its output), **the essential would remain acquired**.

**Cost: €0. Connection needed: none.**

What this changes for the decision: the commercial licence would fill no missing capability,
and what it would bring concerns only the **second priority** — bringing the call tree and
the values together in a single interface, and aggregating by parameter value. **Paying for
the option, when the objective is already reached.**

## Summary analysis — what is easy, what is less so

### Easy, right away, free of charge

**Knowing where the code went** is a solved problem. One JVM flag, one HTML folder, and
anybody opens the report and sees the code coloured line by line. No configuration, no
server, no account, no connection. It is need no. 1 of the study and it demands no decision.

**Seeing the call tree** is almost as simple: one installation, one flag, one HTML file that
one unfolds and sends by mail.

**Publishing all that for a non-developer** turned out easier than expected: the reports are
static files, hence [directly publishable
online](https://beennnn.github.io/runtime-xray/). A link, nothing to install on the reader's
side.

**Restricting the report to the code actually executed** works, with JaCoCo's native
mechanism and a dozen lines of script — the classes never reached disappear from the report.

### Less easy, but feasible

**Displaying the coverage in the forge.** GitLab can do it natively, at the price of a
conversion to the Cobertura format. GitHub cannot, without a third-party service — hence,
without a connection, it cannot at all. The realistic offline channel remains the published
HTML report.

**Fitting the whole thing into the IDE.** IntelliJ displays the coverage in the margin, and
that is unbeatable for a developer — but nothing comes out of it for anybody else.

**Targeting without drowning.** As soon as one traces finely, the volume explodes: 129 MB for
ten seconds on a hot method. Tracing is targeted method by method; it is a skill, not a
button.

### Hard — and that is where the decision is played out

**The values passed as parameters.** No free tool gives them *easily*. The four existing
tracks each demand something: Arthas a manual offline installation and a reading in a
console, JMC Agent an XML of probes and a binary that is hard to find, BTrace and Byteman a
script per question, IntelliJ a breakpoint laid by hand in a debugging session. **These are
all expert answers, none of them is a report.** That is precisely what JProfiler and YourKit
sell.

**Bringing the three needs together in one navigable report.** Nobody does it. The coverage
report ignores the call tree, the flame graph ignores the dead lines, and neither of the two
shows a value. A unified report does not exist off the shelf — it would have to be built.

**Hiding the non-executed portions inside a file.** Removing a whole class, yes. Reducing a
file to its executed lines alone: no tool surveyed offers it.

### What money buys, exactly

One single thing, but a clear one: **the parameter values in the same tool as the call tree,
without writing a script**. Neither the coverage (JaCoCo gives it better, and free), nor the
flame graph (same engine, free), nor readability for a non-developer — on that last point,
JaCoCo's HTML is even superior to a profiler snapshot.

$549 to fill one precise hole, to be compared with a few days of getting to grips with Arthas
or the JMC Agent. **That is the whole of trade-off no. 1**, and it is honestly settled only
after the free trial.

## The solution retained: JaCoCo + async-profiler + Arthas

**→ [Dedicated page: the complete assembly, the protocol and the integrated view](solution.md)**

The three tools complement each other exactly — what one ignores, another covers — and the
whole was **verified end to end** on the same application, under Java 21.

| Priority | Need | Tool | What one gets |
|---|---|---|---|
| **1** | Executed lines | **JaCoCo** | HTML site, code coloured line by line, dead classes in red |
| **1** | Call tree | **async-profiler** | Self-contained unfoldable HTML, share of time per branch |
| **2 — option** | Parameter values | **Arthas** | The real values, and one call's tree with its line numbers |

**Cost: €0. Connection: none. Licence: none.**

And the three outputs assemble into **a single page** — [the integrated
view](https://beennnn.github.io/runtime-xray/multi/): one navigates the call tree or the
classes, picks a point, and sees at once the executed lines, the calls that leave from them
with their cost, and the values passed.

![The integrated view](../assets/shots/vue-integree.png)

**All of it in a single run**: the three tools run together, and the coverage is not
corrupted — that is verified automatically at every launch. One caveat only: Arthas captures
most of the profile's samples, so the **time percentages** are overestimated on the method it
observes. The view folds its frames back to restore the shape of the tree and shows the
caveat in a banner. Time not being a criterion of the project, that is an acceptable trade —
and `--no-values` separates the two passes the day one wants correct times. Detail in [the
solution](solution.md).

### Minimal variant, if Arthas is a problem

**JaCoCo + async-profiler alone** cover the **two priority objectives**. Arthas serves only
the second priority: its manual offline installation, or the fact that its output is a
console rather than a report, do not call the essential into question.

### The paid complement — postponed, not ruled out

**JProfiler or YourKit**, on top of the base, would bring one precise thing Arthas cannot do:
**aggregating the call tree by parameter value** over the whole set of calls. Decision no. 1
classed it secondary. What would have to be verified in a trial is detailed in [the
evaluation keys](../etude/cles-evaluation.md#ce-quon-attend-des-outils-commerciaux--et-pourquoi).

### For later

- **[Kieker](../etude/fiches/kieker.md)** — the only tool that aims at the *final goal*:
  reconstructing an architecture from the run. To be opened once the diagnosis is tooled, not
  before.
- **The port to Java 25** — it would make exact call counting available with no third-party
  tool at all. See [the measured
  gains](#gains-from-a-port-to-java-25-no-gain-visible-in-the-report).

### Ruled out outright by the offline constraint

Datadog, New Relic, Dynatrace, Codecov, Coveralls, SonarCloud — all SaaS. The detail of the
rejections, with their reasons, is in [the tools ruled out](../etude/outils-ecartes.md).

## Gains from a port to Java 25: no gain visible in the report

### The verdict first

> **On the solution retained, Java 25 improves nothing of what the analysis gives to see.**
> Do not port the code for the analysis tooling. If there is a port, it will be for other
> reasons.

Verified point by point, on the same program:

| What the report shows | Java 21 | Java 25 | Visible difference? |
|---|---|---|---|
| Executed lines (JaCoCo) | 91.1 % / 81.8 % | **identical, value by value** | ❌ none |
| Call tree (async-profiler) | works | works the same | ❌ none |
| Parameter values (Arthas) | works | works the same | ❌ none |

**The three tools retained ignore the JDK's version.** Java 25's gain concerns only a fourth
tool — JFR — which [was established above](#and-jfr-in-all-this-it-is-not-a-third-pillar) we
do not need.

### An argument the other way

[Kieker](../etude/fiches/kieker.md), the only tool that would aim at the study's *final
goal*, announces support **up to Java 23**. Porting to 25 would **remove** that option until
it has followed. From the tooling's point of view, the port would cost rather than bring in.

### The one case where Java 25 would change something

One scenario, and one only: **the one where nothing can be installed** on the target machine.
Only what the JDK carries then remains, and the difference becomes spectacular. Same program,
same duration, same command:

| JFR event | Java 21 | Java 25 |
|---|---|---|
| `jdk.ExecutionSample` (statistical sampling) | 331 | 147 |
| `jdk.MethodTrace` (call stack **at every invocation**) | — | **594,877** |
| `jdk.MethodTiming` (exact count) | — | **16,000,000 invocations counted** |

Under Java 21, the JVM even refuses the setting:

```
[warning][jfr,start] The .jfc option/setting 'jdk.MethodTiming#filter' doesn't exist.
```

### Why Java 25 is more precise — the mechanism

It is not a finer setting: **it is a change in the nature of the measurement.**

**Java 21 samples.** JFR periodically photographs the threads' stacks. Over ten seconds, a
few hundred snapshots, from which one *infers* where the time went. A method called 16
million times but very brief may appear in almost none of the snapshots. One never knows how
many times a thing was called — only in what proportion of the photographs it figured.

**Java 25 instruments** (JEP 520). The tracer rewrites the bytecode of the methods **named in
the filter** to count and time **every** invocation. It is no longer a survey, it is an
exhaustive count. The filter is what makes the operation bearable: one pays the overhead only
on the methods designated.

### What that precision costs

1. **The instrumentation disturbs what it measures** — a traced method is no longer *inlined*
   the same way by the JIT compiler. The count stays exact; the durations of very brief
   methods are to be taken with caution.
2. **The volume explodes** — tracing one method of the hot path produced **129 MB for 10
   seconds**. Playable when targeted, unmanageable when sprinkled about.

Java 25 therefore does not replace sampling: it **adds** a second mode. One samples to know
where to look, one instruments to measure what one has found.

The outputs of both versions are versioned side by side:
[`reports-demo/generated/jfr/`](../../reports-demo/generated/jfr) (Java 21) and
[`reports-demo/generated/jfr-jdk25/`](../../reports-demo/generated/jfr-jdk25) (Java 25).

## Decisions taken

Trade-offs settled by Benoît on 2026-08-20. They reduce the perimeter and fix the
distribution channel.

| # | Question | Decision |
|---|---|---|
| 1 | Buy a licence for the parameter values? | **No, secondary for now.** All the more so since Arthas supplies them free — see above |
| 2 | Targeted or general capture? | **To be developed** — see just below |
| 3 | How does the non-technician receive the report? | **A folder sent as one file**, analysable **without IntelliJ** |
| 4 | Keep a history of the results? | **No, not for now** |
| 5 | IntelliJ Ultimate? | **Do not depend on it** — not everybody has it. What exists only in Ultimate must not be on the critical path |
| 6 | Aim at the "final goal" (restructuring)? | **To be developed** — see the Kieker section |

### What these decisions eliminate

- **SonarQube, Glowroot, OpenTelemetry** leave the perimeter: they suppose a server to
  install and maintain, which decisions 3 and 4 make pointless.
- **IntelliJ Ultimate's profiler** leaves the critical path (decision 5).
- **JProfiler and YourKit** are postponed, without being ruled out (decision 1).

### Question 2, developed: targeted or general capture?

The question bears on the **extent** of the value capture: instrumenting all the code, or
only the methods one designates.

**The general mode** — recording every call of every method with its arguments — is
attractive: one has nothing to decide in advance, one captures and then searches. **It is
impracticable, and that is measured**: tracing **one single** method of the hot path produced
**129 MB for 10 seconds** of running. Extended to a few hundred methods, we are talking
gigabytes per minute, and a slowdown that changes the very behaviour being observed.

**The targeted mode** — designating the methods that interest us — is what all the tools
concerned offer: `watch <class> <method>` in Arthas, an XML of probes in the JMC Agent, a
`filter` in JFR under Java 25. Its counterpart: **one has to know what to look at before
looking.**

**Recommendation: targeted, and a two-stage way of working.**

1. **Spot** with the exhaustive, cheap tools — JaCoCo's coverage says where one went, the
   flame graph says where the time goes. No decision to take at this stage.
2. **Understand** by targeting: once the interesting method is identified, `watch` on it to
   see the values, `trace` to see its tree.

This is not a makeshift: it is how one works with a debugger — one does not lay a breakpoint
on every line. **And above all, it separates no tool from another**: none offers the general
mode in a bearable way. This question defines the *method*, not the *choice*.

### Question 5, developed: what is lost without IntelliJ Ultimate?

| Capability | Community | Ultimate |
|---|---|---|
| Coverage displayed in the editor's margin | ✅ | ✅ |
| Import of a JaCoCo `.exec` produced outside | ✅ *(to be confirmed on a machine)* | ✅ |
| Integrated profiler: flame graph, call tree | ❌ | ✅ (embeds async-profiler) |
| Opening a `.jfr` recording | ❌ | ✅ |

**Consequence, consistent with decision 5**: the **coverage** can be looked at in the IDE by
everybody, Community included. The **call tree**, no — the common channel remains
async-profiler's self-contained HTML, which has the advantage of being readable by somebody
who has no IDE at all.

Put otherwise, the absence of Ultimate costs nothing: its profiler's engine
(async-profiler) is free on the command line, and its output is *more* shareable than the
IDE's.

## The questions still to settle

| # | Question | What swings on the answer |
|---|---|---|
| 1 | **Do we buy a licence for capturing parameter values?** | Option A (€0, configuration to do) against Option B (~$500, IntelliJ integration included). This is **the** decision. |
| 2 | **Must parameter capture be targeted or general?** | Technically decisive: tracing one method of the hot path produced **129 MB of recording for 10 s of running**. A list of targeted methods is realistic, "capture everything" is not. |
| 3 | **How does the non-technician receive the report?** | An HTML folder sent as a file (JaCoCo alone suffices), or an internal server (on-premise SonarQube, self-hosted GitLab) — which adds a brick to install and maintain. |
| 4 | **Must the result be kept as a history?** | One-off diagnosis → this repository's scripts suffice. Tracking over time → a platform is needed, hence self-hosting (offline constraint). |
| 5 | **Is IntelliJ Ultimate justified?** | Its integrated profiler embeds async-profiler — which one gets free on the command line. The subscription is justified only for the comfort of staying in the IDE. |
| 6 | **Should the "final goal" (restructuring) be aimed at right now?** | If yes, Kieker enters the perimeter and changes the project's scale. If no, one tools the diagnosis and decides afterwards. |

## What remains to be done to settle it

1. **Activate the JProfiler (10 d) and YourKit (15 d) trials** and run them on this same
   `sample-app` — it is the only way of answering question no. 1 on the evidence. *(Human
   action: download and licence.)*
2. **Test a free track for capturing parameters** — the JMC Agent first, since it leans on JFR
   already present and stays offline.
3. **Install Arthas offline** from its complete package, to lift the blockage seen here.
4. **Publish the JaCoCo report at a URL** to validate the "I send it to somebody who is not a
   developer" channel.
