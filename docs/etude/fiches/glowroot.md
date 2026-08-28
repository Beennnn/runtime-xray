# Glowroot

> **Status: 📄 on documentation** — not run here (demands deploying its agent + embedded server).

## What it is
An open-source APM (application monitoring): a JVM agent that records the transactions and
their traces, with **its own embedded web server**.

## Its philosophy
**An APM without a platform.** The APMs on the market suppose a remote service; Glowroot
embeds its interface and its storage in the agent. One launches the application, one opens a
browser onto the application itself. **That choice makes it offline-compatible**, where
Datadog or New Relic are eliminated outright.

## What it can do
Traces per transaction with a call tree ✅ · response times, percentiles ✅ · SQL queries ✅ ·
executed lines ❌ · parameter values ⚠️ limited and configurable.

## Its interface
**A complete and polished web interface** — the most "presentable" of the free solutions
surveyed, designed to be looked at every day.

## How one navigates in it
Browser ✅, with no installation on the reader's side (a URL suffices). No IDE, no platform.

## Setting up
One `-javaagent`, then a port to open. Heavier than an isolated flag: a service has to be kept
running.

## Licence and cost
**Apache 2.0**, **€0**.

## What one can hope for from it
The right choice **if the need slides from a one-off diagnosis towards tracking over time** —
that is precisely question no. 4 of the trade-offs. For a one-off analysis, keeping a server
running to read a call tree is out of proportion next to a self-contained HTML.

## Comparable to

- **[OpenTelemetry](opentelemetry.md)** — Same family — transaction tracing — but Glowroot embeds its server and its interface, where OTel demands building a collector and a backend. Far simpler to try.
- **[SaaS APM](apm-saas.md)** — **The self-hostable equivalent**: the same promise, without sending data to a publisher, hence compatible with an offline run.
- **[async-profiler](async-profiler.md)** — Two different scales: Glowroot traces transactions, async-profiler samples methods.

## Easy / less easy

**What is easy.** Looking: it is the finest free interface of the panel, ready to use.

**What is less so.** **Keeping a service running** to read a one-off call tree. And getting line coverage: that is not its trade.
