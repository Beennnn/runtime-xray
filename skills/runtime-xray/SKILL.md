---
name: runtime-xray
description: Observe a Java application while it runs — real coverage, where the time goes, values passed to a method — with runtime-xray. Use to run a campaign, choose the root method and the filters, observe a process one does not launch oneself (service, container, application server), or when a previous measurement did not show what was expected. Keywords - coverage, dead code, what code really runs, profiling, JaCoCo, async-profiler, Arthas, acceptance, test campaign; also in French - couverture, code mort, profilage, recette, campagne de tests.
---

# Running a runtime-xray campaign

`runtime-xray` launches a Java application, observes it while it runs, and assembles a
report: the code actually executed, where the time went, and the values passed to a chosen
method. No change to the analysed code, no change to the build.

It is a jar with **no dependency at all**: no Python, no shell, no Maven to install.

## First of all: find the jar

Do not assume it can be downloaded. The target machine has, by hypothesis, **no network** —
that is the tool's whole reason for being. Look in this order:

```sh
ls runtime-xray*.jar ./*/runtime-xray*.jar 2>/dev/null
ls ~/.runtime-xray/ 2>/dev/null
```

If there is none, ask where it is rather than trying to download it.

## The minimal command

```sh
java -jar runtime-xray.jar --java "java -jar my-app.jar" --out runtime-xray-out
```

That is enough to get a report. Everything else only serves to answer a more precise
question, or to pay less.

`--java` is executed **as it is**: the tool does not relaunch the application its own way,
it launches yours. A complex command — JVM options, system properties, arguments — is copied
across untouched.

## The one decision that takes thought: `--root`

```sh
--root "com.example.Processing::execute"
```

This is **the business entry point**: the method one wants to know the arguments of, and
what it set off. A processing step, a computation, a command. **Not a `main`, not an
accessor.**

Only one is named, and that is deliberate: capturing the values of every method would give
hundreds of megabytes on a busy path.

Without `--root` everything still works — only the values and the detailed call tree are
lost.

## The four restrictions do NOT restrict the same thing

**This is the most frequent confusion**, and it costs a whole campaign: one widens a filter
that had nothing to do with it, re-runs, and gets the same report.

| Option | What it commands, and **nothing else** |
|---|---|
| `--sources` | the display of source code in the page |
| `--root` | the capture of values, and the default time filter |
| `--filter` | the measurement of time, alone |
| `--cover` | JaCoCo's instrumentation, hence the coverage |

Before widening anything, work out **which** symptom is being seen, then take the matching
row. The report itself carries this table, written from the symptom.

## What one agrees to pay

On a large codebase, do not take everything on the first go.

```sh
--level coverage      # JaCoCo alone — the cheapest, often the most useful
--level tree          # + stack sampling
--level full          # + values (the default)

--cover "com.example.*"  # ← the main cost centre
--interval 10            # sampling at 10 ms instead of 1 ms
```

**`--cover` is the first lever.** Without it, *every class loaded* is instrumented,
dependencies included.

### When it is the number of files that costs

The levers above lower the cost of the **measurement**. Another cost exists, and does not
reduce to it: JaCoCo writes **two files per class**, and the tool asks it for **two sites
per run**. A campaign's file count therefore grows with the size of the code, not with the
measurement — and on a machine where every file open crosses an antivirus, an EDR, a DLP,
that is often the real brake.

**Start with the exclusion, it takes nothing away.** The `runs/` directory is the right
candidate for an antivirus exclusion: nothing in it is executed, everything in it is
generated and reproducible. It is the only measure that *removes* the cost. Everything below
reduces it, and each line below gives something up — the tool prints the file count itself
past ten thousand, and `runtime-xray --help` weighs the settings one by one.

```sh
--jacoco-reports detailed   # the complete site alone, without the focused one
--jacoco-reports data       # jacoco.xml and .csv only, no site per run
--jacoco-reports minimal    # and no merged site either — its XML and CSV are still written
--archive                   # gather runs/ into runs.zip, and keep the tree
--archive replace           # gather it, then put the archive IN PLACE of the tree
```

These settings change **neither the measurement nor what the page shows**: the coverage comes
from `jacoco.xml`, written in every case. `detailed` loses no data — the focused report
holds none the complete one lacks. `data` loses JaCoCo's rendering per run, but keeps the
whole campaign's; `minimal` loses that one too, keeping its figure. An absent report stays
named in the page, with the command that produces it.

`--archive replace` is the one to weigh: it removes the measurements once the archive has
been checked against them, so the page's links to the JaCoCo sites stop resolving and
`--report-only` has nothing left to rebuild from. The page, the diagnostic, the facts and
the Markdown still read.

Two things happen on their own and need no setting: the two sites of a run are rendered at
the same time, and the focused report is not written when every analysed class ran — it
would repeat the complete one.

## Observing a process one does not launch oneself

A systemd service, a container, an application server, an integration script: the tool
launches nothing, it hands over the text to paste.

```sh
java -jar runtime-xray.jar --print-options --out runtime-xray-out
```

Then the line obtained is added to the existing Java command, it is left to run, and the
report is assembled afterwards:

```sh
java -jar runtime-xray.jar --report-only --out runtime-xray-out
```

This is also the answer when the command is **buried in nested scripts**: one does not try
to carry the launch's intelligence up through the layers, one injects the agents once, right
at the bottom.

## Seeing what happens during the run

An analysis lasts as long as the observed application. Three ways of not waiting blind, from
the most universal to the most readable.

**1. The file — it works everywhere, there is nothing to ask for.**

```sh
tail -f runtime-xray-out/progression.jsonl
```

One JSON line per second, written in any case: seconds elapsed, cores busy, activity tier,
size of the output produced. The last line carries `"event":"end"` — that is what says one
can stop watching. The path does not depend on the run's name.

**2. The band in the terminal** — it shows on its own in front of a real terminal, and stays
quiet in a pipe. A nested shell often puts a pipe in the middle; one then asks for it:

```sh
RUNTIME_XRAY_PROGRESSION=1 java -jar runtime-xray.jar …
```

**3. The page, when one has a browser.**

```sh
java -jar runtime-xray.jar --follow …          # http://127.0.0.1:8788
```

It shows what a sequence of JSON lines shows badly: the **shape** of the run — the activity
band, the curve of busy cores, the output growing or no longer growing, and the tail of the
application's log. Local loopback only.

**None of the three measures anything.** The processor time comes from what the system
counts anyway, the output size from a file already written. The report is identical whether
one follows the run or not — a display that shifted the measurement would make the report it
accompanies lie.

## Several runs

Runs **accumulate** in the same `--out`. Name them, otherwise the report becomes unreadable:

```sh
--out campaign --name "nominal acceptance"
--out campaign --name "degraded acceptance"
```

The page then allows accumulating the coverage of the ticked runs, and seeing which run
covered which line.

## On a machine with no network

The three analysis components (JaCoCo, async-profiler, Arthas) are looked for **on the
machine first**, the network comes last. If the launch fails for want of components, the
message lists **every path tried**: that message is what suffices to get out of it, and it
must be read in full rather than guessed at.

```sh
--components /path/to/the/components   # takes priority over any download
--repo https://mirror.internal/maven   # internal repository, as a last resort
```

## After the measurement: check before concluding

A disappointing report is **silent**: an empty code panel reads exactly like a panel nobody
could fill.

- `diagnostic.json` is written at every assembly, without being asked for. It carries the
  environment, the launch's real configuration, the source roots actually opened, and the
  class-by-class matching. **It is the file to look at first** when the result surprises.
- The "Source analysis" section of the overview makes it readable, and offers source roots
  with figures — "this root would resolve 27 of the 27 classes without source".
- **Never guess a source root from a convention.** Wrong code displayed against a coverage
  costs more than an empty panel, because it is believed.

## A platform limit to announce, not to discover

**Under Windows there is no time measurement**: async-profiler publishes only Linux and
macOS binaries. Coverage and values work. The tool says so at launch. A zero time under
Windows therefore does not mean "never called".

## Next

To answer a question from the report obtained, see the **runtime-xray-read** skill.
