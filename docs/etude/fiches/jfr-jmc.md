# JFR (Java Flight Recorder) & JDK Mission Control

> **Status: ✅ tested here** (JFR on the command line) · **📄 on documentation** (the JMC interface)
> — outputs in [`reports-demo/generated/jfr/`](../../../reports-demo/generated/jfr)

## What it is

**JFR** is an event recorder built into the JVM itself. **JMC** is the desktop application that
opens and analyses those recordings.

## Its philosophy

**Being already there.** JFR is not a tool one adds: it is a JDK subsystem, designed to stay
enableable in production with a minimal overhead. It records not "a profile" but a **stream of
typed events** — GC, locks, I/O, execution stacks — which one queries afterwards.

That generality is its strength and its limit: it sees everything, but nothing is presented in
the form "here is your call tree" without a reading tool.

## ⚠️ The decisive point: Java 21 against Java 25

JDK 25 brings **targeted method tracing** (JEP 520). **Under Java 21, the project's target,
those settings do not exist:**

```
[warning][jfr,start] The .jfc option/setting 'jdk.MethodTiming#filter' doesn't exist.
```

Same program, same duration:

| Event | Java 21 | Java 25 |
|---|---|---|
| `jdk.ExecutionSample` | 331 | 147 |
| `jdk.MethodTrace` | — | **594,877** |
| `jdk.MethodTiming` | — | **16,000,000 invocations counted exactly** |

**Why 25 is more precise**: Java 21 *samples* (a few hundred photographs of the stack, from
which one statistically infers where the time goes); Java 25 *instruments* the methods named in
the filter and counts **every** invocation. A change of nature, not of setting. In exchange the
instrumentation disturbs the JIT and makes the volume explode — detail and limits in [the
results](../../resultat/resultats.md#why-java-25-is-more-precise--the-mechanism).

Under Java 21, JFR supplies only sampling — less well than async-profiler, for more complexity.
**Under Java 25, it becomes a front-rank tool with nothing to install.** See [the gains from a
port](../../resultat/resultats.md#gains-from-a-port-to-java-25-no-gain-visible-in-the-report).

## What it can do

| Capability | Java 21 | Java 25 |
|---|---|---|
| Call tree | ⚠️ sampled | ✅ exact, per invocation |
| Call count | ❌ | ✅ exact |
| Executed lines | ❌ | ❌ |
| Parameter values | ❌ | ❌ — the signature is displayed, **not the values** |
| GC, locks, I/O, threads | ✅ | ✅ |

## Its interface

**On the command line** — the `jfr` tool shipped with the JDK:

```
jdk.MethodTrace {
  method = lab.sample.transfer.Timetable.frequencyMinutes(Leg)
  stackTrace = [
    lab.sample.transfer.Connections.transferMinutes(Leg, Leg) line: 32
    lab.sample.transfer.Connections.waitMinutes(List, int) line: 23
    lab.sample.RoutePlanner.travelTimeMinutes(Trip) line: 63
    lab.sample.Main.main(String[]) line: 55
  ]
}
```

**This extract is the clearest demonstration of the gap being looked for**: the complete stack
is there, the parameter's name and its type too (`Leg`) — but **never its value**.

**In JMC** — a desktop application (Eclipse RCP) with graphical views: flame view, call tree,
correlation with the GC and the locks. Rich, but it is an expert's tool: not a report one sends
to a non-technician.

## How one navigates in it

- **Outside an IDE**: ❌ — the `.jfr` is a binary, unreadable without a tool. No web page.
- **In JMC**: ✅ a complete interface, but desktop and technical.
- **In IntelliJ Ultimate**: ✅ opens `.jfr` files natively.
- **GitHub / GitLab**: ❌.

## Setting up

```bash
./tools/jfr/collect.sh
```

**Nothing to install**: it is a JVM flag. The script detects the JDK's version and adapts the
options — it does not pretend to have JEP 520 under Java 21.

> ⚠️ Tracing one method of the hot path produced **129 MB for 10 s** of running, beyond
> GitHub's file limit. Fine tracing is targeted, it is not sprinkled about.
>
> ⚠️ The binary `.jfr` triggers GitHub's secret scanner (a false positive). It is excluded from
> the repository; the text extracts are versioned.

## Licence and cost

**GPLv2 + Classpath Exception** (OpenJDK), **€0**, included in the JDK. JMC is distributed
separately under **UPL 1.0** *(to be confirmed)*. **Offline by construction.**

## What one can hope for from it

**Under Java 21**: a general-purpose diagnostic base (GC, locks, threads) already present, but
not the best answer to the study's needs.

**Under Java 25**: the exact call tree becomes free and installation-free — a serious argument
in favour of a port. The parameter values, for their part, stay out of reach in both cases.

## Comparable to

- **[async-profiler](async-profiler.md)** — **The direct competitor.** Under Java 21, it does better for less effort. Under Java 25, the ratio reverses: JFR counts exactly where the other samples.
- **[VisualVM](visualvm.md)** — The same family, older. VisualVM opens faster, JFR records more finely.
- **[JMC Agent](jmc-agent.md)** — Its natural extension: it adds to the JFR stream events carrying the **parameter values**, which JFR alone does not capture.
- **[JProfiler](jprofiler.md) · [YourKit](yourkit.md)** — They do what JFR does, plus the argument values, in a single interface — against a licence.

## Easy / less easy

**What is easy.** Starting a recording: nothing to install, it is a flag. It is impossible to make it simpler.

**What is less so.** **Getting something out of it**: the `.jfr` is binary, JMC is needed. Targeting the tracing without the volume exploding (129 MB for 10 s on a hot method). And under **Java 21**, getting better than sampling: it is not difficult, it is impossible.
