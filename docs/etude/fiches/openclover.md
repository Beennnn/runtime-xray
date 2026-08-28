# OpenClover

> **Status: 📄 on documentation** — not run here.

## What it is
A Java coverage tool, heir to Clover (Atlassian), turned open source.

## Its philosophy
**Linking the coverage to the tests that produce it.** Where JaCoCo answers "was this line
executed?", OpenClover answers "**by which test**?". It keeps the link between each test and
the lines it touched.

## What it can do
Lines and branches ✅ · **coverage per test** ✅ · coverage per method ✅ ·
call tree ❌ · parameter values ❌.

## Its interface
An HTML report with annotated source code, comparable to JaCoCo's, enriched by the per-test
view.

## How one navigates in it
Self-contained web page ✅, like JaCoCo. Historic IDE plugins, of uncertain maintenance
*(to be checked)*.

## Setting up
Instrumentation at **compile time** (and not at run time as with JaCoCo): its plugin has to be
plugged into the build. More intrusive.

## Licence and cost
**Apache 2.0**, **€0**. Offline.

## What one can hope for from it
Interesting only if the question "which test covers what?" becomes central. For the need
described — the coverage of a **run**, not of a test suite — JaCoCo is more direct and better
supported.

## Comparable to

- **[JaCoCo](jacoco.md)** — **The direct competitor**, and the one retained here: instrumentation at run time rather than at compile time, a markedly livelier ecosystem. OpenClover regains the advantage only if the question becomes "which test covers what?".
- **[SonarQube](sonarqube.md)** — The same displayer role for one as for the other.

## Easy / less easy

**What is easy.** Reading the report: same family as JaCoCo, same reflexes.

**What is less so.** **Inserting it into the build** — the instrumentation happens at compile time, so it touches the build process. And judging the project's vitality, less supported than JaCoCo.
