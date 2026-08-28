# Reducing the footprint on a large codebase

> The three pieces of information sought have neither the same value nor the same cost. On
> an enterprise application one does not take them all on the first go: one goes down a
> level, measures, and comes back up if the application takes it.

## The order of priority, and why it is that one

| | Information | What it costs | What is lost without it |
|---|---|---|---|
| 1 | **Coverage** — which lines ran | Instrumenting the bytecode at load time, then a counter per branch | Everything. It is the only **exhaustive** information: it sees the brief methods, the ones a profiler misses by construction |
| 2 | **The call tree** — who calls whom | Waking the JVM at every sampling interval | The shape of the program. One knows what ran, not what set off what |
| 3 | **The values** — with which arguments | An interception at every entry and exit of the observed method | The explanation of the differences. It is useful, but only once one knows where to look |

That order is not a preference: it follows what each measurement **replaces**. Without
coverage, neither the profile nor the values say what did *not* run — and dead code is often
what one was after. Without values, coverage and the tree stay readable.

## The three levels

A single setting commands them: `--level`.

```bash
# 1. the cheapest — coverage alone
java -jar runtime-xray.jar --level coverage \
  --java "java -jar app.jar" --cover "com.example.*" --sources src/main/java

# 2. + the call tree, sampled every 10 ms
java -jar runtime-xray.jar --level tree --interval 10 \
  --java "java -jar app.jar" --cover "com.example.*" --filter "com/example/*" \
  --sources src/main/java

# 3. + one method's values (the default)
java -jar runtime-xray.jar --level full \
  --java "java -jar app.jar" --root "com.example.Engine::compute" \
  --cover "com.example.*" --sources src/main/java
```

| Level | JaCoCo | async-profiler | Arthas | What the page shows |
|---|---|---|---|---|
| `coverage` | yes | **no** | **no** | The executed code, line by line, and the dead code |
| `tree` | yes | yes | **no** | The same, plus the call tree and the share of time |
| `full` *(default)* | yes | yes | yes | The same, plus the values received by the root method |

The level chosen is **written into the run's context** and shown in the page. Without that, a
report less full than another would read as a failed measurement rather than as a choice.

## The levers, from the most effective to the finest

### 1. Restrict the instrumentation — `--cover`

This is the main cost centre, and the one most often forgotten. With no instruction, JaCoCo
instruments **every class the JVM loads**: your code, but also the framework, the HTTP
client, the database driver, the rendering engine. On an application that loads fifteen
thousand classes, most of the overhead comes from code nobody intends to read.

```bash
--cover "com.example.*:com.example.shared.*"
```

The format is the JaCoCo agent's: class-name patterns separated by `:`. What does not match
is neither instrumented, nor slowed down, nor counted.

**It is also what makes the coverage rate readable**: the denominator becomes the project's
code, instead of everything the JVM loaded.

### 2. Space out the samples — `--interval`

Sampling wakes the JVM at a fixed interval, a thousand times a second by default. Going to
10 ms divides the number of samples — and the overhead that goes with it — by ten.

```bash
--interval 10     # 100 samples per second instead of 1000
--interval 20     # 50 samples per second
```

What is lost: fineness. A method that consumes only 0.1 % of the time may no longer appear at
all. To understand the shape of a program, that has no consequence; to chase a microsecond,
it does.

### 3. Restrict the profile — `--filter`

Independent of the previous one: `--filter "com/example/*"` tells async-profiler to keep only
the stacks that pass through that package. The cost of waking up stays, but the file produced
and the time to assemble the page collapse.

With no instruction, the filter is derived from the root method's package.

### 4. Give up the values — `--level tree` or `--no-values`

Value capture is the only mechanism that **intercepts** instead of observing: it inserts
itself at the entry and exit of the named method. On a method called millions of times, it is
the most visible cost — and [the caveat shown in the report](lire-le-rapport.md) is a
reminder that it distorts the measurement of that method's time.

Two practical consequences:

- for **accurate times**, measure without the values, then capture them in a second run;
- for a **first contact** with an unknown application, `--level tree` is plenty: one does not
  yet know which method to observe.

### 5. Lower the captured volume — `WATCH_COUNT`, `TRACE_COUNT`

In the configuration file: the number of calls whose values are kept (10 by default) and the
number of invocations whose tree is traced. Lowering them to 3 shortens how long the capture
tool stays attached.

### 6. Do not relaunch the application — `--print-options`

An application slow to start, a service managed by systemd, a container: relaunching it under
the tool costs more than the measurement itself. `--print-options` gives the JVM options to
add to the existing command line; assembling the page is done afterwards with
`--report-only`.

```bash
java -jar runtime-xray.jar --print-options --out measurements --classes app.jar
# … add the options shown to the service, let it run, stop it …
java -jar runtime-xray.jar --report-only --out measurements --sources src/main/java
```

## A procedure, on an application one does not know

1. **Coverage alone, on the project's code.** `--level coverage --cover "com.example.*"`.
   Nothing else. One gets what ran and what never ran, for an overhead a production
   application generally takes without any tuning.
2. **Add the tree, spaced out.** `--level tree --interval 10`. One sees the shape of the
   program and the places where the time is consumed.
3. **Choose a method and observe it.** Only then does `--root` make sense: one now knows
   which one to look at, and why.

If the measurement is still too expensive at level 1, the problem is no longer the settings:
it is the perimeter. Restricting `--cover` to one module, or measuring on a shorter scenario,
are the only two honest answers.

## Caveats

- **The overheads are not quantified here.** They depend on the observed code, the machine
  and the JVM; announcing a percentage taken from elsewhere would be exactly the kind of
  claim [the method](../etude/methode.md) forbids. What is established is the **order** of
  the cost centres, and the fact that each lever acts on the one it names.
- **These pieces of advice have not been replayed on an application of several thousand
  classes** — a limit already stated in [the README](../../README.md#scope-and-limits-of-the-study).
  The levers are the ones the underlying tools document; the effect measured on an industrial
  codebase remains to be observed.

## What to read next

- [Manual](mode-emploi.md) — every setting
- [Taking the result into another tool](exports.md) — perf, cpuprofile, LCOV
- [Reading the report](lire-le-rapport.md) — what the page shows and what it folds away
