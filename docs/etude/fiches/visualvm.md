# VisualVM

> **Status: 📄 on documentation** — not run here (desktop application, manual attachment).

## What it is

The Java ecosystem's historic monitoring and profiling tool, long shipped with the JDK, today
distributed separately. One launches it, it lists the running JVMs, one clicks on one of them.

## Its philosophy

**The universal entry point.** To demand nothing: no agent to configure, no flag at launch, no
licence. One opens it, one sees one's JVMs, one looks. It is the shortest path between "an
application is running" and "I see something" — hence its persistent popularity.

Its limit is the reverse of that simplicity: it shows the essentials and stops there.

## What it can do

| Capability | |
|---|---|
| Call tree | ✅ sampling or instrumentation |
| Memory, threads, GC | ✅ |
| Executed lines | ❌ |
| Parameter values | ❌ |

## Its interface

A desktop application: Monitor / Threads / Sampler / Profiler tabs, a sortable call tree.
Comfortable for a developer, **not a deliverable** one hands on.

## How one navigates in it

Only in its own interface. No web page, no output usable outside the tool, no platform
integration. An IDE plugin exists *(to be confirmed on recent versions)*.

## Setting up

⭐ **Very simple, but manual**: launch the application (hence the interest of the 10 s
calibration and of `--hold-seconds`), launch VisualVM, click. Nothing to script — hence
nothing to replay automatically either.

## Licence and cost

Open source, **€0** *(GPLv2 + Classpath Exception, to be confirmed)*. Offline.

## What one can hope for from it

A **first glance** in thirty seconds, with nothing to prepare. For this project, it is
dominated by async-profiler on the call tree (which produces a shareable file on top) and
answers neither the coverage nor the parameters. Useful as a stopgap tool, not as a solution.

## Comparable to

- **[async-profiler](async-profiler.md)** — Replaces it advantageously as soon as one wants to **keep** or **hand on** a result.
- **[JFR](jfr-jmc.md) · [JMC](jfr-jmc.md)** — Same family, out of the JDK. JMC records more finely, VisualVM is picked up faster.
- **[JProfiler](jprofiler.md)** — What VisualVM does, far more completely — that is the paid step.

## Easy / less easy

**What is easy.** Seeing something in thirty seconds, with nothing to prepare. That is its only argument, and it is a real one.

**What is less so.** **Replaying or sharing**: everything is manual and nothing comes out of the tool. No script, no file to hand on.
