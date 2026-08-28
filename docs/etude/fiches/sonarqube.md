# SonarQube

> **Status: 📄 on documentation** — not run here.

## What it is
A code-quality analysis platform. It is **not** a dynamic analysis tool: it measures nothing at
run time. It figures here for one reason only — it is **the best displayer of the coverage
produced by JaCoCo**.

## Its philosophy
**Making quality readable by those who do not read code.** Dashboards, trends, thresholds,
history: Sonar addresses a manager as much as a developer. That is exactly the audience aimed
at by criterion no. 2.

## What it can do
Displays the coverage ✅ (by consuming the JaCoCo XML) · history and trends ✅ ·
static analysis ✅ · call tree ❌ · parameter values ❌.

## Its interface
A complete web interface: navigation by project, package, file, down to the **annotated source
code**. More polished than JaCoCo's raw HTML, and with the history on top.

## How one navigates in it
Web ✅ · native decoration of GitLab merge requests and GitHub pull requests ✅ ·
SonarLint in the IDE ✅.

## Setting up
⚠️ A **server to install and maintain**. The Community edition self-hosts, hence
**offline-compatible** — unlike SonarCloud, eliminated by the constraint.

## Licence and cost
**Community Edition**: free, self-hosted. Higher editions: commercial.

## What one can hope for from it
A **distribution channel** for the coverage, not an extra source of data. Relevant only if one
wants a history and a permanent portal — otherwise JaCoCo's HTML report suffices and demands
no infrastructure. That is trade-off no. 3.

## Comparable to

- **[JaCoCo](jacoco.md)** — A supplier, not a competitor: Sonar runs nothing, it displays JaCoCo's XML.
- **[Codecov](../outils-ecartes.md) · Coveralls** — The SaaS equivalents, eliminated by the offline-run constraint — whereas SonarQube Community self-hosts.
- **[IntelliJ IDEA](intellij.md)** — The other face of the same data, in the editor rather than in a portal.

## Easy / less easy

**What is easy.** Displaying a coverage already produced: it consumes JaCoCo's XML asking for nothing more.

**What is less so.** **Installing and maintaining a server** for what an HTML folder already does, if one has no need of a history.
