# OpenTelemetry (Java agent)

> **Status: 📄 on documentation** — not run here (demands a collector and a backend).

## What it is
The open instrumentation standard. Its Java agent automatically instruments the known
libraries and emits **traces**: trees of nested *spans*.

## Its philosophy
**Decoupling the production of the data from its exploitation.** The agent can display
nothing: it emits to a collector, which feeds the backend of one's choice (Jaeger, Grafana
Tempo, Zipkin). That is a strength for a lasting information system, and a **handicap for a
one-off diagnosis**: the whole chain has to be built before the first trace is seen.

## What it can do
Call tree between components ✅ · values ⚠️ only what one places in a span attribute ·
executed lines ❌.

⚠️ An important nuance: automatic instrumentation operates **at the boundaries** (HTTP, JDBC,
messaging), not method by method. To see inside a computation, one has to instrument by hand.

## How one navigates in it
Through the backend: Jaeger or Tempo, on the web ✅. Nothing in the IDE, nothing in
GitHub/GitLab.

## Setting up
⚠️ An agent **+ a collector + a backend**. Feasible offline (everything is self-hostable), but
it is an infrastructure, not a tool.

## Licence and cost
**Apache 2.0**, **€0** — plus the cost of running the backend.

## What one can hope for from it
Beside the point for the need described. It would become relevant if the question moved
towards the continuous monitoring of a distributed system.

## Comparable to

- **[Glowroot](glowroot.md)** — The "turnkey" version of the same idea: agent plus interface, with no infrastructure to build.
- **[SaaS APM](apm-saas.md)** — They consume OTel, precisely. The difference is where the data lands.
- **[Kieker](kieker.md)** — The same raw material — traces — for a different end: monitoring against reconstructing.

## Easy / less easy

**What is easy.** Instrumenting the boundaries (HTTP, database): the agent does it on its own.

**What is less so.** **Seeing inside a computation** — one has to instrument by hand. And building the complete collector + backend chain before seeing the first trace.
