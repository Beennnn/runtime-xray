# IntelliJ IDEA

> **Status: 📄 on documentation** — verifying supposes opening the IDE; not automatable here.
> IntelliJ IDEA **Community** is installed on this machine.

## What it is

The project's target IDE. It counts here on two grounds: it **displays** data produced by
other tools (coverage), and it **embeds** its own profiler in the Ultimate edition.

## Its philosophy

**Bringing the measurement back to where the code is written.** A report in a browser forces
one to make the round trip mentally; a coloured margin in the editor puts the information on
the line concerned, within reach of all the usual navigation shortcuts (go to declaration, find
usages, walk up the call hierarchy).

## What it can do

| Capability | Community | Ultimate |
|---|---|---|
| Coverage displayed in the editor | ✅ | ✅ |
| Import of an external JaCoCo `.exec` | ✅ *(to be confirmed)* | ✅ |
| Integrated profiler (call tree, flame graph) | ❌ | ✅ — **embeds async-profiler** |
| Reading `.jfr` recordings | ❌ | ✅ |
| Parameter values | ⚠️ through the **debugger**: a non-suspending breakpoint that logs an evaluated expression | same |

> The last row deserves attention: a non-blocking breakpoint that prints `trip.mode()` at every
> pass captures parameter values **with no extra tool at all, free, in the IDE**. It is
> handmade and limited to a debugging session — but it is the most immediate answer to the
> third need for a developer.

## How one navigates in it

This is **the** strong point: the coverage shows in the margin, one jumps from one call to
another, one walks up the hierarchy. No other channel offers that fluidity **to a developer**.
In exchange: none of it can be handed on to a non-technician — they would have to install the
IDE and open the project.

## Setting up

Load an existing coverage report: *Run › Show Coverage Data…*, then point at the `.exec`
produced by [JaCoCo](jacoco.md). **To be checked on a machine** — not tested here.

## Licence and cost

**Community**: Apache 2.0, free — but **without the integrated profiler**.
**Ultimate**: commercial subscription *(price to be checked on the JetBrains list)*.

## What one can hope for from it

For **criterion no. 4**, IntelliJ is the answer on the developer's side, and the Community
edition suffices for the coverage. For that precise need, the Ultimate subscription adds only
the comfort of having async-profiler in the IDE rather than on the command line — the engine
being the same and free elsewhere.

## Comparable to

- **[JaCoCo](jacoco.md)** — A supplier, not a competitor: IntelliJ **displays** what JaCoCo measured.
- **[async-profiler](async-profiler.md)** — Ultimate's profiler **is** async-profiler. The choice comes down to: on the command line (free, shareable output) or in the editor (paid, more comfortable).
- **[JProfiler](jprofiler.md) · [YourKit](yourkit.md)** — Both supply an IntelliJ plugin: the IDE becomes their front end.
- **[SonarQube](sonarqube.md)** — The other way of displaying the same coverage — in a web portal rather than in the editor.

## Easy / less easy

**What is easy.** For a developer, reading the coverage in the editor's margin and jumping from one call to another: nothing matches it for comfort.

**What is less so.** **Getting out of the IDE.** Nothing can be handed on to somebody who has not installed it. The integrated profiler is reserved to Ultimate, and importing an external `.exec` remains to be confirmed on a machine.
