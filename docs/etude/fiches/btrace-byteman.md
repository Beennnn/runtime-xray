# BTrace & Byteman

> **Status: 📄 on documentation** — not run here.

Two different tools, one family: **injecting observation code into a running application,
without recompiling it**.

## BTrace

### What it is
A tracer driven by annotated Java scripts. One writes a small program that says "when this
method is called, print that parameter"; BTrace compiles it and injects it.

### Its philosophy
**A deliberately restricted language.** BTrace scripts can neither allocate, nor loop, nor call
just anything: those restrictions guarantee that a probe cannot break the observed application.
It is a choice of safety before expressiveness — relevant when one instruments something other
than a sandbox.

### What it can do
Parameter values ✅ · call tree ✅ · executed lines ❌ · text or JFR output.

## Byteman

### What it is
An injection-rule engine (a JBoss/Red Hat project). One writes *event–condition–action* rules:
"on entering this method, if this condition holds, then do this".

### Its philosophy
**Changing the behaviour, not only observing it.** Byteman comes from robustness testing: its
first reason for being is to inject failures (throwing an exception, slowing a call).
Observation is a by-product of that — a powerful one, but the tool aims wider than our need.

### What it can do
Parameter values ✅ · behaviour injection ✅ · call tree ⚠️ indirect · executed lines ❌.

## For both

**Interface**: text output, no navigable report. **No channel towards a non-technical reader**
without extra formatting.

**Licence**: open source, **€0** *(exact licences to be confirmed)*. They work offline.

**What one can hope for from them**: the capture of parameters, at the price of a script or a
rule to be written for every question asked. More supple than Arthas, less immediate. To be
considered **if** Arthas and the JMC Agent fail — they are the most demanding of the three in
writing effort, hence the most penalised by criterion no. 1.

## Comparable to

- **[Arthas](arthas.md)** — Answers the same questions, with not a line to write. That is what puts it ahead on criterion no. 1.
- **[JMC Agent](jmc-agent.md)** — The same capability, declaratively, with a standardised output (JFR) instead of a text stream.
- **[JProfiler](jprofiler.md) · [YourKit](yourkit.md)** — The commercial versions of the same idea, with an interface and aggregation on top.

## Easy / less easy

**What is easy.** Expressing a precise question: these tools can capture just about anything.

**What is less so.** **Everything else.** A script or a rule to write per question asked, a text output with no formatting, and for Byteman a tool whose first object is to inject failures, not to observe.
