# The integrated tool — using it on any Java project

> `runtime-xray.jar` produces, from a real run, **a single page** showing all the code that
> ran, its call tree, and the values passed to one method.
> No licence, no connection while it runs.

**[→ Example output](https://beennnn.github.io/runtime-xray/multi/)**

## What is needed

**Java, and nothing else.** The tool is a jar with no dependency at all: no Python, no
shell, no Maven, nothing to install beforehand.

```bash
curl -LO https://github.com/Beennnn/runtime-xray/releases/download/v1.2.2/runtime-xray-cli-1.2.2.jar
```

An already-built jar, to download and run. No clone is needed. Preparing a machine with no
network: [Getting the tool](../../README.md#se-procurer-loutil).

The three observation tools (JaCoCo, async-profiler, Arthas) are looked for **on the machine
first**: the `~/.runtime-xray` cache, a directory named by `--components`, the jar's
neighbourhood, then the local Maven repository — the one that any company `mvn` has already
filled. Downloading from a Maven repository, the publisher's or an internal mirror through
`MAVEN_REPO`, only happens as a last resort, and what comes from it is cached.

**On the second launch, nothing goes out on the network any more.** That is what makes the
tool usable in a disconnected environment. Three ways of getting there, depending on what
one has: fill the cache in advance, put the files beside the jar, or carry an edition that
bundles its components — see [Preparing a machine with no
network](../../README.md#préparer-une-machine-sans-réseau).

When a component really is missing, the error message lists every path tried: on a closed
machine, it is meant to be enough.

## Running it

### In one line

```bash
java -jar runtime-xray.jar \
  --java "java -jar target/my-app.jar" \
  --root "com.example.engine.Calculator::compute" \
  --classes target/my-app.jar \
  --sources src/main/java
```

Four settings, and only one is really required (`--java`). The others are best understood
through their reason for being:

| Setting | What it names | Why it exists |
|---|---|---|
| `--java` | the command to execute, **as it is** | the tool does not launch your application its own way: it launches yours |
| `--root` | the method whose values and call path one wants | capturing the values of *every* method would produce an unmanageable volume; one method is named |
| `--classes` | where to find the analysed bytecode | without it, no coverage — see just below, it is not necessarily `target/classes` |
| `--sources` | the source roots | to display the code annotated line by line rather than method names |

### `--classes`: normally unnecessary

The bytecode to analyse is determined automatically. Three sources are consulted in order,
and **each is checked before being kept** — a guessed but wrong path would be worse than no
guess, since the mistake would only show up in the report:

| Source | What it gives | Cases covered |
|---|---|---|
| The **observed JVM's real arguments**, read from the system | the `-jar` launched, or the **directories** of the `-cp` | `java -jar`, `java -cp`, and any script or launcher that ends up calling `java` |
| The **configured command**, if the previous one is unreadable | the same | a JVM too brief to be observed |
| The **project's convention**, if the directory exists | `target/classes`, `build/classes/java/main`, `out/production/classes`, `bin` | `mvn exec:java`, `./gradlew run` — where the application classpath appears on no command line |

All three cases were checked end to end on the demonstration program, without ever passing
`--classes`.

**Dependency jars are deliberately left out.** A real classpath holds dozens of them;
analysing them all would produce a report where the project's code weighs one per cent of
the total, and where "what ran?" no longer has a readable answer. The code one owns is a
directory of classes, or the jar one launched oneself.

**When to set it anyway** — this is the only case where it stays useful:

```bash
--classes target/classes:libs/core-1.4.jar    # analyse an internal dependency AS WELL
--classes target/my-app-boot.jar              # a "fat" jar: BOOT-INF/classes and the
                                              # jars of BOOT-INF/lib are walked
--classes modules/billing/target/classes      # restrict to one precise module
```

The first case is the most interesting when taking over code: the in-house library delivered
compiled is precisely the one nobody has a mental model of, and nothing requires having its
sources to see that it ran.

### `--hide`: not seeing what is not your code

The JDK is always folded away — nobody opens `java.util.ArrayList` to understand their
application. The same holds for libraries that are not part of it but that one treats as
infrastructure: a logger, an HTTP client, a framework.

```bash
--hide "org.slf4j, ch.qos.logback"
```

Where that boundary lies is not a technical question: it depends on what the team owns and
what it merely puts up with. Hence a setting rather than a hard-coded list.

The hidden packages' time is **not lost**: it is attributed to the application method that
called them, exactly as for the JDK. Hiding moves the information, it does not remove it.

**One can also learn it by reading the report.** In the **Code** tab, every class and every
package carries a **filter** button on hover; the path shown above the source makes each
package level clickable the same way. The list accumulates in the page and **stays** there —
the browser remembers it from one load to the next, there is nothing to save.

The *"Stop measuring it too"* button is a second, optional gesture: it copies the
`HIDDEN_PACKAGES="…"` line to paste into the configuration, so that the next runs no longer
even **measure** that code — shorter collection, lighter report. A static page cannot write
into your configuration file; that is also what lets it be published as it is.

### With a configuration file

There is nothing to copy: **the file is generated if it does not exist**, with the default
values, comments and examples for every key.

```bash
java -jar runtime-xray.jar --config my-project.conf
# ✅ Configuration file generated: my-project.conf
#    Fill in JAVA_CMD at least, then run again.

java -jar runtime-xray.jar --config my-project.conf
```

With no argument at all, the tool looks for `runtime-xray.conf` in the current directory and
creates it if it is missing — so one can start by simply typing
`java -jar runtime-xray.jar`.

## How it hooks onto the application

This is the point everything hangs on: **nothing is imposed on the way Java is launched.**
The command supplied is executed as it is, and the analysis agents are injected through the
**`JAVA_TOOL_OPTIONS`** environment variable, which every JVM reads at start-up.

So it works with:

```bash
--java "java -jar app.jar"
--java "mvn -q exec:java -Dexec.mainClass=com.example.Main"
--java "./gradlew run"
--java "./scripts/start-acceptance.sh"
```

No change to the analysed code, no change to the build.

## The settings

### What one gives it

| Setting | Required | What it is for |
|---|:--:|---|
| `JAVA_CMD` / `--java` | ✅ | The command that launches the application, executed as it is |
| `CLASSES_DIR` / `--classes` | no | The analysed bytecode. **Determined on its own** in the usual cases — see above. One writes it only to analyse something else |
| `ROOT_METHOD` / `--root` | — | `package.Class::method`: the root function, the only one whose argument values will be captured |
| `SOURCE_DIRS` / `--sources` | — | The sources, to display the annotated code. Several roots: separated by `:` |
| `HIDDEN_PACKAGES` / `--hide` | — | Packages to keep quiet like the JDK, e.g. `org.slf4j`. Their time goes back to the caller |
| `CLASS_FILTER` / `--filter` | — | Restricts the time measurements to application code, e.g. `com/example/*`. Derived from `ROOT_METHOD`'s package when absent |
| `OUT_DIR` / `--out` | — | Output directory (default `runtime-xray-out`) |
| `RUN_NAME` / `--name` | — | Readable name for *this* run. Runs accumulate, and the view lets you move from one to the next |

### The collection settings

| Setting | Default | What it is for |
|---|---|---|
| `ATTACH_AFTER` / `--attach-after` | 8 s | Delay before inspecting the values. The application must have started **and** still be working |
| `MAX_SECONDS` / `--max-seconds` | 600 s | Guard rail: beyond it the run is stopped and the reports are produced all the same |
| `WATCH_COUNT` | 10 | How many calls have their values captured |
| `TRACE_COUNT` | 10 | How many invocations have their call path traced. The more there are, the more lines carry the "calls …" annotation |
| `MAVEN_REPO` / `--repo` | Maven Central | Where to fetch the analysis components from, once. On a closed network, put the internal mirror here |
| `COMPONENTS` / `--components` | — | Directory where the components are **already** present. What it holds takes priority over any download. Maven's names (`org.jacoco.agent-0.8.13-runtime.jar`) are accepted, as are the official distributions' (`jacocoagent.jar`) |
| `--no-values` | — | Does not capture the values. The time percentages become exact, since nothing instruments any more |

### What one agrees to pay

Three pieces of information, three costs. On a large codebase one does not take them all on
the first go — **[Reducing the footprint on a large codebase](empreinte.md)** sets out how.

| Setting | Default | What it is for |
|---|---|---|
| `LEVEL` / `--level` | `full` | How far to observe: `coverage` (JaCoCo alone), `tree` (+ sampling), `full` (+ values). The first knob to turn down when the measurement costs too much |
| `COVER_INCLUDES` / `--cover` | everything | Classes JaCoCo instruments, e.g. `com.example.*`. **Without it, every class loaded is instrumented**, dependencies included: it is the main cost centre |
| `SAMPLE_INTERVAL_MS` / `--interval` | 1 ms | Stack sampling interval. At 10 ms, ten times fewer samples |
| `JACOCO_REPORTS` / `--jacoco-reports` | `full` | How much of JaCoCo's own rendering to write per run — see [When the number of files costs](#when-the-number-of-files-costs) |
| `FOLLOW_PORT` / `--follow` | — | Port of the follow page. Without it nothing is served — but `progression.jsonl` is written all the same |
| `EXPORT` / `--export` | — | Rewrites the measurements for other tools: `perf`, `cpuprofile`, `lcov`, `values`, or `all` — see [the exports](exports.md) |

### The modes that measure nothing

| Option | What it does |
|---|---|
| `--print-options` | Shows the agent line to paste into a Java command **one does not control** — a systemd service, a container, an application server. The tool launches nothing: it hands you the text |
| `--report-only` | Reassembles the page from runs already on disk, without running anything again. Useful after annotating a run, or after changing the hidden packages |
| `--serve [port]` | Serves the report (default: 8787) and lets the page **write its annotations** beside the runs, then regenerates it. Several people can annotate at once — see [Annotating the runs](annotations.md) |
| `--follow [port]` | Serves **a page showing the run in progress** (default: 8788, local loopback): activity band, busy cores, output produced, tail of the log. The `progression.jsonl` file, for its part, is **always** written: `tail -f <out>/progression.jsonl` follows the run with no browser and no open port |
| `--context ["question"]` | Writes on standard output **a bounded extract of the report, ready to hand to a language model**: the facts that answer the question, their vocabulary, and at the top what was *not* measured. Sends nothing anywhere. The families picked up are **announced on standard error**; `--help` gives the table of recognised words, **in English** — French works too, undocumented — see [Having a report read by an AI](integration-ia.md) |
| `--families a,b` | Names the fact families to attach **instead of deducing them from the question**. This is the path for scripts: the result no longer depends on the words used. An unknown family stops, with the list of those that exist |
| `--serve-host <host>` | Listening interface (default: `127.0.0.1`). `0.0.0.0` for a shared server |
| `--serve-token [secret]` | Guards the served report with a **shared secret**, asked once then remembered for twelve hours. With no value, a secret is drawn at random and shown. `XRAY_SERVE_TOKEN` does the same without exposing it in `ps`. Without the option, nothing is asked: to be kept for the local loopback or an already filtered network — see [what that secret is worth](annotations.md#what-that-secret-is-worth-and-what-it-is-not) |

These options combine: `--report-only --serve` serves measurements already taken, without
running anything, and that is the mode of a server where results from elsewhere are dropped.

### Choosing the root function

This is the only setting that takes a decision. The root function is **the business entry
point**: the one whose arguments one wants to know, and what it set off. A processing step,
a computation, a command — not a `main`, not an accessor.

Capturing the values of *every* method is not an option: on a busy execution path, the trace
runs to hundreds of megabytes. Hence one root, and one only.

### If the application is too fast

Value inspection attaches to a **live** JVM. If the program ends in two seconds, there is
nothing to observe. Two answers: increase the workload (the number of iterations, the size
of the data set), or lower `ATTACH_AFTER`. Increasing the load is preferable — a profile
over two seconds does not say much anyway.

### While it runs

An analysis lasts as long as the observed application. As long as it is working, an activity
band rewrites itself on a single line:

```
   03:12  ··░▒▓███▓▒░··████▓▒·······    cpu 41.3 s | output 812.0 KB
```

Each square is one second, and its density the number of cores busy during that second: `·`
nothing, `█` several. One reads off it what one wants to know without waiting for the end —
that the program is still working, that it has started waiting on an input or a socket, that
a computation phase has just begun.

That band **measures nothing**. The processor time is the one the system accounts for
anyway, the output size that of a file already written: no option is added to the observed
JVM, nothing extra is sampled. The report is identical whether it shows or not.

It only shows in front of a terminal. Redirected into a file or read by an integration
pipeline, a line rewriting itself every second would be nothing but noise: in that case the
tool keeps quiet, as before. `NO_COLOR` removes the colours without removing the squares —
the density is what carries the information, the colour only doubles it.

## What one gets

```
runtime-xray-out/
├── index.html                   ← the integrated view: opening the folder is enough to find it
├── rapport.md                   ← the same content, readable in a forge (see below)
├── noms.json                    ← annotations for the whole report (optional, exchangeable)
└── runs/
    └── 20260821-004121-acceptance-v2/
        ├── run-context.json     ← identifier, name, command, time, machine…
        ├── config.json          ← annotations for THIS run (optional, takes priority)
        ├── execution.log        ← the application's output
        ├── jacoco/html/         ← the detailed coverage, all the analysed code
        ├── jacoco-focused/html/ ← the same, restricted to the classes that ran
        ├── classes-executees.jar ← the bytecode kept for that second report
        ├── async-profiler/      ← the folded stacks, plus the profile rendered by the tool
        │                          itself (flamegraph.html and its inverse)
        ├── arthas/              ← the captured values and the invocation trace
        └── exports/             ← the same measurements for other tools, if --export
```

Everything the tools wrote stays reachable this way. The page gives the list, grouped by
tool: that is what allows checking the summary when it surprises — and staying usable if it
no longer opens.

### When the number of files costs

The two `jacoco*/html/` directories are the bulk of the count: JaCoCo writes **two files per
class**, and the tool asks it for **two sites per run**. The count therefore grows with the
size of the analysed code, not with the measurement. On a machine where every file open
crosses a stack of filters — antivirus, EDR, DLP — that count is what a campaign pays for:
at write time, at every later walk, and when archiving it to pass it on.

`JACOCO_REPORTS` (or `--jacoco-reports`) decides how much of it is written:

| Value | What is written | What is lost |
|---|---|---|
| `full` *(default)* | both sites | nothing |
| `detailed` | the complete site alone | a framing: the focused report holds **no datum** the complete one lacks |
| `data` | `jacoco.xml` and `jacoco.csv` only | JaCoCo's rendering **per run**; the campaign's (`jacoco-fusion/`) stays |

This setting touches **neither the measurement nor what the page shows**: the coverage
rendered line by line comes from `jacoco.xml`, written in every case. And an absent report is
never silent — the page keeps naming it, greyed out, and clicking gives the command that
produces it.

`runs/` is, moreover, the right candidate for an antivirus exclusion: a single directory,
holding only generated artefacts, of which nothing is executed and everything is
reproducible.

### `rapport.md` — for a forge

Beside the page, the tool writes a **`rapport.md`**: the same content, reduced to what reads
without interaction. It exists because a forge shows a `.html` file as **source code**, not
as a page; Markdown, on the other hand, is rendered natively by GitHub as by GitLab, private
repositories included, with no Pages and no third-party service.

## Several runs in one report

Each launch creates a run under `runs/`, and the view shows a **selector** at the top: one
moves from one to the next without changing page. In the list of methods, a marker shows
those that **also** ran elsewhere (`+2`) and those that are **specific to this run** (`only
here`) — that is what makes the comparison readable.

**[→ Example with three runs](https://beennnn.github.io/runtime-xray/multi/)**

### Naming, describing, labelling, pruning

A run carries three identities that must not be confused — the **identifier**, which never
changes; the **name given at launch** by `--name`, which says what one thought one was
measuring; the **name put on afterwards**, which says what one understood. Failing a name,
the identifier names the run: nothing is invented.

To that are added a free description, key/value labels, and the pruning of the call tree —
cutting a branch, or starting again from a node. All of it is typed into the **identity
band** placed under the run's name, and written to a file beside the measurements. The
gestures — save, export, import — live in the top bar: the report is read, it is not filled
in.

Three ways of keeping these annotations alive, which coexist:

| Mode | How | What it gives |
|---|---|---|
| **In the browser** | open the page | immediate; **export** hands over the file, **import** takes in a colleague's |
| **On your own machine** | `--serve` | the page writes beside the runs and the report is regenerated |
| **Shared server** | `--serve --serve-host 0.0.0.0` | results are dropped there, everyone reads and **annotates in parallel** |
| **… closed by a secret** | `--serve-token` as well | an entry page, a twelve-hour session; it complements network filtering, it does not replace it |

```bash
java -jar runtime-xray.jar --report-only --out runtime-xray-out --serve
```

A run's annotation is written in **its own directory** (`config.json`), which makes it travel
with the measurement; a `<run>-config.json` placed beside it, or the shared `noms.json`, are
still read — in that order of priority.

**All the detail — the three modes, concurrent writing, the file locations, the formats and
the caveats: [Annotating the runs](annotations.md).**

### Gathering runs from elsewhere

The view accepts **any tree**: it treats as a run any directory holding a
`run-context.json`. Gather output directories produced on different machines into one
folder, then:

```bash
java -jar runtime-xray.jar --report-only --out shared-folder --sources src/main/java
```

### Reading the view

Two tabs on the left lead to the same code: **Code** (by package and by class) and **Runs**
(the call tree, with the runs as roots). The checkboxes choose which runs are shown.

What the page shows exactly, what it folds away and why, how to filter a package in one
click and how to unfold a loop iteration by iteration: **[Reading the
report](lire-le-rapport.md)**.

**The page opens in English**, and the `EN | FR` selector in the header switches everything
it shows — without reloading anything, without losing anything. A browser that announces
French gets it straight away; every other reads English. The choice is kept for the next
openings. The source code displayed is never translated: it is the observed application's.

### The context is written into the report

Program launched and its arguments, root method, start and end times, duration, machine,
user, system, number of cores, Java version, working directory, tool versions. A report found
again three months later says what it is about.

## Publishing the result

The output is **a set of static files**, and `index.html` is **self-contained**: all its data
is embedded in the file. That is what makes distribution simple.

### In a professional environment

| Medium | How | Remarks |
|---|---|---|
| **GitLab Pages** | A CI job publishes `runtime-xray-out/` as a pages artefact | The cleanest if the forge is a self-hosted GitLab. Free, integrated, offline |
| **GitLab — job artefact** | `artifacts: paths: [runtime-xray-out/]` + `expose_as: "Run report"` | A link appears **directly in the merge request**. Viewable online without downloading |
| **Confluence** | Attach `index.html` to a page, or use the *HTML* macro | ⚠️ The HTML macro is often disabled by administrators. The attachment, on the other hand, always works: the file being self-contained, one click opens it |
| **SharePoint / Teams** | Drop the folder; open `index.html` | SharePoint may force a download depending on the configuration |
| **A network share** | Copy the folder | The simplest, and the only one that really works everywhere |
| **Nexus / Artifactory** | Publish the folder as a *raw* artefact | Often already there, often forgotten for this use |
| **Internal web server** | `python3 -m http.server` or any Apache | Enough for a demonstration |

**Recommendation**: if the forge is a **GitLab**, the job artefact with `expose_as` is the
best option — the report becomes a link in the merge request, with no extra hosting and no
download. Otherwise, the **Confluence attachment** is the safest route, because it depends on
no administration permission.

### One click from GitHub, with no hosting and no download

Four routes, depending on whether the repository is public or private:

| Route | Public repo | Private repo | Cost |
|---|:--:|:--:|---|
| **GitHub Pages** | ✅ one click | ⚠️ requires a paid plan | free in public |
| **raw.githack.com** | ✅ one click | ⚠️ URL with a token, not advised | free |
| **htmlpreview.github.io** | ✅ one click | ❌ | free |
| A workflow artefact | ⚠️ download | ⚠️ download | minutes billed in private |

**On a public repository, GitHub Pages is the answer**: it is free, it is a link, there is
nothing to install. It is what this repository uses.

**Without enabling Pages**, the trick rests on `index.html` being self-contained: services
such as `raw.githack.com` or `htmlpreview.github.io` serve a raw file from GitHub with the
right MIME type, which is enough to display it. One only prefixes the file's URL:

```
https://raw.githack.com/<account>/<repo>/main/runtime-xray-out/index.html
```

⚠️ These are **third-party services**, hence outside the perimeter of a disconnected
environment, and they are not suitable for confidential code. For internal use, GitLab Pages
or a network share remain the right answers.

## Its limits

- **Values are captured on the root method only** — that is a choice about volume, not a
  technical limit.
- **The time percentages are overestimated on that root method**: value inspection steps in
  at every call. The view says so and folds the frames concerned away. Running with
  `--no-values` gives exact times, without the values.
- **Time measurements sample**: a very brief method may not appear in them. The list of
  executed code, on the other hand, is exhaustive — coverage is what counts there.
- **A report holds several runs, but each measurement stands on its own**: the page
  accumulates the coverage of the ticked runs, and `jacoco-fusion/` merges them for JaCoCo's
  own figure — but nothing merges two runs' *time* measurements, which were taken under
  different conditions.
