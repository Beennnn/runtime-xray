# Tool sheets

One page per tool: what it is, its philosophy, what it can do, what its interface looks like,
how one navigates in it, its licence, and what one can hope for from it — alone or combined.

The overview is in [the comparison](../comparatif.md), the decision in
[the results](../../resultat/resultats.md).

## WITHOUT LICENCE — free, self-hostable

### Tested on the target application

| Tool | What it brings |
|---|---|
| [JaCoCo](jacoco.md) | ✅ The executed lines, with annotated source code navigable outside an IDE |
| [async-profiler](async-profiler.md) | ✅ The call tree, as self-contained interactive HTML |
| [JFR & Mission Control](jfr-jmc.md) | ✅ The base included in the JDK — and the difference measured between Java 21 and 25 |

### Tracks for the parameter values — the free base's hole

| Tool | What it brings |
|---|---|
| [Arthas](arthas.md) | 🚧 Interactive capture of the values, web console — candidate no. 1 |
| [JMC Agent](jmc-agent.md) | 📄 Declarative capture, backed by JFR already present |
| [BTrace & Byteman](btrace-byteman.md) | 📄 Capture by script or by rule, the most demanding |
| [IntelliJ IDEA](intellij.md) | 📄 Non-suspending breakpoint: the immediate free answer, but a handmade one |

### Other tools surveyed

| Tool | Why it figures here |
|---|---|
| [VisualVM](visualvm.md) | 📄 The shortest path to a first glance |
| [OpenClover](openclover.md) | 📄 Coverage **per test**, if the question arises |
| [Glowroot](glowroot.md) | 📄 Polished web interface, self-hostable hence offline |
| [SonarQube](sonarqube.md) | 📄 The best displayer of JaCoCo's coverage |
| [Kieker](kieker.md) | 📄 The only one that aims at the "final goal": reconstructing the architecture |
| [OpenTelemetry](opentelemetry.md) | 📄 The standard, but it is an infrastructure |

## WITH LICENCE — commercial

Prices read on 2026-08-20. Steps for obtaining the trial keys and storing them outside the
repository: **[the evaluation keys](../cles-evaluation.md)**.

| Tool | What it brings | Price |
|---|---|---|
| [JProfiler](jprofiler.md) | *Method splitting by parameter values* — paid candidate no. 1 | $549 perpetual |
| [YourKit](yourkit.md) | Extensible probes; free for open source, $99/year academic | $549 perpetual |
| [IntelliJ IDEA Ultimate](intellij.md) | Integrated profiler — which embeds async-profiler, free otherwise | subscription |
| [APM SaaS](apm-saas.md) | ⛔ Eliminated by the offline constraint — a motivated rejection | usage-based |
