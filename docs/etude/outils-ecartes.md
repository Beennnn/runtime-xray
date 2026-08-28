# Tools ruled out, and why

> This page exists so that the rejections are **motivated and dated**, and so that the same
> questions are not asked again in six months. None of these tools is bad: they are
> incompatible with *these* constraints.
>
> ⚠️ **A precision on the offline criterion**: it bears on the **run**, not on the
> installation. Downloading a tool beforehand — from the Internet or from an internal mirror —
> poses no problem at all. What is disqualifying is needing the network **while the program is
> running**. That is the reading applied below.

## Ruled out by the offline constraint

### Datadog, New Relic, Dynatrace — ⛔ architectural incompatibility

Their model rests on a **permanent outbound connection, during the run**: the agent
instruments, but **displays nothing** — it sends, continuously, to the publisher. It is
therefore not an installation problem that downloading in advance would settle: with no
outbound link at the moment the program runs, the agent has nowhere to write.
It is not a setting, it is the product.

**The usable equivalent**: [Glowroot](fiches/glowroot.md) (agent + embedded web server,
self-hostable) or [OpenTelemetry](fiches/opentelemetry.md) + Jaeger/Tempo.

### Codecov, Coveralls, SonarCloud — ⛔ same reason

These are the coverage displayers GitHub uses for want of native support. All SaaS.
**The usable equivalent**: JaCoCo's HTML report (no service at all), or
[SonarQube Community](fiches/sonarqube.md) self-hosted if a permanent portal is wanted.

### Coverage visualisation in a GitHub pull request — ⛔ no native solution

GitHub cannot display the covered lines in a diff without a third-party service, hence without
a connection. **A self-hosted GitLab, for its part, does it natively** from a Cobertura report
— if the internal forge is a GitLab, this channel becomes available again.

## Ruled out by the decisions taken

### SonarQube, Glowroot, OpenTelemetry — out of perimeter

Technically compatible offline (all self-hostable), but they suppose **a server to install and
maintain**. Decisions no. 3 (a file one sends) and no. 4 (no history keeping) make that
infrastructure pointless.

**To be reopened if** the need moves from a one-off diagnosis to tracking over time.

### IntelliJ IDEA Ultimate's profiler — off the critical path

One cannot suppose that everybody has an Ultimate licence (decision no. 5). And the finding is
a comfortable one: its profiler **embeds async-profiler**, which one gets free on the command
line, with an output *more* shareable than the IDE's.

### JProfiler, YourKit — postponed, not rejected

Decision no. 1 classes them secondary, and Arthas filled the gap that justified them. They keep
one precise interest: **aggregating the call tree by parameter value**. See [the evaluation
keys](cles-evaluation.md#what-is-expected-of-the-commercial-tools-and-why).

## Ruled out because another tool does better, for the same price

| Tool | Why it is not retained |
|---|---|
| [VisualVM](fiches/visualvm.md) | Dominated by async-profiler on the call tree, and nothing comes out of it: no file to hand on, no script to replay |
| [OpenClover](fiches/openclover.md) | Answers "which test covers what?", not "what did this run do?". Instrumentation at compile time, hence more intrusive than JaCoCo |
| [BTrace, Byteman](fiches/btrace-byteman.md) | Do what Arthas does, but demanding a script or a rule to be written per question |
| [JMC Agent](fiches/jmc-agent.md) | Good idea — declarative capture backed by JFR — but no ready-to-use binary found, where Arthas works in two commands |

## Deferred, not ruled out

### [Kieker](fiches/kieker.md) — the right tool for the next question

The only one that aims at the study's **final goal**: reconstructing an architecture from the
run. But it is a project in itself, and opening it before having the first measurement would
reverse the reasonable order.

⚠️ To be rechecked if the port to Java 25 is retained: version 2.0.3 announces support **up to
Java 23**.
