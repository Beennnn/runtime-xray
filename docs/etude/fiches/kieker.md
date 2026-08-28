# Kieker

> **Status: 📄 on documentation** — not run here.
> Version 2.0.3 (April 2026). Apache 2.0 licence. **€0.**

## What it is

A framework for **monitoring and architecture reconstruction**, out of research (University of
Kiel). It does not belong to the same family as the panel's other tools: where a profiler
answers "where does the time go?" and a coverage tool "where did we go?", Kieker answers
**"what does this software look like, seen from its execution?"**.

It is the only tool surveyed whose declared objective coincides with the **study's final
goal**: *analysing, restructuring and redefining the code from that data*.

## Its philosophy

**The trace is a material, not a report.** The other tools present what they measured and stop
there. Kieker explicitly separates two moments:

1. **the collection** — probes write a stream of typed records (method entry, exit, thread,
   timestamp) to a file, a message queue or the network;
2. **the analysis** — a processing chain reads that stream back and *derives* something from
   it: a dependency graph between components, a sequence diagram, an aggregated call tree, a
   deployment view.

That separation is its strength: the same collection can feed several analyses, and an analysis
can be replayed months later on archived traces. It is also its heaviness: nothing is ready to
look at until the second half has been built.

## How it instruments

Three ways, all **without modifying the source code**:

- **AspectJ** (1.9.25.1 in 2.0.3) — on-the-fly weaving, the historic mode;
- **ByteBuddy** (1.18.8) — bytecode rewriting, the modern mode;
- a probe API to write oneself when precise business data has to be captured.

It is by that last way that one obtains the **parameter values**: they are not captured by
default, they have to be declared.

## What it produces — and why that is different

| Output | What the other tools do not have |
|---|---|
| **Dependency graph** between classes, packages, components | A **structural** view, not a temporal one: who depends on whom, as the run revealed it — and not as the `import`s declare it |
| **Reconstructed sequence diagrams** | The real chaining of the calls, in the form an architect reads |
| **Deployment view** | Which part runs where, on a distributed system |
| **Archivable and replayable traces** | The other tools produce a frozen report; Kieker produces data one re-analyses |

That is the whole difference with the base retained: JaCoCo, async-profiler and Arthas answer
**one-off** questions about a piece of code. Kieker aims at a **representation of the entire
system**, on which one decides a decomposition.

## What one can hope for from it for the "final goal"

If the objective is really to **restructure** — extracting modules, redividing
responsibilities, breaking dependencies — then the *observed* dependency graph is worth far
more than a static analysis: it tells the dependencies **really exercised** apart from those
that exist only in the `import`s. That is not anecdotal: it is precisely the gap that makes
overhauls decided on paper fail.

Concretely, on code being taken over, it would allow answering: *which classes always work
together?*, *which subset could come out as a self-contained module without breaking the
rest?*, *which declared dependencies are never used?*

## What it costs

⚠️ **It is the heaviest of the panel, and by far.**

- The **analysis chain** has to be built on top of the collection — the diagrams do not fall
  out of a JVM flag.
- The vocabulary is that of research ("records", "filters", "analysis pipelines"): there is a
  step to climb before the first result.
- The volume of traces is of the same order as the one seen with JFR: on a hot path, it counts
  in hundreds of megabytes. Selecting the instrumentation points is not optional.

⚠️ **Java compatibility**: version 2.0.3 targets **Java 17 by default and announces support up
to Java 23**. Java 21 is therefore covered — but **Java 25 is not yet**. If the port to 25 is
decided, this point is to be rechecked before counting on Kieker.

## How one navigates in it

No navigating in the code: Kieker produces **diagrams**, which one looks at. It therefore does
not replace the coverage report for the question "show me this line" — it answers at another
scale.

## Verdict for this project

**To be considered in a second stage, as a project in itself.** The base retained (JaCoCo +
async-profiler + Arthas) answers the need described — knowing where the code goes, seeing the
call tree, reading the values. Kieker answers the *next* question: what to do with that code
once it is understood.

Opening it now would amount to building an analysis infrastructure before having the first
measurement. The reasonable order is the reverse.

## Comparable to

- **[OpenTelemetry](opentelemetry.md)** — Both produce traces meant to be processed elsewhere. But OTel aims at monitoring a running system, Kieker aims at **understanding its structure**.
- **[Glowroot](glowroot.md)** — Glowroot shows traces, Kieker **derives an architecture** from them. It is not the same question.
- **[Arthas](arthas.md)** — At the exact opposite: Arthas answers a one-off question in three seconds, Kieker builds a representation of the whole system.

## Easy / less easy

**What is easy.** Nothing, honestly — and that is the price of what it aims at. No other tool
reconstructs an architecture from the run.

**What is less so.** Everything: instrumentation to configure, an analysis chain to build, a
volume of traces to master, a vocabulary to learn. And a Java compatibility to recheck if the
port to 25 is retained.
