# Third-party code present in this repository

The [repository's licence](LICENSE) — 0BSD, with no condition at all — covers **the code
written here**: the orchestrator (`orchestrator/`), the demonstration program
(`sample-app/`), the scripts (`tools/`, `bin/`), the documentation (`docs/`) and the home
page (`site/`).

It does not cover, and cannot cover, the files below: they belong to their authors and stay
under their original licence.

## Versioned third-party files

These are **tool outputs** kept as supporting evidence for the study. They embed the
resources those tools themselves place in their reports.

| Path | Origin | Licence |
|---|---|---|
| `reports-demo/**/jacoco-resources/`<br>`reports-demo/executions/**/jacoco-resources/` | JaCoCo report resources (`report.css`, `sort.js`, the icon GIFs) | [EPL-2.0](https://www.eclipse.org/legal/epl-2.0/) |
| `**/jacoco-resources/prettify.js`<br>`**/jacoco-resources/prettify.css` | google-code-prettify, redistributed by JaCoCo | Apache-2.0 |
| `reports-demo/executions/**/async-profiler/flamegraph*.html` | async-profiler flame graphs, navigation JS included | Apache-2.0 |

These directories are **regenerable**: they are versioned only so that the claims of the
[comparison](docs/etude/comparatif.md) stay verifiable without replaying the whole chain.

## Tools fetched at run time

The ordinary jar (`runtime-xray.jar`, ~170 KB) redistributes none of what follows: it
downloads it from a Maven repository at first launch, into `~/.runtime-xray`.

| Tool | Licence |
|---|---|
| [JaCoCo](https://www.jacoco.org/) | EPL-2.0 |
| [async-profiler](https://github.com/async-profiler/async-profiler) | Apache-2.0 |
| [Arthas](https://arthas.aliyun.com/) | Apache-2.0 |

### Two editions, for their part, do redistribute them

`runtime-xray-jacoco.jar` (~950 KB, `mvn -Pjacoco package`) and `runtime-xray-complet.jar`
(~19 MB, `mvn -Pcomplet package`) **embed** those tools, in the exact form of their archives
published on Maven Central — no modification, no disassembly, no mixing with our code. They
lay them down in `~/.runtime-xray` at first launch, then take themselves out of the path.

These artefacts exist for one case only: the machine with no network, where a single file to
carry beats a cache to prepare. They are not published on Maven Central.

What applies to them, and does not apply to the ordinary jar:

| Embedded file | In which edition | Origin | Licence |
|---|---|---|---|
| `lab/xray/composants/org.jacoco.agent-*-runtime.jar`<br>`lab/xray/composants/org.jacoco.cli-*-nodeps.jar` | `jacoco` and `complet` | [JaCoCo](https://www.jacoco.org/) | [EPL-2.0](https://www.eclipse.org/legal/epl-2.0/) |
| `lab/xray/composants/async-profiler-*.jar`<br>`lab/xray/composants/jfr-converter-*.jar` | `complet` | [async-profiler](https://github.com/async-profiler/async-profiler) | Apache-2.0 |
| `lab/xray/composants/arthas-packaging-*-bin.zip` | `complet` | [Arthas](https://arthas.aliyun.com/) | Apache-2.0 |

The EPL-2.0 allows this redistribution in binary form and asks that the licence accompany the
files and that the source stay accessible: the archives are intact, they carry their own
notices, and JaCoCo's sources are published by the project itself. This repository's 0BSD
licence neither covers nor modifies these files.

The whole observation capability comes from those three projects. This repository brings only
the assembly of their outputs.
