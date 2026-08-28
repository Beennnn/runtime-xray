# Comparison table

> Filled in from two distinct sources, never mixed: what was **run on this machine** (and
> whose output is versioned in `reports-demo/`), and what comes from the **public
> documentation** and remains to be verified.
> Criteria and protocol: [the method](methode.md).

## 0. Verification status

| Status | Meaning |
|---|---|
| ✅ **Tested here** | Run on `sample-app`, output versioned |
| ⛔ **Blocked by licence** | Demands a licence or a commercial trial activated by a human |
| 🚧 **Blocked technically** | Free, but not runnable in this environment — reason given |
| 📄 **On documentation** | Not run; sheet established on public sources, to be confirmed |

**Without licence**

| Tool | Status | Proof / reason |
|---|---|---|
| JaCoCo | ✅ Tested here | [`reports-demo/generated/jacoco/html/`](../../reports-demo/generated/jacoco) |
| async-profiler | ✅ Tested here | [`flamegraph.html`](../../reports-demo/generated/async-profiler/flamegraph.html) + [`tree.html`](../../reports-demo/generated/async-profiler/tree.html) |
| JFR (Flight Recorder) | ✅ Tested here | [`recording.jfr`](../../reports-demo/generated/jfr) + `method-timing.txt` |
| JDK Mission Control | 📄 On documentation | Desktop GUI: opening the `.jfr` produced here was not done |
| VisualVM | 📄 On documentation | Desktop GUI, manual attachment |
| IntelliJ IDEA | 📄 On documentation | Verifying = opening the IDE; not automatable here |
| Arthas | ✅ Tested here | [`reports-demo/generated/arthas/`](../../reports-demo/generated/arthas) — installed offline from Maven Central, verified with the HTTP traffic cut |
| JMC Agent | 📄 On documentation | Not published on Maven Central in a directly runnable form |
| BTrace / Byteman | 📄 On documentation | Not run |
| OpenTelemetry Java agent | 📄 On documentation | Demands a collector + a visualisation backend |
| Glowroot | 📄 On documentation | Demands an agent + its embedded server |
| Kieker | 📄 On documentation | Not run |
| OpenClover | 📄 On documentation | Not run |

**With licence** — prices and steps: [the evaluation keys](cles-evaluation.md)

| Tool | Status | Proof / reason |
|---|---|---|
| JProfiler | ⛔ Blocked by licence | 10-day trial to be activated manually |
| YourKit | ⛔ Blocked by licence | 15-day trial to be activated manually |
| XRebel (Perforce) | ⛔ Blocked by licence | Commercial licence |
| Dynatrace / Datadog / New Relic | ⛔ Blocked by licence | SaaS, account + agent |

## 1. Functional coverage — the three kinds of data sought

The three columns take up the request word for word: executed lines, call tree, parameter
values.

| Tool | Executed lines | Call tree | Parameter values |
|---|---|---|---|
| **JaCoCo** ✅ | ✅ **yes**, line by line + branches | ❌ no | ❌ no |
| **async-profiler** ✅ | ❌ no (it samples, proves nothing about what did not run) | ✅ **yes**, complete and interactive | ❌ no |
| **JFR** ✅ | ⚠️ partial — sampled stacks, not a coverage | ✅ yes (stacks + targeted `jdk.MethodTrace`) | ❌ no — the `(List, int)` signature is displayed, **not the values** |
| **JMC** 📄 | ⚠️ same as JFR | ✅ yes, graphical view of the `.jfr` | ❌ no |
| **VisualVM** 📄 | ❌ no | ✅ yes (sampler / instrumentation) | ❌ no |
| **Arthas** ✅ | ❌ no | ✅ **yes, per call, with the line numbers** | ✅ **yes** — the values, not the types |
| **BTrace** 📄 | ❌ no | ✅ yes | ✅ yes (instrumentation scripts) |
| **Byteman** 📄 | ❌ no | ⚠️ indirect | ✅ yes (injection rules) |
| **JMC Agent** 📄 | ❌ no | ✅ through JFR | ✅ yes — declarative capture (XML) of parameters and return values into JFR events |
| **OpenTelemetry** 📄 | ❌ no | ✅ yes (nested spans) | ⚠️ only what one puts in a span attribute |
| **Glowroot** 📄 | ❌ no | ✅ yes (traces per transaction) | ⚠️ limited, configurable |
| **Kieker** 📄 | ⚠️ execution traces | ✅ yes — **designed to reconstruct the architecture** from the traces | ✅ yes (parameterisable probes) |
| **OpenClover** 📄 | ✅ yes, + coverage **per test** | ❌ no | ❌ no |
| **JProfiler** ⛔ | ❌ no | ✅ yes, very rich | ✅ yes — *method splitting by parameter values* |
| **YourKit** ⛔ | ❌ no | ✅ yes | ✅ yes (*probes*, Open API) |

> **The central finding reads off this table**: *no tool covers the three columns on its own.*
> Line coverage and parameter capture belong to different technical families — one instruments
> all the bytecode permanently, the other grafts probes onto a few targeted methods. **The
> result will necessarily be a combination of at least two tools**, and that is what has to be
> settled, not "which one wins".

## 2. Where the result is looked at — IDE, web page, platform

This is the study's decisive axis: *seeing where the code went and navigating inside it*, for a
developer **as much as** for a non-developer.

| Tool | Self-contained web page (outside an IDE) | In the IDE | In GitHub / GitLab | Desktop GUI |
|---|---|---|---|---|
| **JaCoCo** ✅ | ✅ **yes — annotated source code, clickable, line by line** ([screenshot](../assets/shots/jacoco-source-weather.png)) | ✅ IntelliJ, Eclipse, VS Code | ⚠️ **GitLab yes** (native visualisation in the MR diff, through a converted Cobertura XML); **GitHub not natively** (demands Codecov / Coveralls / a PR comment) | ❌ |
| **async-profiler** ✅ | ✅ **yes — flame graph + self-contained HTML call tree**, zoom and search ([screenshot](../assets/shots/async-tree.png)) | ⚠️ through IntelliJ Ultimate (which embeds it) | ❌ | ❌ |
| **JFR** ✅ | ❌ — binary output + CLI text | ✅ IntelliJ Ultimate opens `.jfr` files | ❌ | ✅ through JMC |
| **JMC** 📄 | ❌ | ❌ | ❌ | ✅ Eclipse RCP application |
| **VisualVM** 📄 | ❌ | ⚠️ an IDE plugin exists | ❌ | ✅ |
| **Arthas** 🚧 | ✅ web console (`http://<host>:8563`) | ❌ | ❌ | ❌ |
| **Glowroot** 📄 | ✅ **yes — complete embedded web interface** | ❌ | ❌ | ❌ |
| **OpenTelemetry** 📄 | ✅ through Jaeger / Grafana Tempo | ❌ | ❌ | ❌ |
| **OpenClover** 📄 | ✅ HTML report with annotated source | ⚠️ historic plugins | ⚠️ like JaCoCo | ❌ |
| **SonarQube** 📄 | ✅ **yes — annotated source + navigation, designed for non-developers**; consumes the JaCoCo XML | ✅ SonarLint | ✅ native decoration of MRs/PRs | ❌ |
| **Codecov / Coveralls** 📄 | ✅ annotated source online | ❌ | ✅ **yes — PR comment + per-line view** | ❌ |
| **JProfiler** ⛔ | ⚠️ HTML/CSV export of the snapshots | ✅ native IntelliJ plugin | ❌ | ✅ |
| **YourKit** ⛔ | ⚠️ multi-format export | ✅ IDE plugin | ❌ | ✅ |

### What this implies for the two audiences

- **Developer** → the IDE wins: IntelliJ displays the coverage directly in the editor's
  margin, and one jumps from one call to another with the usual shortcuts. Shortest path:
  produce a JaCoCo `.exec` and load it into the IDE.
- **Non-developer** → the self-contained web page wins, and **JaCoCo is the only tool tested
  here that produces one for the coverage**: an HTML folder, no server, no account, clickable
  from the overview down to the coloured line. Publishable as it is on GitHub Pages, hence
  shareable by a simple URL.
- **Code review (GitHub/GitLab)** → this is **not** a strong point of raw Java coverage.
  GitLab can do it natively but demands a conversion to the Cobertura format; GitHub demands a
  third-party service. Neither of the two shows a **call tree** — for that, the self-contained
  web page remains the only realistic channel.

## 3. Ease of setting up (criterion no. 1)

| Tool | Mode of integration | What has to be done before having a report | Verdict |
|---|---|---|---|
| **JFR** ✅ | Native JVM option | One `-XX:StartFlightRecording` flag — **nothing to install**, it is in the JDK | ⭐ the simplest |
| **JaCoCo** ✅ | JVM agent (`-javaagent`) | Fetch a jar, launch with the agent, generate the report (3 commands, scripted here) | ⭐ simple |
| **async-profiler** ✅ | Native agent (`-agentpath`) | Nothing to install: the Maven jar embeds the native library | ⭐ simple |
| **VisualVM** 📄 | Attaching to the process | Launch the app, open the tool, click on the process | ⭐ simple, but manual |
| **JProfiler** ⛔ | Agent + GUI | Install, licence, attach — announced as "zero configuration" | to be verified in a trial |
| **Arthas** 🚧 | Attaching to the process | One jar, then interactive commands | simple if the network allows it |
| **OpenTelemetry** 📄 | JVM agent | Agent **+ collector + backend** to deploy | ⚠️ heavy for a one-off diagnosis |
| **Kieker** 📄 | Agent + configuration | Instrumentation to configure, then offline analysis | ⚠️ the heaviest — it is a research framework |

> A reminder: this is criterion no. 1. A tool that demands half a day of configuration loses
> against a JVM flag, even if it shows more.

## 4. Licence and cost (criterion no. 5)

| Tool | Licence | Model | Indicative cost |
|---|---|---|---|
| JaCoCo | EPL 2.0 | open source | €0 |
| async-profiler | Apache 2.0 | open source | €0 |
| JFR | GPLv2 + Classpath Exception (OpenJDK) | included in the JDK | €0 |
| JMC | UPL 1.0 *(to be confirmed)* | open source | €0 |
| VisualVM | GPLv2 + CE *(to be confirmed)* | open source | €0 |
| Arthas | Apache 2.0 | open source | €0 |
| BTrace / Byteman | open source *(exact licences to be confirmed)* | open source | €0 |
| Kieker | Apache 2.0 | open source / academic | €0 |
| OpenClover | Apache 2.0 | open source | €0 |
| Glowroot | Apache 2.0 | open source | €0 |
| OpenTelemetry | Apache 2.0 | open source | €0 + the backend's cost |
| IntelliJ IDEA Community | Apache 2.0 | free | €0 — **but the integrated profiler is reserved to Ultimate** |
| IntelliJ IDEA Ultimate | commercial | subscription | order of magnitude: a few hundred €/year *(to be checked on the JetBrains price list)* |
| JProfiler | commercial | perpetual licence + maintenance | ~$500 / licence, ~$200 academic *(order of magnitude, to be confirmed)* |
| YourKit | commercial | licence | equivalent; **free for open source**, reduced academic rate *(to be confirmed)* |
| XRebel | commercial | subscription | to be priced |
| Dynatrace / Datadog / New Relic | commercial | usage-based SaaS | the most expensive; beside the point for a one-off diagnosis |

> ⚠️ **No price in this table was read from the vendor's pricing page during this session.**
> They come from general knowledge and must be confirmed before any purchasing decision.

## 5. Provisional summary

What is **established by running it** (and not by reading):

1. **For "where the code went", JaCoCo is already the answer.** It produces an HTML site where
   one descends from the overview down to the source coloured line by line, with no IDE, no
   server, no account. On `sample-app`: 91.1 % of instructions and 81.8 % of branches covered,
   with `PlaneSpeed` at 0 % and the `SNOW` branch in red — exactly what a non-technical reader
   must be able to see alone.
2. **For the call tree, async-profiler gives the best free effort-to-result ratio**: a
   self-contained HTML, unfoldable and searchable, obtained with a single JVM flag.
3. **For the parameter values, nothing free could be demonstrated here.** JFR displays the
   signature, not the values — that is verified, not supposed. The free tracks (Arthas, JMC
   Agent, BTrace, Byteman) remain to be tested; it is precisely the ground on which the
   commercial tools claim their value.

What **remains to be settled** — see [the method](methode.md#7-what-remains-to-be-done):

- the JProfiler / YourKit trial on this same `sample-app`, the only way of knowing whether the
  paid route is justified **on the one point where the free one falls short**;
- the most credible free track for capturing parameters (Arthas or JMC Agent);
- publishing the JaCoCo report on GitHub Pages, to validate the "URL shared with a
  non-developer" channel.
