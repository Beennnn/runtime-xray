# Evaluation keys — how to obtain them, where to keep them

> This page describes the steps to take to bring the licensed tools into the study.
> **These are human actions**: they demand a named e-mail address and, depending on the
> publisher, a manual validation. None can be automated from this repository, and **none has
> been undertaken** — this page lists the procedures, it reports on no step actually taken.

## Absolute rule: no key in the repository

The repository is **public**. A committed licence key stays in the git history even after the
file is deleted, and automatic scanners find it within minutes. Three protections, cumulative:

1. **The keys live outside the repository**, in a directory outside any git tree — and, ideally,
   replicated automatically by whatever backup the machine already has.
2. **The scripts read them through an environment variable**, never from a file in the
   repository:

   ```bash
   # ~/licences/runtime-xray/licences.sh
   export JPROFILER_LICENSE_KEY="..."
   export JPROFILER_LICENSE_NAME="..."
   export YOURKIT_LICENSE_KEY="..."
   ```

   ```bash
   source ~/licences/runtime-xray/licences.sh
   ./tools/jprofiler/collect.sh
   ```

3. **The `.gitignore` blocks the usual shapes** (`*.license`, `licence*.sh`, `.env.local`) — a
   safety net, not the main line of defence: the real protection is that the file is not in the
   tree at all.

> An alternative for those who prefer to leave nothing in the clear on disk: the macOS keychain.
> `security add-generic-password -s runtime-xray -a jprofiler -w '<key>'` to deposit,
> `security find-generic-password -s runtime-xray -a jprofiler -w` to read back in a script.

## Before asking for anything: what we already have

Asking for a trial only makes sense if one knows **what one is trying to obtain on top**. Here
is the verified state of the free base, on the same application, under Java 21:

| Need | Tool | What one gets, concretely |
|---|---|---|
| Executed lines | JaCoCo | HTML site, code coloured line by line, 91.1 % of instructions covered, dead classes in red |
| Cumulative call tree | async-profiler | Self-contained unfoldable HTML, % of time per branch, 71 % of samples on business methods |
| Call tree of **one** call | Arthas `trace` | The tree of one invocation, **with the line numbers**, conditional branches included |
| **Parameter values** | Arthas `watch` | `@Mode[CAR] @Weather[SUNNY] @TimeOfDay[RUSH_HOUR]` and the return value |

**The study's three needs are covered, for €0, offline.** The commercial trial therefore no
longer serves to fill a gap: it serves to answer a narrower question.

## What is expected of the commercial tools, and why

### The main gain: aggregation by parameter value

This is the only point where one can reasonably expect better, and it deserves to be stated
precisely because it is what would justify the expense.

**What Arthas does**: it shows the values of **a few individual calls** — the next five, for
example. Useful for understanding one case, insufficient for reasoning about a set.

**What JProfiler claims** (*method splitting by parameter values*): **splitting the call tree
by argument value**. Over 16,000,000 calls, that would give one subtree for `mode=CAR`, another
for `mode=TRAIN`, with their respective times — that is to say, the answer to *"which context
costs a lot?"*, and not only *"what happened on this particular call?"*.

Arthas cannot aggregate that way. **That is the gain to verify, and it is the only one worth
$549.**

### The secondary gain: one tool instead of three

Today: an agent for the coverage, an agent for the profile, an attached console for the values
— three procedures, three output formats. A commercial profiler would bring the profile and the
values together in a single session, with the time / memory / lock correlation in the same
views.

A real gain, but **one of comfort**: decision no. 1 explicitly classed it secondary.

### What must NOT be expected of them

- **Line coverage** — neither JProfiler nor YourKit produces it. JaCoCo stays necessary in
  every case.
- **A report readable by a non-developer** — their snapshots export, but they are not worth
  JaCoCo's annotated HTML on that ground. The channel retained (decision no. 3) would not
  change.
- **IntelliJ integration** — downgraded by decision no. 5, since one cannot suppose everybody
  has Ultimate.

### The decision criterion, in one sentence

> If *method splitting by parameter values* brings something Arthas does not give in three
> commands, the licence is justified. Otherwise, no.

That is what has to be verified during the 10 and 15 days of trial — not "is the tool good",
but **that question**.

## JProfiler (ej-technologies)

| | |
|---|---|
| **Step** | Online form: [ej-technologies.com/jprofiler/trial](https://www.ej-technologies.com/jprofiler/trial) |
| **Information asked for** | Name, company, e-mail address. The key arrives **by e-mail** — the address must be exact |
| **Duration** | 10 days *(to be confirmed on the form at the time of the request)* |
| **Data kept by the publisher** | Name, company, e-mail and anonymised IP; a reminder e-mail is sent during the evaluation |
| **Download** | [ej-technologies.com/download/jprofiler](https://www.ej-technologies.com/download/jprofiler/files) |
| **Open-source licence** | Free for open-source projects — to be asked of the publisher |
| **⚠️ Offline** | The question is not the download (allowed) but the **activation**: a key entered once and validated locally is fine; an online check **at every launch** would be disqualifying. To be confirmed by the publisher |

**Prices (read on 2026-08-20 on the publisher's store)** — perpetual licences:

| | Without support | With 1 year of support and updates |
|---|---|---|
| Single licence | **$549** | $768 |
| Floating licence (1 concurrent user) | **$2,199** | $3,078 |

## YourKit Java Profiler

| | |
|---|---|
| **Step** | Direct download: [yourkit.com/download](https://www.yourkit.com/download/) — the trial is built in |
| **Duration** | **15 days** |
| **Open-source licence** | **Free**, in exchange for a link to YourKit on the project's pages. To be asked of the sales department |
| **Academic / scientific** | Subscription at **$99/year** per seat, **$999/year** for a whole institution |
| **⚠️ Offline** | **Floating** licences query, by default, a **licence server in the cloud during the run** — that is disqualifying. A **self-hosted** option exists: that is the one to ask for. *Seat* licences, validated locally, do not pose this problem |

**Prices (read on 2026-08-20 on [the pricing page](https://www.yourkit.com/java/profiler/purchase/))**:

| | Annual subscription | Perpetual |
|---|---|---|
| 1 seat (Basic) | $449 | **$549** |
| 1 seat (Advanced) | $579 | $713 |
| 5 seats (Basic) | $1,259 | $1,539 |
| Floating 1 user (Basic) | $2,249 | $2,749 |
| Floating 5 users (Basic) | $2,699 | $3,299 |

## IntelliJ IDEA Ultimate (JetBrains)

| | |
|---|---|
| **Step** | 30-day trial from the Toolbox or the JetBrains site |
| **Free licence** | Existing programmes for open source, teaching and students |
| **⚠️ Offline** | JetBrains activation normally goes through an online account; a **self-hosted licence server** exists for organisational licences. To be checked according to the setting |
| **Usefulness for the study** | Limited: the integrated profiler embeds async-profiler, already available free on the command line |

## What has to be measured once the keys are obtained

For the trial to serve to decide and not only to look, replay **exactly** the free tools'
protocol — same `sample-app`, same 10 s run, under Java 21 then Java 25:

1. **The capture of parameter values** — it is the only point that justifies the expense.
   Verify concretely that JProfiler's *method splitting by parameter values* really does
   separate the calls of `RoutePlanner.travelTimeMinutes` according to the mode of transport
   passed.
2. **The real overhead** — compare the time reported by `Metrics` with and without the tool.
   The 10 s of reference give an immediate basis for comparison.
3. **What comes out of the tool** — an exportable snapshot readable by a non-developer, or an
   interface one has to install in order to consult it? That is **criterion no. 2**.
4. **Offline activation** — the question that can disqualify everything. To be handled first,
   before even installing.

Record the results in [the comparison](comparatif.md) by moving the line from ⛔ to ✅, and drop
the outputs into `reports-demo/generated/<tool>/`.
