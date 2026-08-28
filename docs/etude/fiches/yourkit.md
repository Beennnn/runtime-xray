# YourKit Java Profiler

> **Status: ⛔ blocked by licence** — 15-day trial to be activated manually. Not tested here.

## What it is
The other reference commercial Java profiler, JProfiler's direct competitor.

## Its philosophy
**Making the tool extensible.** Its particularity are the *probes*: sensors the user defines
themselves, through an open API, to capture data specific to their application. Where JProfiler
offers a finite and highly finished set of views, YourKit offers an extension mechanism.

## What it can do
Call tree ✅ · **parameter values** ✅ through the probes · memory, locks ✅ ·
executed lines ❌ · multi-format export ✅ (a strong point: its snapshots are handed on more
easily than the average).

## Its interface
A desktop application + an IDE plugin.

## How one navigates in it
Its own interface ✅ · IDE ✅ · varied exports ⚠️ · no platform integration.

## Setting up
Agent + interface. The probes demand development — which moves the cursor of criterion no. 1
according to whether one makes do with the probes supplied or writes some.

## Licence and cost
Commercial. Prices read on 2026-08-20 on [the pricing page](https://www.yourkit.com/java/profiler/purchase/):

| | Annual subscription | Perpetual |
|---|---|---|
| 1 seat (Basic) | $449 | **$549** |
| 1 seat (Advanced) | $579 | $713 |
| 5 seats (Basic) | $1,259 | $1,539 |
| Floating 1 user (Basic) | $2,249 | $2,749 |

**Free for open-source projects** (in exchange for a link to YourKit).
**Academic: $99/year** per seat, **$999/year** for a whole institution — markedly cheaper than
JProfiler in that niche.

⚠️ **Offline**: **floating** licences use, by default, a **licence server in the cloud**,
incompatible with the constraint. A **self-hosted** option exists — that is the one to ask for.
*Seat* licences do not have this problem.

15-day trial, direct download — see
[the evaluation keys](../cles-evaluation.md#yourkit-java-profiler).

## What one can hope for from it
The same role as JProfiler in the decision. To be tested in the same movement, on the same
`sample-app`, so as to compare on the evidence rather than on the brochure.

## Comparable to

- **[JProfiler](jprofiler.md)** — **The direct competitor.** Two philosophies: extensible probes here, finished views there. YourKit is markedly cheaper academically ($99/year) and free for open source.
- **[Arthas](arthas.md)** — Free, and sufficient for capturing values. YourKit's *probes* take the advantage only if one wants business data **aggregated** in the interface.
- **[Kieker](kieker.md)** — Its before/after snapshot comparison overlaps the restructuring objective Kieker addresses differently.

## Easy / less easy

**What is easy.** Getting started: direct download, the 15-day trial is built in, no form.

**What is less so.** **The probes** — the custom sensors, which are the tool's argument for capturing values, demand development. And on a floating licence, obtaining the self-hosted server rather than the cloud by default.
