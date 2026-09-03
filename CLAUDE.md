# Taking over this repository

This file is read at the start of every Claude Code session opened on this repository. It
holds what one needs to know before touching the code, and the state of the work in
progress. The [README](README.md) explains the tool; here, we explain the repository.

---

## Building and exercising

```bash
mvn test                            # 257 tests, none of them touches the network
mvn -DskipTests package             # orchestrator/target/runtime-xray.jar   (175 KB)
mvn -Pjacoco  -DskipTests package   # runtime-xray-jacoco.jar               (950 KB)
mvn -Pcomplet -DskipTests package   # runtime-xray-complet.jar               (19 MB)
```

The last two profiles embed the analysis components and lay them down in `~/.runtime-xray`
at first launch: that is what makes the tool usable on a machine with no network. They are
**not** the default build, and that is deliberate — the ordinary jar redistributes nothing,
those two redistribute EPL-2.0 and Apache-2.0. See [THIRD-PARTY.md](THIRD-PARTY.md), which
says which one carries what.

Two end-to-end acceptance recipes live in `bin/`: `acceptance-local.sh` exercises what one
is about to publish, `acceptance-published.sh` exercises the published artefact. They launch
a real observed JVM, which the unit tests never do.

## Publishing a release

Pushing a tag is enough — `.github/workflows/release.yml` does the rest:

```bash
git tag -a v1.3.0 -m "runtime-xray 1.3.0" && git push origin v1.3.0
```

The workflow refuses to publish if the tag does not match the `pom.xml`'s version. So
remember to raise that version **before** tagging: three files, `pom.xml`,
`orchestrator/pom.xml`, `sample-app/pom.xml`.

**The download links switch over by themselves.** The last step of `release.yml` rewrites on
`main`, once the release is created, the download URL, the jar's name and the table's
"Version" row — in `README.md` and `docs/outil/mode-emploi.md`. They designate the
**published** release, so they cannot rewrite themselves beforehand: naming a tag in advance
that does not exist offers a 404 to the first reader. Do not touch them by hand between the
tag and the end of the workflow.

The README's other version numbers speak of something else — the MIT licence of the already
published 1.0.0, the tagging example — and the three expressions are written narrowly so as
not to touch them. Their delimiter is `#` and not `|`: the "Version" row is a Markdown table
row, full of vertical bars, and escaping them in a command delimited by `|` gives a pattern
BSD sed reads wrong — tried, it rewrote the whole file.

**No binary is versioned in this repository.** Git keeps every copy forever; the repository
weighs 3.3 MB, one single complete jar would make it six times bigger, and every following
release would add itself with no way back without rewriting history. The artefacts live
attached to a tag. The only tracked `.jar` file is a 40-byte fake, as a test resource.

## Publishing the home page

Nothing to do: `.github/workflows/page.yml` copies `site/index.html` over to the root of
`gh-pages` as soon as the source changes on `main`. The page is therefore modified **in
`site/`**, never on `gh-pages`.

The workflow touches only `index.html`, and that is deliberate: `gh-pages` also carries
`demo/`, `jfr/` and `multi/` — generated reports that exist nowhere in `main`. Rebuilding
the branch from `site/` would wipe them out.

`workflow_dispatch` republishes on demand, for the case where somebody has fixed something
directly on `gh-pages`. The automatic trigger is narrow — only `site/index.html` sets it off
— because publishing at every commit on `main` would teach nothing and would make noise in
the branch's history.

The mechanism was born of a drift: on 24 August 2026, the page served was a paragraph behind
its source, and had been for long enough that nobody could say since when. Two files that
look as though each exists on its own do not diverge noisily.

## How the tool finds its components

This is the most often touched mechanism, and the one that decides whether the tool starts
on the target machine — which, by hypothesis, has no network. `Toolbox` looks in this order,
and goes out on the network only last:

| Order | Place |
|---|---|
| 1 | `~/.runtime-xray` — the cache the tool fills itself |
| 2 | `--components <dir>`, `COMPOSANTS=`, `RUNTIME_XRAY_COMPOSANTS` |
| 3 | the jar's directory, and its `composants/` subdirectory |
| 4 | `~/.m2/repository` (or `MAVEN_REPO_LOCAL`), in Maven layout |
| 5 | the embedded components, for the editions that carry them |
| 6 | the network — `--repo` / `MAVEN_REPO`, an internal mirror included |

Maven's names are recognised, and so are the official distributions' (`jacocoagent.jar`,
`jacococli.jar`, `arthas-bin.zip`, an already-unpacked Arthas). When a file declares its
version in its manifest, it is checked and the tool stays quiet if it matches. When nothing
succeeds, the message lists every path tried: on a closed machine, that message is what must
suffice to get out of it, and it must stay so.

`bin/offline-kit.sh` assembles the equivalent package to carry, fingerprints included.

## When the report does not show what was expected

This is the tool's most costly failure mode, because it is **silent**: an empty code panel
reads exactly like a panel nobody could fill. Three distinct things can be missing, they are
fixed in three opposite ways, and nothing in the page allowed telling them apart.

- **`diagnostic.json`** is written beside the page at every assembly, without being asked
  for. It carries the environment, the launch's configuration, the roots actually opened,
  the runs and their reports, and the class-by-class matching. It is the file to ask for
  when somebody reports a disappointing report — it saves the round trip of screenshots. It
  contains **no secret**; a test guards that.
- **The "Source analysis" section** of the overview makes it readable: the tree package by
  package, coloured by what each class is missing, and a search that answers "this class,
  did you find it, and where?".
- **The panel in place of the code** says the key looked for, the roots consulted and the
  exact line to add to the configuration.

**The four restrictions do not restrict the same thing**, and that is the most frequent
confusion — one widens a filter that had nothing to do with it, re-runs, and gets the same
report. `--sources` commands only the display of the code; `--root` the capture of values
and the time filter; `--filter` the measurement of time alone; `--cover` JaCoCo's
instrumentation. The table is in the page, written from the symptom and not from the
documentation.

**When sources are missing, the tool looks for them** — and never guesses them. Guessing a
root from a convention would be handy where one does not need it, and wrong where one would:
on the machine where the application runs far from its code, the convention designates
nothing, or worse, designates another project's sources. Wrong code displayed against a
coverage costs more than an empty panel, because it is believed.

`Sources.searchRoots` therefore looks, and counts. It explores the neighbourhood of the
analysed bytecode, that of the roots already configured and the launch directory — never
beyond — and keeps a file only if the **package it declares** produces exactly one missing
key. A namesake from another project does not count; a test guards that. Every proposal
arrives with its figure — "this root would resolve 27 of the 27 classes without source" —
and the `SOURCE_DIRS=` line to copy. When nothing matches, it says so.

**Reassembling does not lose what the measurement knew.** Every run records its source roots
in its `run-context.json`; a `--report-only` with no `--sources` takes them up again. It is
not a guess and that is what makes it acceptable: the run deduces nothing, it reads back what
it wrote itself. What is given on the command line always wins — it is the gesture of
somebody who is there, now, and who is perhaps fixing a path that has moved.

A recorded root that no longer exists is **adopted anyway**, by design: the diagnostic
already knows how to show an absent root rather than make it disappear, and its conclusion
then names the path to correct. Filtering it out would have put the reader back in front of
"no source directory was given" — the sentence that sent them looking for a setting they had
already made, and the real cost of the original defect.

**The source index is built on the declared package**, not on the path relative to the root
given. `SOURCE_DIRS` can therefore designate the whole project, the source directory, or a
package directory: it lands on its feet. That is what broke on 26 August 2026 — a root one
notch too high was enough to shift the whole index, hence "Source unavailable" on the 447
classes of one analysis.

## The activity band during the wait

`Progress` writes a line that rewrites itself while the observed application works. Three
decisions hold it, and they are linked:

- **It measures nothing.** The processor time comes from `ProcessHandle.Info.totalCpuDuration`
  — the system counts it anyway — and the size of `execution.log` from a file already
  written. Nothing is added to the observed JVM. That was the condition set: a comfort
  display that shifted the measurement would make the report it accompanies lie.
- **One square per package would have said more**, and was not retained: the only two ways of
  obtaining the code actually reached along the way are an `-Xlog:class+load` in
  `JAVA_TOOL_OPTIONS` — unknown to a JVM 8, which would then refuse to start, and that is
  precisely the estate targeted — or an `output=tcpserver` on the JaCoCo agent, which moves
  the writing of the `.exec` off the path that works. The cost bore on the capture; the gain,
  on the display.
- **The count is in busy cores**, not in a percentage of the machine. On a thirty-two-core
  server, an application that saturates one of them is working flat out: brought back to
  3 %, it would show as asleep.
- **The first processor reading fixes the origin, it is not a rate.** A rate needs two
  readings, and the first one carries everything consumed before the band existed — the JVM
  starting, the classes loading, the JIT compiler's own threads, all of it on several cores
  at once. Divided by one interval it announced *26.71 busy cores on a four-core machine*,
  which set the scale of the `--follow` curve and flattened every honest reading after it.
  Until the second reading, the output stands witness, exactly as on a platform that
  publishes no processor time. Two tests hold it, including the case of a reading that
  arrives late.

The band, the `--follow` page and the console messages are **in English**, like the rest of
what the user sees. The numbers follow: `41.3 s`, `812.0 KB`, a timestamp in
`yyyy-MM-dd HH:mm:ss`. An option name or a value in French — `--niveau complet`,
`--export tout` — stays accepted for life; it is simply no longer what is displayed.

It stays quiet outside a terminal (`System.console() == null`): in a pipe or in an
integration log, one line per second teaches nothing and drowns the rest.
`RUNTIME_XRAY_PROGRESSION` asks for it anyway — a shell that launches another often puts a
pipe in the middle while an operator is indeed watching a screen at the end; `=0` imposes the
opposite. The characters `· ░ ▒ ▓ █` exist in CP850 as in UTF-8, for the same reason as the
rest of the messages.

## Following a run that has no terminal

This is the band's continuation, and the answer to the case it did not cover: the tool
launched at the bottom of nested scripts, its output in a pipe, and **nobody seeing anything
any more**.

- **`progression.jsonl` is always written**, at the root of `--out` — hence at a path one
  knows without knowing the run's name. One line per second, `tail -f` is enough. The last
  one carries `"event":"end"`: without it, a reader would not know when to stop.
- **`--follow [port]` serves a page** that reads that file back, and nothing else. It shows
  what a sequence of JSON lines shows badly — the *shape* of the run. Local loopback only: it
  displays a command and an application's output.
- **The load computation is in `Progress`, and nowhere else.** `Progress.tick` renders it,
  `Follow` copies it. Two separate computations would end up diverging, and the file would
  contradict the band with nothing to signal it.
- **A failure to write the feed never carries off the measurement** — it is a comfort, the
  measurement is what one came for; a test guards that, as it guards that the served tail of
  the log is bounded to 64 KB.

## Cumulative coverage

An acceptance campaign is ten runs, and the question asked in front of the code is "did
**something** cover this line?". Two mechanisms answer it, and they are complementary:

- **In the page**, ticking "accumulate" unites the **ticked** runs and keeps, for each line,
  *which one* covered it, as pips. That works on any subset, offline, with nothing to re-run
  — because the union is computed on data already there.
- **`jacoco-fusion/`** carries the JaCoCo report of all the runs merged (`jacococli merge`
  then `report`), for the figure that is authoritative and that one hands on. It loses the
  attribution, on the other hand: once the `.exec` files are added together, nothing says any
  more who covered what. It is produced only from two runs upwards, and only if the bytecode
  is known.

JaCoCo cannot choose its runs at display time: its report is a static rendering, computed on
the `.exec` files it is given. An arbitrary subset would therefore demand one report per
combination — which is precisely what accumulating in the page avoids.

## What we give an AI to read

`faits.jsonl` is written beside the page, one JSON object per line, each of them
understandable on its own — its vocabulary is on its first line, because documentation laid
down beside it gets lost in the first zip. `--context "a question"` draws a bounded, ready-to-
paste extract from it. Three decisions hold it, each guarded by a test in `ContextTest`:

- **What was NOT measured comes before the figures**, and is never pruned by the budget. A
  reader who sees the figures first has already interpreted them by the time they reach the
  caveats; a model will in addition produce a confident answer about a zero that measured
  nothing.
- **Nothing is cut silently** — neither by the budget, nor when a family is empty. An empty
  block reads exactly like a block nobody could fill, here as in the page.
- **The question has two roles**, and that is what makes it ambiguous: it chooses the facts,
  coarsely, and it travels in the package. Free text lets one believe in an understanding
  that does not exist — hence the announcement on **stderr** of what was retained, the table
  of keywords in `--help`, and `--families` for scripts, which short-circuits the
  interpretation. An unknown family stops there where an unknown sentence gives the overview:
  the first comes from a script that has a defect, the second from a human who is searching.
- **The facts format is at 2.0, and there is no migrator.** That is not an oversight:
  `faits.jsonl` is *derived* from the measurements in `runs/`, like the page, the diagnostic
  and the blocks. A `--report-only` rewrites it entirely in the current format, with all the
  accumulated fixes along the way — a migrator would only have renamed keys in a file left
  stale in every other respect, and it would have had to be maintained and then remembered
  and deleted. A 1.0 report is therefore *refused* on reading, with the command that
  regenerates it; and when `runs/` has disappeared, the message says so instead of proposing
  a command that would fail. The 1.0 family names stay accepted by `--families`, for life.
- **The lines are copied across as they are.** Reading a JSON back and rewriting it would
  render `41` as `41.0`: the format does not tell an integer from a float, and that detail
  makes one doubt the rest.

`skills/` carries two skills to copy into a project's `.claude/skills/` — running a campaign,
reading a report — and the offline kit takes them along, because it is on the isolated
machine that one can no longer go and read the documentation. They are two Markdown files,
but they are **held by `SkillsTest`**: every option they cite must exist in `Main`'s `switch`,
and the vocabulary table must coincide with `Facts.VOCABULARY`, in both directions. An option
renamed here and left written there fails nowhere — it sends somebody into a wall with
"Unknown option" for all explanation, on the machine where they can check nothing.

**The tool talks to nobody**: no vendor name, no key, no network call. The shape of the
requests changes every six months; the need to give a bounded and exact text does not.
`bin/ask.sh` carries the three envelopes of the moment (`openai`, `anthropic`, `gemini`) side
by side, some thirty lines each — and "openai" there designates the **shape** of the request,
not the vendor. The captured values never go out: they stay in their block, on disk, and two
tests guard that.

## The view's language

The page opens **in English**. A report is sent, attached to a ticket, read by somebody who
did not launch the measurement: nothing says they speak French. The header's `EN | FR`
selector gives French back to whoever wants it, and a browser that announces French gets it
straight away. Every other browser reads English — there is no third language, and there will
not be one. Two letters, no flag: a flag names a country, not a language.

**The template is written in English, and it is the translation that walks the DOM.** The page
runs to five thousand lines and half its text is composed inside concatenations of tags; sowing
keys through it would have demanded touching each of those lines — hence risking an `id`, a
selector or a comparison on every one — for no gain. The dictionary lives in a block apart, at
the end of the file, and nothing else carries a key.

The template was French until August 2026, and the walk translated it into English. Turning it
round costs nothing at render time and buys the thing that matters: **the language the tool
gets read in is the one the file carries.** A dictionary hole used to show as French inside an
English report sent to somebody who does not read French; it now shows as English inside a
French one, to a reader who asked for French and is looking at their own tool. And English
costs no walk at all — asking for it returns the template's own text, untouched.

Three rules hold the mechanism:

- **Only whole, known strings are translated.** No word replacement. A class name, a captured
  value, a line of the observed application's code is never a label of the dictionary. The
  **code cell** is in addition excluded from the walk — and it alone, because the folds and
  the call annotations of the same line are, for their part, the tool's own text. The tree's
  labels are excluded too: a class named `Comment` must not become `Commentaire`.
- **The original English is kept**, not translated back. A round trip over a non-injective
  dictionary would lose text; here it gives the original back to the byte.
- **A `title` rewritten after the fact is watched like an added node.** The page reassigns
  several — the "expand everything" button changes its own at every toggle — and that creates
  no node, hence does not show in `childList`.

**French spacing lives in the walk, not in the template.** French puts a space before `: ; ! ?`
and English does not; the template holds the English form, and the FR pass puts the space back
on the punctuation that ends a word. The eleven fragments that *begin* with a colon — the page
composes them after a `<b>…</b>` — carry that space in the dictionary instead: a rule keyed on
the start of a text node would also have spaced out `? Help`.

**Drifting apart is the failure mode, and it is silent**: somebody rephrases a label in the
template, the dictionary no longer knows it, and the French view displays English with nothing
to signal it. `ViewLanguageTest` checks the whole static body for that reason — every English
sentence it carries must have its translation. The rest of the text is born in JavaScript and
only shows at render time: that check is done at the browser, before pushing.

**The identifiers are English; the keys are not, and that line is the whole difficulty.** The
template's code holds names that are *also* frozen JSON keys — `nom`, `valeur`, `racine`,
`chemin`, `elagage`, `coupes`, `ordre` — read from and written to disk, so a rename must
leave `.nom`, `nom:` and `"nom"` alone and touch only the bare identifier. The page's own
internals have no such constraint: the block loader's members are English (`load`, `store`,
`touch`, `evict`, `pending`, `pinned`, `_queue`), because no file on disk names them —
`XR.bloc` is the single name the generated `vue/*.js` know, and it stays.

**Three traps, each of which shipped a defect.** A script that renames bare identifiers gets
all three wrong unless it is told:

- **The object-literal shorthand.** `{...c, pkg, runs: [i]}` names a property by naming a
  variable. Renaming the variable renames the property (the view came up empty, 29 August);
  *skipping* it leaves a reference to a variable that no longer exists (`noeuds is not
  defined`, the same day). Neither is right: the shorthand must be **expanded** —
  `noeuds: nodesByName` — which keeps the frozen name and points it at the new variable.
- **A ternary's colon is not a key.** `? valeur : undefined` reads as `valeur:` to a regex,
  so the rename skipped it and left a name that had been renamed away. It throws only after
  a successful save through the served page — the one path the acceptance drives with `curl`
  rather than a browser, so nothing saw it for a day.
- **A frozen key inside a string.** Translating the identifiers turned `annotationPatch(R,
  "nom", …)` into `"name"`, while every reader, the exporter and the save payload went on
  asking for `nom`. A name typed in the page went into a field nobody read — and the page
  showed it anyway, because it re-reads the input it just filled. `ViewContractTest` holds
  that line now: a patched field outside `nom, description, etiquettes, elagage` fails the
  build.

**The way to verify a change here is to render it.** Before touching the block, take a snapshot
of every string the page displays — both languages, a dozen states of the view — and take the
same one after: the inversion itself was carried out that way, and so was the rename of the
last eighty French names, which left **0 differences over 7 734 captured strings**. A reading
of the diff proves nothing; the DOM does. And the DOM does not prove everything either — it
never opens the save path, which is why the two defects above needed a test apiece.

## What wins between the file and the command line

**The command line, on every setting — and for a long time that was true of nine out of
twenty-two.** `merge` was a hand-written list of `if`s, and it stopped being complete around
the fifth option added after it. Everything absent from the list was dropped without a word
the moment a `--config` sat on the line beside it: `--level coverage` measured everything,
`--jacoco-reports data` wrote its hundred and eighty files. Nothing failed — the run simply
did something other than what it had been asked, and the only way to notice was to count the
files afterwards. Found on 29 August 2026 while adding a key, not while reading the code.

The rule is now **applied rather than enumerated**: a setting that differs from a fresh
`Config` is one the command line set, and it overrides the file. A list has to be remembered
at every new option; a comparison does not. `SettingsPrecedenceTest` walks `Config`'s fields
by reflection and fails the build on any that does not come through — which is the guard a
longer list could never be.

**`SERVE_HOST` says where to listen, and never that one should.** The interface a machine
exposes is a property of that machine, so it belongs in the configuration; the decision to
serve is a gesture, so it stays on the command line. That split is the whole point: a
configuration file travels — into a repository, a ticket, another machine — and one that
could open a port by travelling would be unreadable safely. `--serve` remains the only thing
that puts the tool into listening, and a test holds it: exactly one `serve = true` in the
options switch.

Past the loopback, the report becomes readable — and annotatable — by whoever reaches the
port, and it carries the argument values captured from a real application. So the default
stays `127.0.0.1`, the key is shipped commented out with its price beside it, the tool warns
at start-up when it listens wider without a secret, and the documentation carries the
deployment that does it properly: the proxy terminates TLS and the tool answers only to it.

## Conventions

- **The tool speaks English, and so does the code now.** The switch happened in stages — the
  help, the examples, the keywords, the facts format, the view, the console messages, then the
  code's names, its comments and its `@DisplayName`s, including those of `sample-app`, whose
  code is displayed in the report — and finally the documentation, the README, the study, the
  tool sheets, the `bin/` and `tools/` scripts, the workflows and the diagrams. **All new code
  is therefore written in English**, comments and `@DisplayName`s included, and so is every
  document.

  One thing stays in French, for a reason: the **commit messages**, which address whoever takes
  over the repository and not whoever runs the tool. The `dashboard.html` template joined the
  rest in August 2026 — French now lives in its dictionary, not in its markup (see "The view's
  language"). Examples are in English **everywhere**, including inside French
  prose: `com.example.app`, `--context "which classes never ran?"`.
- **A French option name stays accepted for ever**, silently, now that its English equivalent
  has arrived. Scripts already deployed never break. Only `faits.jsonl` will break, in format
  2.0, and that is assumed: it is three days old.
- **A produced file keeps one vocabulary, and `diagnostic.json`'s is French.** Its keys —
  `executions`, `rapprochement`, `fichiersSansSource` — predate the switch to English and are
  frozen like every other produced key. New keys there follow them (`empreinte`,
  `fichiersEcrits`, `seuilAlerte`): a document half in one language and half in the other
  reads worse than either, and the choice is between consistency inside one file and
  consistency with the code around it. `faits.jsonl` went the other way and paid for it with
  a format break at 2.0 — worth it there, because that file is *read by others*; the
  diagnostic is read by us.
- **A commit explains why**, not what the diff already shows. The subject is a sentence, not a
  label.
- **A test guards a decision.** The tests here do not check lines but choices: that the Maven
  repository is pointed at a dead address in `ToolboxTest` is not a detail — a test that
  passed by going out on the network would prove nothing.
- **The components' versions have two sources**: the `pom.xml`'s properties, which decide what
  is embedded, and `Toolbox`'s constants, which decide what is looked for.
  `ToolboxTest.thePomVersionsMatchTheCode` compares them — divergent, they would give a jar of
  apparently complete appearance that re-downloaded everything.
- **Line endings**: `.gitattributes` freezes the working copy to LF. On a Windows machine that
  had cloned beforehand, `git add --renormalize .` once. Nothing in the code must depend on
  the line ending — that is what broke twelve tests in August 2026.

## Platform traps

- **No time measurement under Windows**: async-profiler publishes only Linux and macOS
  binaries. The coverage and the values work; the tool says so at launch rather than failing.
- **The CI exercises both systems**: `tests.yml` runs `mvn test` on `ubuntu-latest` AND
  `windows-latest`, with `fail-fast: false` — knowing whether a defect is specific to Windows
  or common to both changes what one has to go and look at. Building the three editions stays
  on a single machine: it exercises the Maven profiles, nothing that depends on the system.
  Added on 26 August 2026, after two Windows defects a Linux CI could not see.
- **A run may never be scheduled**, and then nothing unblocks it: on 26 August 2026, one
  stayed "queued" for seventeen hours without creating a single job, the API refusing right up
  to its cancellation. The pull request was waiting behind a check that would never come.
  `tests.yml`'s `concurrency` makes that the escape hatch: **pushing a commit cancels the
  previous run and takes its place**. It is the only gesture that works — neither a re-run nor
  a cancellation does. And the `timeout-minutes` bound the other form of the same problem: a
  job set to last six hours, GitHub's default. Do not wait on a silent CI more than once: see
  it, say it, and push.
- **The tilde is a shell character only at the start of a word**: Windows's 8.3 short names
  carry one — `C:\Users\RUNNER~1`, `C:\PROGRA~1` — and counting it everywhere sent the command
  to `cmd /c`. It ran, but `ClassSources` no longer read the `-jar` in it, hence no longer the
  bytecode: an empty report, with no explanation. Found by the first pass of the Windows CI,
  the very day it was added.
- **Windows paths in a list**: `SOURCE_DIRS` and `CLASSES_DIR` are separated by `:`, which
  goes without saying on Unix and not at all on Windows — `C:\project\src` begins with a `:`
  that is not a separator. `Config.decouper` gives back to the path the `:` that follows a
  lone letter at the start of a segment and precedes a separator, and accepts `;` as well.
  Without that, a valid absolute path gave two non-existent entries and the tool answered "not
  found" on a path the user had in front of them. Discovered on 26 August 2026, when the
  search for roots started proposing absolute paths.
- **The tests never compare a path to a literal with separators**:
  `endsWith("project/src/main/java")` fails under Windows when the code found exactly what was
  needed. Build the expected path with `resolve` and compare the two.
- **The report generates a great many small files, and that is what costs on a machine laden
  with security agents.** The count grows with the number of classes analysed, not with the
  volume: JaCoCo writes two HTML files per class, the tool makes it produce **two sites** per
  run — the complete one and the focused one — and adds to that a copy of the bytecode of
  every executed class in `classes-executees/`. On a large application, a campaign of several
  runs counts in hundreds of thousands of files, of a few kilobytes each.

  On Linux, opening them takes a few seconds. On a machine where every file open crosses a
  stack of filters, it takes minutes — and **the machine can do nothing about it**: the
  latency is paid serially, one open after another, so neither the memory nor the cores buy it
  back. It is not specific to Git Bash: a native archiver suffers just as much, which rules
  out MSYS2's POSIX translation as the cause. The JVM escapes it because it opens few files
  and keeps its handles.

  **That sentence is about a *walk*, and it must not be read as "concurrency never helps".**
  An archiver, an explorer, a backup agent goes through the files one after another and
  nothing overlaps. When we hold the *producer*, on the other hand, two producers do overlap
  — which is what makes the parallel rendering below worth its extra pass.

  **The cost model was measured rather than assumed**, and the measurement is what settled
  the rest. Injecting a fixed latency on every `open` under the output — an `LD_PRELOAD` that
  changes nothing else — gives a total that tracks **one open per file written**: ~185
  delayed opens for the 178 files of a run's two sites. So the time paid is the file count
  times the filter's latency, and any change is worth exactly what it does to that product.

  **`JACOCO_REPORTS` decides what is written of them**, and nothing else: `full` both sites
  (the default, unchanged), `detailed` the complete one alone, `data` neither, `minimal`
  not even the campaign's merged site — its XML and CSV are still written, so the figure
  survives its rendering. The setting touches neither the measurement nor the display — the
  coverage rendered comes from `jacoco.xml`, written in every case, and `Coverage.parse`
  reads it directly. On the example application, a run goes from 186 to 101 files in
  `detailed`, to 9 in `data`, with the same coverage to the figure.

  Three decisions hold it, and they are the ones to know before touching it:

  - **The default does not move.** No existing report changes shape without being asked to.
    That is what tells this setting apart from a fix.
  - **The net is at campaign level, not at run level.** `data` does not remove the JaCoCo
    rendering: `jacoco-fusion/` stays, and so does `jacoco.xml`. That is what makes the
    aggressive value defensible — one removes a convenience per run, never the last possible
    reading.
  - **An absence is never silent.** The page's JaCoCo group is in `toujours: true`: a report
    not produced stays named, greyed out, and the click gives the command. Without that, the
    setting would have manufactured exactly the failure mode this project fights everywhere —
    a poorer report that does not say it is poorer. Each group carries **its** command: the
    open formats rewrite themselves from measurements already there, the JaCoCo sites demand
    another setting.

  Staging the executed classes is done as **one jar** and not as a tree: `jacococli` accepts
  both and draws an identical report from them — verified, `diff -r` finds nothing — and the
  copy goes through the zip filesystem, so the intermediate files never exist. Writing them in
  order to delete them would have cost exactly what one is trying to avoid.

  **Three things are now done without being asked, because they take nothing away.**

  - **The two sites are rendered at once.** The focused report learns which classes ran from
    `jacoco.xml`, so the data pass has to come first — and it used to carry the complete site
    with it, which left the two renderings strictly one after the other. Split into three
    passes, the data pass writes two files and the two renderings overlap. Measured **on the
    rendering step**, which is the only thing this changes: the two `jacococli` calls alone
    go from 1.69 s to 1.24 s at 5 ms per open (−27 %), 2.62 → 1.73 at 10 ms (−34 %), 4.44 →
    2.68 at 20 ms (−40 %) — and *0.75 against 0.76* with no filter at all, the extra
    start-up being exactly paid by the overlap. On the tool itself, read off the files'
    timestamps, the phase goes **4.26 s → 2.45 s at 20 ms (−42 %)**.

    **Do not quote those percentages of a whole run.** The same run goes 7.84 s → 7.22 s
    (−8 %), because it also pays for the application, the components and the assembly, none
    of which this touches. The gain is on the rendering and grows with the number of
    classes; everything else is fixed. The parallelism is of degree two and stays there:
    each rendering parses the whole class set, so this phase's peak memory doubles.
  - **The focused report is not written when every analysed class ran.** It would then list
    exactly what the complete one lists. The test is strict and made on the coverage alone,
    before staging: a single class never entered, or a hidden package — hidden on our side,
    unknown to the CLI, hence present in the complete site — puts the two out of step and the
    shortcut off.
  - **The count is said.** Past `Footprint.NOTABLE` files the tool prints it at the end of a
    campaign and names the directory to exclude; `diagnostic.json` carries it run by run.
    Counting opens each *directory* and never a file, so it costs the tree's directories and
    not its files — 0.63 s for one walk at 20 ms per open, against some four seconds for a
    single pass over the files that walk describes. Cheap, not free: which is why the
    diagnostic **sums the runs it has already counted** instead of walking them a second
    time, and why the total is taken once, at the very end. Before this, the figure was
    reachable only by counting by hand on the machine where counting is itself slow, which
    is to say never: the setting existed, was documented, and nothing ever pointed at it.

  **`ARCHIVE` gathers `runs/` into one `runs.zip`**, `keep` beside the tree or `replace`
  instead of it. `replace` is the one that restricts — the page's links to the JaCoCo sites
  stop resolving and `--report-only` has nothing left to rebuild from — so it is off by
  default and says so where it is offered. Nothing is removed before the entries written are
  counted against the files walked: this deletes measurements. And writing the archive does
  read every file, so the gesture costs, once, exactly what it stops costing at every later
  walk.

  **A cache of rendered pages across runs is not possible, and the example application hides
  it.** JaCoCo's source page carries the per-line colouring: the same class, with an `exec`
  and without, gives 14 lines `fc` against 14 lines `nc`. But every run of `sample-app`
  covers the same lines, so its 26 source pages come out identical from one run to the next —
  a cache keyed on the code would pass every test here and be wrong exactly in a campaign
  whose runs differ, which is the only reason to run a campaign. What *is* run-independent is
  `jacoco-resources/`: 20 files per site, 22 % of a site on the example, ~2 % on an analysis
  of 447 classes. It shrinks where the problem grows.

  `runs/` remains, moreover, the right candidate for an antivirus exclusion: a single
  directory, containing only generated artefacts, of which nothing is executed and everything
  is reproducible. It is the only measure that **removes** the cost rather than trimming it,
  and it is the first thing the help's section on filtered machines says — that section lists
  every setting that reduces the count **with what it gives up**, and `FileCostTest` holds it
  to that: a value added to `JACOCO_REPORTS` and left out of the section fails the build.
- **Windows terminal**: the tool writes in UTF-8. A terminal in cp850 — the default on many
  machines — renders the accents unreadable. Fix it on the terminal's side (mintty → Options →
  Text → UTF-8), or launch with `-Dstdout.encoding=cp850`. Do not "fix" this in the code: when
  the output goes into a pipe, the JVM cannot know which character set the terminal will use.
