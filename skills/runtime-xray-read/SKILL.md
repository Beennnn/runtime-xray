---
name: runtime-xray-read
description: Answer a question from a runtime-xray report that has already been produced — which classes never ran, where the time goes, which run covered what, why the report does not show what was expected. Use as soon as a directory holds faits.jsonl, index.html + vue/, or diagnostic.json. Keywords - dead code, coverage, classes never executed, hot methods, acceptance campaign, run report, faits.jsonl; also in French - code mort, couverture, classes jamais exécutées, méthodes chaudes, campagne de recette.
---

# Reading a runtime-xray report

A report is a **folder**, not a file. What matters for answering a question is not the page.

| File | What it holds | For whom |
|---|---|---|
| `faits.jsonl` | the conclusions, one JSON object per line | **a program, a model — start here** |
| `diagnostic.json` | what the tool really did: environment, config, roots opened, class-by-class matching | when the report disappoints |
| `index.html` + `vue/` | the page, loaded in pieces | a human in front of a browser |
| `rapport.md`, `synthese.svg` | the summary to paste elsewhere | a ticket, a message |
| `runs/<name>/` | each run's raw measurements | a reprocessing |

**Do not read `index.html` to answer a question.** It is an application, not data: the code
in it is arranged to be displayed, not queried, and the big blocks live in `vue/`. The page
opens in English, with an `EN | FR` selector in its header — worth telling whoever receives
it, useless for answering a question.

## The shortest path

```sh
java -jar runtime-xray.jar --out <report-dir> --context "which classes never ran?"
```

That writes on standard output a **bounded, ready-to-read** extract: the facts that answer,
their vocabulary, and at the top what was *not* measured. Nothing is sent anywhere.

The question **chooses** the facts attached — by plain **English** keywords, not by
understanding: `never` `dead` `unused` for the dead classes, `time` `slow` `hot` for time,
`cover` `coverage` for coverage, `source` `missing` `root` for sources, `run` `when` for
runs. A keyword **opens a word**: "screenshot" does not trigger `hot`. French words work
too. The question then **travels** inside the pack. What was picked up is announced on
**standard error**; `--help` gives the table of recognised words. For a reproducible result,
name the families instead of having them deduced:

```sh
java -jar runtime-xray.jar --out <the-folder> --context "…" \
     --families class.never_executed,method.hot
```

If the jar is out of reach, read `faits.jsonl` directly: **its first line holds its own
dictionary**, there is no documentation to go and find.

```sh
head -1 <report-dir>/faits.jsonl | python3 -m json.tool
grep '"fact":"class.never_executed"' <report-dir>/faits.jsonl
```

## The vocabulary

| Value of `fact` | What the line says |
|---|---|
| `campaign` | the header: tool, version, date, and the dictionary itself |
| `run` | one observed run: identity, command, machine |
| `unavailable` | a measurement that was **not taken**, and why |
| `caveat` | a measurement that was taken, but whose reach the tool itself limits |
| `coverage.run` | instructions covered out of the total, for one run |
| `class` | a class, its best coverage, and the runs that covered it |
| `class.never_executed` | an analysed class that **no** run ever reached |
| `method.hot` | a method among the costliest, in stack samples |
| `source.missing` | coverage known, source code not — root not configured |
| `source.hint` | a root to add, with what it would resolve |

Every measurement carries its unit (`"mesure"`). A measurement without a unit does not exist
in this file; if one seems to be there, something else is being read.

## Three reading traps — hold them before answering

**1. A zero does not mean "did not run".** It may mean "was not measured". **Read the
`indisponibilite` entries BEFORE any figure**, without exception. The most common case:
under Windows there is no time measurement at all, and every time is zero there.

**2. A source that cannot be found is never "the code does not exist".** It is a source root
that is not configured. The answer is a `SOURCE_DIRS=` line to add, not a finding about the
code.

**3. A coverage rate says where the code went.** It says nothing about whether it is
correct, nor about the quality of the tests. Never present it as a grade.

## How to answer

- **Quote the names exactly** as they appear — classes, methods, runs. An approximate name
  sends the reader to a search that finds nothing.
- **Say what cannot be concluded**, and why, rather than assuming. A confident answer drawn
  from an unmeasured zero costs more than no answer at all.
- **Never present a subset as a total.** If the extract announces facts left out, say so:
  neither a total, nor a ranking.
- **A class never executed is not dead code.** It is code that *none of the observed runs*
  reached. Naming those runs (`runsAnalysed`) is the difference between a finding and an
  accusation.

## When the report does not show what was expected

Open `diagnostic.json`, and look in this order:

1. `sources` — which roots were really opened, and how many files indexed.
2. `rapprochement` — class by class: what the coverage was looking for, what the index found.
3. `executions` — the launch's real configuration, which is not always the one believed to
   have been passed.

Then **work out which of the four restrictions is at fault** — they do not restrict the same
thing, and widening the wrong one changes nothing:

| Symptom | The only option that commands it |
|---|---|
| no code displayed | `--sources` |
| no values captured | `--root` |
| no time on application code | `--filter` |
| no coverage on a class | `--cover` |

## What one does not do

- **Do not guess a source root** from a project convention. On the machine where the
  application runs far from its code, the convention names nothing — or worse, names another
  project's sources. `source.hint` offers *verified* roots, each with the number of classes
  it would resolve: use those.
- **Do not re-run a campaign to answer a reading question.** Accumulating coverage over a
  subset of runs is computed offline, on data already there. And `--report-only` reassembles
  the page without re-running anything.
- **Do not let captured values leave the machine.** They stay in their block, on disk, and
  the tool never puts them in the facts nor in a context.
