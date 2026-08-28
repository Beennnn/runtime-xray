# JaCoCo

> **Status: ✅ tested here** — output versioned in [`reports-demo/generated/jacoco/`](../../../reports-demo/generated/jacoco)

## What it is

The reference for Java code coverage. A JVM agent that notes, during the run, which
instructions and which branches were taken, then produces a report.

## Its philosophy

**Suppose nothing, count everything.** JaCoCo does not sample: it instruments the bytecode and
records every pass. A direct and precious consequence: it knows what was **not** executed —
something no sampling profiler can assert.

Corollary: it says nothing about *time*, nor about the order of the calls. It answers "where
did we go?", not "how long did we spend there?".

## What it can do

| Capability | |
|---|---|
| Executed lines | ✅ to the line |
| Branches | ✅ partially covered ones flagged |
| Cyclomatic complexity | ✅ per method |
| Call tree | ❌ |
| Parameter values | ❌ |
| Output formats | HTML, XML, CSV |

## Its interface

A static HTML site, with three levels of navigation.

**Overview** — sortable by column, one spots the weak packages at a glance:

![JaCoCo overview](../../assets/shots/jacoco-index.png)

**Annotated source code** — the level that matters:

![Annotated source](../../assets/shots/jacoco-source-weather.png)

The code itself is coloured: green executed, red never reached, yellow partially covered. The
diamonds in the margin detail the branches. The breadcrumb at the top
(*sample-app › lab.sample.weather › WeatherPenalty.java*) allows going back up.

**A whole class never executed** comes out entirely in red:

![Uncovered class](../../assets/shots/jacoco-source-plane.png)

## How one navigates in it

- **Outside an IDE** — one opens `index.html` in a browser and clicks: package → class →
  source. No server, no account. It is a folder of files: one zips it, one sends it, the
  recipient opens it. **It is the simplest channel for a non-technician**, and it works
  offline.
- **In IntelliJ** — the `.exec` file imports (*Run › Show Coverage Data…*) and the coverage
  shows in the editor's margin, with the IDE's usual navigation. *(To be confirmed on a
  machine: not verified here.)*
- **In GitLab** — native visualisation of the coverage in a merge request's diff, provided the
  JaCoCo XML is converted to the Cobertura format.
- **In GitHub** — no native support; a third-party service is needed (Codecov, Coveralls),
  hence an outbound connection — **incompatible with the offline constraint**.

## Setting up

```bash
./tools/jacoco/collect.sh
```

The script does three things: fetch the agent's jar, launch the application with `-javaagent`,
then generate the report. **No modification of the analysed code.**

> ⚠️ JaCoCo's usual mode is the Maven plugin that measures the coverage **of the tests**. That
> is not the question asked here: we want the coverage of a **real run**. Hence using the agent
> directly.

> ⚠️ `-Djacoco.outputDirectory` is not honoured by the `report` goal — the report lands in
> `target/site/jacoco` whatever one does. The script copies it explicitly.

## Licence and cost

**EPL 2.0**, open source, **€0**. No usage limit, no network call at run time: the jar is
installed once and works offline.

## What one can hope for from it

**Alone**: the complete and definitive answer to "where did the code go", with a navigable
report a non-developer can use. That is already the project's need no. 1.

**Combined**: its XML feeds SonarQube (a more polished web interface, designed for a
non-technical audience) or GitLab's native visualisation. It combines without conflict with a
call-tree profiler, both agents cohabiting on the same JVM.

## Measured here

On `sample-app`, under Java 21: **91.1 % of instructions**, **81.8 % of branches**.
`PlaneSpeed` and the `SNOW` branch do come out as not executed.

## Comparable to

- **[OpenClover](openclover.md)** — The same trade — coverage — but it keeps the link with **the test** that covered each line. Richer on that axis, more intrusive: it instruments at compile time, not at run time. For the coverage of a **run**, JaCoCo is more direct.
- **[SonarQube](sonarqube.md)** — Not a competitor: it **consumes** JaCoCo's XML. It displays it better, with a history — at the price of a server to maintain.
- **[IntelliJ IDEA](intellij.md)** — Another consumer, not another measurer: it displays the `.exec` in the editor's margin.
- **[async-profiler](async-profiler.md)** — **Complementary, never a substitute.** JaCoCo knows what did *not* run; a sampling profiler never will.

## Easy / less easy

**What is easy.** Getting a report: one JVM flag, three scripted commands. **Reading it with no training**: the coloured code is understood without knowing what a branch is.

**What is less so.** Measuring a **run** rather than a test suite — that is the minority use, less documented. Displaying it in GitLab (a Cobertura conversion to do) or GitHub (a third-party service, hence online). And **hiding the non-executed lines inside a file**: impossible natively, only excluding whole classes is.
