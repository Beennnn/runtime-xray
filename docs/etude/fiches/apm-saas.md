# SaaS APM — Datadog, New Relic, Dynatrace

> **Status: ⛔ eliminated by the offline constraint.** This sheet exists so that the rejection
> is motivated, not implicit.

## What it is
Hosted application-monitoring platforms: an agent instruments the application and sends the
data to a remote service, where everything is stored and presented.

## Its philosophy
**Observability as a service.** Nothing to install on the server side, everything is supplied:
storage, correlation, alerts, dashboards, retention. The model rests entirely on the permanent
outbound connection to the publisher.

## Why it is eliminated here
The **"environment disconnected from the Internet"** constraint is incompatible with the model
itself: with no outbound link, the agent has nowhere to write. It is not a question of
configuration, it is the product's architecture.

Two further points, secondary but real: the **usage-based cost** is the highest of the panel,
and the need described is a **one-off diagnosis** — not continuous monitoring.

## The usable equivalent
If the appeal is the polished web interface, the self-hostable counterpart exists:
**[Glowroot](glowroot.md)** (agent with embedded server) or
**[OpenTelemetry](opentelemetry.md) + Jaeger/Tempo** (a complete chain to build).

## Licence and cost
Commercial, usage-based billing.

## What one can hope for from it
Nothing in this context. The sheet is here so that the question is not asked again in six
months.

## Comparable to

- **[Glowroot](glowroot.md)** — **The recommended replacement**: the same promise, agent and interface self-hosted, hence usable with no network during the run.
- **[OpenTelemetry](opentelemetry.md)** — The open road to the same result, at the price of a collector and a backend to install.

## Easy / less easy

**What is easy.** Everything, if one is connected: that is the very principle of the model.

**What is less so.** **Nothing is possible offline.** It is not a difficulty to overcome, it is an architectural incompatibility.
