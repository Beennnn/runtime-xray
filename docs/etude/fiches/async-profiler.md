# async-profiler

> **Status: ✅ tested here** — outputs in [`reports-demo/generated/async-profiler/`](../../../reports-demo/generated/async-profiler)

## What it is

A sampling profiler for the JVM, in the form of a native agent. It regularly interrupts the
program, notes the call stack, and aggregates the whole into a tree.

## Its philosophy

**Measuring without deforming.** Profilers that instrument every method change the behaviour
they measure — the JVM no longer optimises the same way. async-profiler samples at a fixed
interval, with an overhead of a few per cent.

The price of that discretion: it is **statistical**. A rare method may appear in no sample at
all. It can therefore never assert that a piece of code did *not* run — which is exactly
JaCoCo's complement.

## What it can do

| Capability | |
|---|---|
| Call tree | ✅ complete, with % of time per branch |
| Flame graph | ✅ self-contained interactive HTML |
| Executed lines | ❌ (sampling: no proof of non-execution) |
| Parameter values | ❌ |
| Memory, lock, wall-clock profiles | ✅ (`alloc`, `lock`, `wall`) |
| Formats | HTML (flame graph, tree), collapsed, JFR |

## Its interface

A **single HTML file**, with no external dependency.

**Tree view** — unfoldable, searchable, with the percentage and the sample count:

![Call tree](../../assets/shots/async-tree.png)

**Flame graph** — a bar's width is the time spent; one clicks to zoom in:

![Flame graph](../../assets/shots/async-flamegraph.png)

## How one navigates in it

- **Outside an IDE** — a single `.html` file: one opens it, unfolds, searches for a method
  name, zooms on a branch. Sendable as an attachment, readable offline.
- **In IntelliJ** — IntelliJ IDEA **Ultimate** embeds async-profiler as the engine of its
  integrated profiler. Community, no. Put otherwise: the engine is free, it is the IDE's
  comfort that is paid for.
- **No GitHub / GitLab integration** — a call tree has no place in a diff.

## Setting up

```bash
# Nothing to install: the orchestrator fetches the jar from Maven Central (or the internal
# mirror) and extracts the platform's native library from it, once only.
# For a manual installation, the ASYNC_PROFILER_LIB variable points at a copy of one's own.
brew install async-profiler       # optional, if one prefers to install it oneself
./tools/async-profiler/collect.sh
```

Then a single JVM flag. **No modification of the analysed code.**

> ⚠️ On macOS, `perf_events` does not exist: use `event=itimer`.
>
> ⚠️ Without `-XX:+DebugNonSafepoints`, the frames are attributed to the wrong line number.
>
> ⚠️ Without `include=lab/sample/*`, 60 % of the samples are the JIT compiler's threads —
> exact, but unreadable for somebody looking for their own code.

## Licence and cost

**Apache 2.0**, open source, **€0**. A native binary installed once: it works offline.

## What one can hope for from it

**Alone**: the best free answer to "show me the call tree", in a format one shares with nothing
to install on the recipient's side.

**Combined**: with JaCoCo, two of the study's three needs are covered for €0, offline. It can
also write in the JFR format, hence feed Mission Control.

## Measured here

After refining the program, **71 % of the samples have a leaf in `lab.sample`**, and the
profile's top five leaves are business methods. The profile moreover served to fix three real
defects in the example program — see
[the technical details](../../outil/technique.md#refinements-decided-by-measurement-not-by-guesswork).

## Comparable to

- **[JFR](jfr-jmc.md)** — **The direct competitor**: the same nature of data, sampled stacks. Under Java 21, async-profiler wins on setting up and on readability (self-contained HTML against a binary + a desktop application). Under Java 25, JFR moves ahead thanks to exact counting.
- **[VisualVM](visualvm.md)** — The same service, in a graphical interface. More immediate to open, but nothing comes out of it: no file to hand on, no command to replay.
- **[JProfiler](jprofiler.md) · [YourKit](yourkit.md)** — Paid, and richer: they correlate the profile with memory, locks, and argument values. On the call tree alone, the gap is small.
- **[IntelliJ Ultimate](intellij.md)** — Not a competitor: its profiler **embeds async-profiler**. One pays for the integration, not for the engine.
- **[JaCoCo](jacoco.md)** — **Complementary.** One says where the time goes, the other what never ran.

## Easy / less easy

**What is easy.** One flag, one HTML file one opens or sends. Zero configuration once installed.

**What is less so.** Getting rid of the noise: without `include=`, 60 % of the samples are the JIT compiler, and without `DebugNonSafepoints` the line numbers are wrong — two options one has to know about. And **interpreting a sampled profile**: it never proves that a piece of code did not run.
