# JMC Agent

> **Status: 📄 on documentation** — not run here; not found in a directly runnable form on
> Maven Central (the `org.openjdk.jmc` group publishes only the libraries there).

## What it is

A JVM agent from the Mission Control project that **adds bespoke JFR events**, without touching
the source code. One describes in an XML file the methods to instrument and the values to
capture; the agent rewrites the bytecode at load time.

## Its philosophy

**Extending JFR rather than replacing it.** Everything JFR already does — low-overhead
recording, standard format, reading in JMC — stays acquired; the agent only adds events the JVM
does not emit natively, among them **the parameter values and the return values**.

## What it can do

| Capability | |
|---|---|
| **Parameter values** | ✅ — declared in XML, captured into JFR events |
| Return values, fields | ✅ |
| Call tree | ✅ through the JFR stacks |
| Executed lines | ❌ |

## Its interface

No interface of its own: the events produced are read **in JMC**, beside the native events. A
real advantage — the parameter capture ends up correlated with the GC, the locks and the
execution times in one and the same recording.

## How one navigates in it

Like JFR: in JMC (desktop) or IntelliJ Ultimate. **No web page**, hence no direct channel
towards a non-technical reader.

## Setting up

An XML of probes to write, then `-javaagent:agent.jar=probes.xml`. The configuration is heavier
than a flag — that weighs against **criterion no. 1** — but it is declarative and versionable,
hence replayable identically.

## Licence and cost

An OpenJDK Mission Control project, **open source**, **€0**. Works offline once the jar is
fetched.

## What one can hope for from it

**The most coherent free track for the parameter values**, precisely because it leans on JFR,
already present. To be tested right after Arthas — and all the more interesting if the port to
Java 25 is decided, since everything then converges towards a single JFR recording.

## Comparable to

- **[Arthas](arthas.md)** — The immediate competitor, and it has a clear advantage: it works in two commands, with no XML to write. The JMC Agent's advantage lies elsewhere — its output is **structured** (JFR events), hence reusable.
- **[BTrace](btrace-byteman.md) · [Byteman](btrace-byteman.md)** — The same instrumentation work, expressed as code rather than as a declaration.
- **[JFR](jfr-jmc.md)** — It does not replace it, it **extends** it: same recordings, same reading tools, with extra events.

## Easy / less easy

**What is easy.** Leaning on what is already there: the events produced arrive in the same JFR recording as the GC and the locks, correlated.

**What is less so.** **Finding a ready-to-use binary** — it is not published in runnable form on Maven Central. Writing the XML of probes. And reading the result, which supposes JMC.
