# Outputs actually produced

This directory holds what the tools **actually wrote** while analysing
[`sample-app`](../sample-app/) — not screenshots, not reconstructed examples.

**→ [Everything is browsable online](https://beennnn.github.io/runtime-xray/)** with nothing to clone.

The runs in [`executions/`](executions/) carry an `exports/` directory as well: the same
measurements rewritten for other tools — `cpuprofile` (speedscope), LCOV (editors, coverage
tracking), and the captured values as JSON. See
[Taking the result up in another tool](../docs/outil/exports.md).

| Directory | Tool | What it shows |
|---|---|---|
| `generated/jacoco/html/` | JaCoCo | Coverage site, **source code coloured line by line**. Entry point: `index.html` |
| `generated/jacoco-focused/html/` | JaCoCo (CLI) | The same, **restricted to the classes that actually ran** — code never reached disappears from the report |
| `generated/async-profiler/` | async-profiler | `tree.html` (unfoldable call tree) and `flamegraph.html` |
| `generated/jfr/` | JFR under Java 21 | Summary and execution samples — **no** method tracing: it does not exist before Java 25 |
| `generated/jfr-jdk25/` | JFR under Java 25 | `method-timing.txt` (16,000,000 invocations counted exactly) and an extract of `MethodTrace` |
| `generated/arthas/` | Arthas | `watch-params.txt`: **the values actually passed** · `trace-calltree.txt`: the tree of **one** call with line numbers |

## How to look at them

```bash
open reports-demo/generated/jacoco-focused/html/index.html
open reports-demo/generated/async-profiler/tree.html
```

In the coverage report, the most telling file is
`lab.sample.weather/WeatherPenalty.java.html`: a red branch inside a method executed
millions of times — the case a per-class percentage hides and a line-by-line report
reveals.

## Regenerating them

```bash
./tools/jacoco/collect.sh && ./tools/jacoco/collect-focused.sh
./tools/async-profiler/collect.sh
./tools/jfr/collect.sh
JAVA_TARGET=25 ./tools/jfr/collect.sh
```

## The contrast to look at

Open `generated/jfr/` then `generated/arthas/watch-params.txt`: JFR shows the signature
`travelTimeMinutes(Trip)` and the whole stack, **but never the value of the `Trip`**.
Arthas, for its part, says `@Mode[CAR] @Weather[SUNNY] @TimeOfDay[RUSH_HOUR]`. That is the
whole difference between knowing *that* a call took place and knowing *which one*.
