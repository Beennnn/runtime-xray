# The solution retained — one run, three tools, one view

> No licence · offline · €0 · verified end to end under Java 21.

**[→ See the integrated view](https://beennnn.github.io/runtime-xray/multi/)**

![Overview](../assets/shots/vue-accueil.png)

*On opening: what the run produced, and on the left **the list of all the code that ran**,
from the most costly to the least. No tree to descend — the list is exhaustive, established
by JaCoCo.*

![A method in detail](../assets/shots/vue-integree.png)

*One click opens the code: executed lines in green, purple annotations saying which call
leaves from which line and in how long, and at the bottom the values actually passed. The
"show only what runs under this method" button restricts the left-hand list to that
perimeter.*

## The three tools and their roles

| | Tool | What it brings | What it cannot do |
|---|---|---|---|
| <img src="../assets/icons/jacoco.svg" width="22"> | **JaCoCo** | The executed lines, to the line, and above all **the ones that never were** | Nothing about time, nothing about the order of calls |
| <img src="../assets/icons/async-profiler.svg" width="22"> | **async-profiler** | The **call tree** aggregated over the whole run, with each branch's share of the time | Never proves that a piece of code did not run (it samples) |
| <img src="../assets/icons/arthas.svg" width="22"> | **Arthas** | The **parameter values**, and the tree of **one single** call with its line numbers | No report to hand on: it is a console |

The three complement each other exactly: what one ignores, another covers.

## The protocol: one single run

[`tools/run-all.sh`](../../tools/run-all.sh) launches **one single process** with both agents
attached at startup, then plugs Arthas into it while the calls are happening:

```
java -javaagent:jacoco-agent.jar=destfile=…          ← instruments the bytecode
     -agentpath:libasyncProfiler.dylib=start,…       ← samples the stacks
     -jar sample-app.jar --iterations 24000000       ← time enough to attach
                                                     ↳ arthas-boot --arthas-home … -f watch-params.as
```

```bash
./tools/run-all.sh
```

### Why a single pass, when Arthas disturbs the measurement

The question deserves to be settled explicitly, because the disturbance is real and large —
but it does **not** touch what the project is after.

Here is, objective by objective, what the single pass gives:

| Objective | State in a single pass | Verified how |
|---|---|---|
| **1 — executed lines** | ✅ **intact** | The script checks at every run that the observed class keeps its coverage. Arthas *retransforms* it, which could have wiped it out: it does not |
| **1 — call tree (the shape)** | ✅ **intact** | The structure of the calls stays complete: `RoutePlanner → legMinutes → Terrain…`. Nothing disappears |
| **2 — parameter values** | ✅ intact | Arthas is precisely what supplies them |
| *time percentages* | ⚠️ **skewed** | More than 80 % of the samples fell inside Arthas's instrumentation (`SpyAPI.atBeforeInvoke` 41 %, `atAfterInvoke` 40 %) |

**What is degraded is the one indicator that is not a criterion of the project.** Computation
time was explicitly ruled out of the criteria: optimising performance is a separate subject,
to be taken up later, once the code is understood and redesigned. Sacrificing the precision
of a time measurement to obtain all three kinds of data in a single launch is therefore a
good trade.

### The mitigation applied

Rather than displaying a misleading profile, the integrated view **folds Arthas's frames
back onto the application method that triggered them**. The samples stay counted, but
attributed to the business code rather than to the observation tool: the shape of the
distribution is restored, and the tree becomes readable again.

A banner shows the caveat at the top of the view, with the share of samples concerned — the
problem is not hidden, it is announced.

**A caveat that remains**: the displayed cost of the *observed method* stays overestimated,
because the instrumentation's own work is measured too. The other methods are unaffected.

### If the percentages have to be right

```bash
java -jar runtime-xray.jar --java "…" --no-values      # measurement alone, clean profile
java -jar runtime-xray.jar --java "…" --root "…"       # inspection, on a second pass
```

Two runs of the same deterministic scenario: the measurement first, with nothing
instrumenting, then the inspection. To keep for the day time really matters — that is to
say, not within this study, where time is not a criterion.

### What was verified, and did not go without saying

**The three agents cohabit without corrupting the coverage.** Arthas retransforms the
classes it observes, when JaCoCo has already instrumented them — the coverage could have
been lost on those precise classes. The check is therefore automatic, at every launch:

```
▶ Integrity check: did the coverage survive Arthas's retransformation?
   RoutePlanner: 93 instructions covered — OK
```

## The integrated view

The orchestrator produces **one self-contained HTML file** from the three tools' machine
outputs. It measures nothing: it assembles.

| Source read | What it feeds in the view |
|---|---|
| `jacoco.xml` | The colour of the lines, and the starting line of each method |
| `profil.collapsed` | The call tree and the percentages |
| `trace-calltree.txt` | The "which call leaves from this line, in how long" annotations |
| `watch-params.txt` | The panel of the values actually passed |
| the project's `.java` files | The code displayed |

### What it allows

- **Two navigations, as you like**: by **call tree** (one follows the run) or by **packages
  and classes** (one looks for a precise file). Both lead to the same code view.
- **Everything under the eye**: selecting a node shows at once the executed lines, the calls
  that leave from them, their cost, and the values passed.
- **Hiding code that did not run**: a checkbox removes from the report the classes never
  reached and hides the dead lines, saying how many were folded away. That is the answer to
  "export a cleaned-up result, without being polluted by unused code".
- **Self-contained**: one single file, no external resource, no server — it opens offline and
  is sent as an attachment.

### Its limits, stated

- The line annotations cover only the method traced by Arthas — tracing all the code would
  produce an unmanageable volume (129 MB for 10 s on a single hot method).
- The values displayed are those of **a few calls**, not an aggregation by value. That is
  precisely what commercial tools can do on top.
- The call tree is **sampled**: a rare method may not figure in it. To know what never ran,
  it is the coverage that is authoritative, not the tree.

## What it costs

| | |
|---|---|
| Licences | **€0** |
| Connection during the run | **none** |
| Connection to install | yes, once — JaCoCo and Arthas from Maven Central or an internal mirror, async-profiler through the package manager. The criterion bearing on the run, this is not a constraint |
| Installation | three tools, all scripted in [`tools/`](../../tools) |
| Run | two launches of the program, ~30 s each |
