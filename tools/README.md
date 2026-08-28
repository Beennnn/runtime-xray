# Collectors, one folder per tool

> ⚠️ **This is not the tool.** The tool is a jar:
> `java -jar runtime-xray.jar` — see [the manual](../docs/outil/mode-emploi.md).
>
> These scripts are the trace of the study's **manual protocol**: they show how each tool is
> invoked *natively*, with no orchestrator around it. That is what makes the comparison's
> claims verifiable one by one. They are not maintained at the orchestrator's level and do
> not produce the same page.

Each folder contains one tool's **replayable** procedure: a script that produces a real
output in `reports-demo/generated/<tool>/`.

| Folder | What it produces |
|---|---|
| [`jacoco/collect.sh`](jacoco/collect.sh) | The coverage of a run → an HTML site with annotated source code |
| [`jacoco/collect-focused.sh`](jacoco/collect-focused.sh) | The same report **restricted to the classes actually executed** |
| [`async-profiler/collect.sh`](async-profiler/collect.sh) | Flame graph + call tree, two self-contained HTML files |
| [`jfr/collect.sh`](jfr/collect.sh) | A JFR recording + text extracts, adapted to the JDK's version |
| [`java-env.sh`](java-env.sh) | Fixes the Java version used by all the collectors |

## Choosing the Java version

The project evaluates **two platforms**. By default it is Java 21; `JAVA_TARGET` switches:

```bash
./tools/jacoco/collect.sh              # Java 21 (default)
JAVA_TARGET=25 ./tools/jfr/collect.sh  # Java 25
```

Fixing the version explicitly is indispensable: taking "the java on the PATH" would amount to
documenting capabilities the target platform does not have.

## Adding a tool

⚠️ **A tool is not a Maven module.** That is the point that surprises on arrival: these tools
attach to a JVM, they are not declared as a dependency.

1. Create `tools/<tool>/collect.sh`, on the model of an existing one:
   - `source "$REPO_ROOT/tools/java-env.sh"` at the top;
   - launch **`sample-app` with no argument** — it is the reference run, the same for all of
     them, otherwise the comparison is worth nothing;
   - write into `reports-demo/generated/<tool>/`.
2. Write the sheet `docs/etude/fiches/<tool>.md` following the others' layout.
3. Fill in the tables of [the comparison](../docs/etude/comparatif.md) **with the verification
   status** — ✅ tested here only if the script really ran.
4. If the tool demands a licence: put nothing in the repository, read the key from the
   environment — see [the evaluation keys](../docs/etude/cles-evaluation.md).

## What is not versioned

The `.jfr` recordings (binary, ~10 MB for 10 s, unreadable outside JMC, and whose random
content triggers GitHub's secret scanner). The text extracts, for their part, are.
