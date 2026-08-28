# JProfiler

> **Status: ⛔ blocked by licence** — 10-day trial to be activated manually. Not tested here.

## What it is
A commercial Java profiler (ej-technologies), a market reference along with YourKit.

## Its philosophy
**Everything in a single tool, with zero configuration.** The selling point is immediacy: one
attaches, one sees. Where the free world demands assembling three tools, JProfiler aims at the
single answer — and charges for it.

## What it can do
| Capability | |
|---|---|
| Call tree | ✅ very rich |
| **Parameter values** | ✅ — *method splitting by parameter values*: the call tree **splits by argument value**, one branch per observed value |
| Memory, locks, JDBC | ✅ |
| Executed lines | ❌ |

> The *method splitting* deserves a pause: it is exactly the answer to what `sample-app` was
> designed to show — one and the same call that goes off into different subtrees **according to
> the parameter's value**. Where the others show an aggregate, JProfiler shows one tree per
> value. *(Claimed by the documentation, not verified here.)*

## Its interface
A rich desktop application, **plus a native IntelliJ plugin** — one of the rare tools that
answer criterion no. 4 directly.

## How one navigates in it
In its own interface ✅ · in IntelliJ ✅ · HTML/CSV export of the snapshots ⚠️ less direct than a
report designed to be shared · no platform integration.

## Setting up
Announced as configuration-free (remote attachment included). **To be verified in a trial** —
that is the object of action no. 1 of [the trade-offs](../../resultat/resultats.md).

## Licence and cost
Commercial, **perpetual licence**. Prices read on 2026-08-20 on the publisher's store:

| | Without support | With 1 year of support and updates |
|---|---|---|
| Single licence | **$549** | $768 |
| Floating licence (1 concurrent user) | **$2,199** | $3,078 |

Licence **free for open-source projects**, on request to the publisher.

⚠️ **Offline**: the key is entered locally, but one must **confirm with the publisher that no
online activation is demanded** before counting on the tool in a disconnected environment. A
question to ask at the same time as the trial request.

10-day trial by form — the procedure is detailed in
[the evaluation keys](../cles-evaluation.md#jprofiler-ej-technologies).

## What one can hope for from it
**The serious candidate for filling the free base's one hole.** The question is not "JProfiler
or JaCoCo" — it is "JProfiler **on top of** JaCoCo, or else a rougher free track (Arthas, JMC
Agent)?". That is trade-off no. 1.

## Comparable to

- **[YourKit](yourkit.md)** — **The direct competitor**, at a comparable price. JProfiler bets on finished, ready-to-use views; YourKit on extensibility through probes. To be tested together, on the same program.
- **[Arthas](arthas.md)** — The free alternative, and it already covers the need. What JProfiler adds is precise: **aggregation by parameter value**.
- **[async-profiler](async-profiler.md)** — On the call tree alone, the gap is small and the second one is free.
- **[IntelliJ Ultimate](intellij.md)** — Its plugin makes IntelliJ JProfiler's front end — which lessens the interest of paying *also* for Ultimate for its integrated profiler.

## Easy / less easy

**What is easy.** What the publisher sells: attach and see, with no configuration, with the native IntelliJ plugin. **To be verified in a trial** — it is a commercial promise, not a measurement.

**What is less so.** **Lifting the two unknowns before paying**: offline activation, and what the tool produces for a non-developer reader. The snapshots export, but the tool is not designed to hand on a report.
