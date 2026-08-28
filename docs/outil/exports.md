# Taking the result into another tool

> The page `runtime-xray` produces is **one** way of reading a run, not the only one. The
> same measurements are rewritten into public formats that other tools open: Firefox
> Profiler, speedscope, an editor, a script.

## The need, stated once

A run's measurement is **data**, not a rendering. Whoever takes it must be able to look at it
with the tool they already know, compare it with yesterday's, archive it longer than the
version of the program that produced it, and pass it to someone who does not use ours.

That is exactly the reproach [the comparison](../etude/comparatif.md) makes to closed tools:
the measurement there is a prisoner of the rendering. A free tool that did the same thing,
with its own page as its only output, would have solved nothing.

Hence the rule held here:

> **Everything measured must be able to come out in an open, documented format, readable by
> at least one tool we do not write.**

Two practical consequences:

- **The measurement is exported, not the summary.** The page's foldings — the JDK tucked
  under its caller, the hidden packages, the aggregations — do not apply to the exported
  files. What a third party opens is what the observation tool wrote, in another grammar.
- **No format invented where one exists.** An in-house format is justified only where nothing
  established covers the information — that is the case for argument values, and that case
  only.

## Producing the exports

```bash
java -jar runtime-xray.jar --report-only --out runtime-xray-out --export all
```

`--export` accepts a list (`--export cpuprofile,lcov`) or `all`. The option works during a
measurement as well as afterwards, with `--report-only`: the exports are recomputed from the
files already written, without running the application again.

The files are dropped inside each run:

```
runs/20260821-004121-acceptance/
└── exports/
    ├── profil.perf.txt      ← profile, perf script format
    ├── profil.cpuprofile    ← profile, Chrome DevTools format
    ├── couverture.lcov      ← coverage, LCOV format
    └── valeurs.json         ← captured values
```

The page names them in two places, which show the same list: the **Exports** menu at the top
left, under *"Open formats"*, and the list of raw outputs at the bottom of the overview. Each
entry carries the sentence saying what it holds and what opens it — `perf`, `lcov`,
`cpuprofile` lined up without explanation said nothing to whoever did not already know them.

Those four stay **named even when absent**, greyed out, and a click then gives the command
that produces them: a capability that lived only in a command-line option does not exist for
whoever reads the page.

## The formats chosen

| File | Format | What it holds | What opens it |
|---|---|---|---|
| `profil.perf.txt` | Text output of `perf script` (Linux) | The sampled stacks, one block per sample | Firefox Profiler, `perf report`, the FlameGraph family's converters |
| `profil.cpuprofile` | Chrome DevTools CPU profile | The same stacks, shared in a tree | speedscope, the browsers' developer tools |
| `couverture.lcov` | LCOV | Lines, branches and methods covered | `genhtml`, Coverage Gutters (VS Code), most tracking services |
| `valeurs.json` | JSON described below | The observed calls, their arguments and their returns | a script — see the structure below |

### What one does with them

**Firefox Profiler** — open [profiler.firefox.com](https://profiler.firefox.com), the *Load a
profile from file* button, choose `profil.perf.txt`. It is the documented import format for
Linux profiles.

**speedscope** — open [speedscope.app](https://www.speedscope.app) and drop
`profil.cpuprofile` on it. The `async-profiler/profil.collapsed` file, written by the
measurement itself, opens there just as well: it is the folded-stacks format.

**Coverage in the editor** — under VS Code, the *Coverage Gutters* extension reads
`couverture.lcov` and colours the lines in the editor. On the command line:

```bash
genhtml couverture.lcov -o coverage-html    # lcov's HTML report
lcov --summary couverture.lcov              # just the totals
```

**Comparing two runs by script** — `valeurs.json` carries, per observed method, the list of
calls with their arguments and their return:

```json
{
  "valeurs": {
    "lab/sample/RoutePlanner.legMinutes": [
      {
        "ts": "2026-08-21 00:41:34.018",
        "cost": "0.023709ms",
        "params": [
          { "type": "Leg", "value": "Leg[from=Albi, to=Montauban, distanceKm=57.0]" },
          { "type": "Mode", "value": "WALK" }
        ],
        "retour": { "type": "Double", "value": "717.54336" }
      }
    ]
  },
  "trace": { "lab/sample/RoutePlanner.legMinutes": [] }
}
```

One entry per observed call: the timestamp, the cost measured by the capture tool, the
arguments received in signature order, and the return. `trace` carries, for the same methods,
the call trees recorded invocation by invocation.

## The known open formats, and why these ones

The survey below follows the method of the rest of the study: what was chosen, what was not,
and the reason.

| Format | What it covers | Decision |
|---|---|---|
| **`perf script`** (text) | Sampled stacks | **Chosen.** Firefox Profiler's import format, readable with the naked eye, no dependency |
| **Chrome `.cpuprofile`** | Sampled stacks | **Chosen.** Compact — a one-minute profile fits in a few hundred KB where the text runs to tens of MB |
| **Folded stacks** (`.collapsed`) | Sampled stacks | **Already produced** by async-profiler, left as it is. It is speedscope's and FlameGraph's input |
| **LCOV** | Coverage | **Chosen.** The most widely accepted coverage format |
| **JaCoCo XML / CSV** | Coverage | **Already produced** by JaCoCo, left as it is |
| **Gecko format** (Firefox JSON) | A complete profile, with threads and markers | **Rejected.** Firefox Profiler's native format, richer than what we measure — the `perf` import covers the need without writing it |
| **`pprof`** (protobuf, Google) | Profiles of every kind | **Rejected.** Would need a protobuf dependency, against the no-dependency jar rule |
| **Chrome Trace Event** (JSON) | Traces of timestamped events | **Rejected.** It describes start/end intervals; our measurements are samples. Pouring them in would suppose durations we did not measure |
| **JFR** (`.jfr`) | JVM events | **Rejected here.** A JVM recording format, not a result-exchange one — and [its sheet](../etude/fiches/jfr-jmc.md) explains why it does not answer the three questions |
| **OTLP / OpenTelemetry** | Distributed traces | **Rejected.** Designed for a system in production, not for a local run — see [its sheet](../etude/fiches/opentelemetry.md) |
| **Cobertura XML** | Coverage | **Rejected.** LCOV covers the same uses and many more tools. To reconsider if a forge demands it |
| **SonarQube generic coverage** | Coverage | **Rejected.** Specific to one product; SonarQube already reads JaCoCo's XML |

## Caveats

The points below are known limits, not defects to be discovered.

- **The `perf` format is bulky, and it is thinned out beyond 32 MB.** It rewrites the whole
  stack at every sample: its size therefore depends first on the **depth** of the stacks, not
  on the number of samples — 82,000 samples forty frames deep weigh more than 800,000 samples
  two deep. Beyond the cap, the samples are **thinned proportionally**, and the factor is
  announced on standard output.

  That is exactly what a sampling profiler does when its samples are spaced out: the shape of
  the profile is kept, but **what weighs very little may vanish**. The `.cpuprofile`, for its
  part, is never thinned: it shares the stacks in a tree and fits in a few hundred kilobytes.
  For a fine reading of a large profile, that is the one to take.

  The export no longer **truncates**, which it used to do: stopping part way cut off the end
  of the run, and the profile then lied about the shape of the program — a worse defect than
  a large file.
- **The memory addresses are zero** in the `perf` export. They mean nothing for
  just-in-time-compiled code, and no reader uses them once the symbol is there.
- **LCOV counts 0 or 1 pass per line.** JaCoCo says *whether* a line was executed, never *how
  many times*: the counter cannot say anything else without inventing passes.
- **`valeurs.json` is not an established exchange format.** None exists for this information;
  its structure is described here and follows the one the page reads.
- **Opening them in third-party tools has not been replayed in the environment where these
  exports were written** — network access is closed there. The formats follow their public
  specification and are checked at write time (frame order, coherence of the references, LCOV
  totals) by `ExportsTest`. Status, in the sense of the
  [comparison](../etude/comparatif.md): **conforms to the documentation, import not
  replayed**.

## What to read next

- [Reducing the footprint on a large codebase](empreinte.md) — measure less, but measure all the same
- [Manual](mode-emploi.md) — every setting
- [Formats and decoupling](../resultat/formats.md) — why collection and display are separate
