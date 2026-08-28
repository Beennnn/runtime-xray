# Method

Aim: to compare heterogeneous tools on a common basis, with no familiarity bias, and **never
presenting a reading of the documentation as a measurement**.

## 1. The reference use case

All the tools analyse **the same run**: the program [`sample-app`](../../sample-app) and its
function
[`RoutePlanner.travelTimeMinutes(Trip)`](../../sample-app/src/main/java/lab/sample/RoutePlanner.java).

The run is:

- **deterministic** — no clock, no randomness: two runs produce the same trace. Without that,
  comparing two reports would amount to comparing two different runs, and the difference
  observed would no longer say anything about the tool;
- **calibrated to ~10 seconds** — the minimum for a tool that attaches to a live process to
  have time to be launched and to record;
- **replayed under Java 21 then Java 25**, the two platforms evaluated.

The program is designed to **tell tools apart**: calls conditioned by the context, two paths
never taken, a recursion, a virtual dispatch, a blocking operation. Detail in [the technical
details](../outil/technique.md).

## 2. The five criteria, in order

The order is not decorative: it settles the cases where two tools are good in different ways.

1. **Ease of setting up** — measured in the number of gestures between "nothing" and "a
   report". A JVM flag beats an XML configuration, even if the second shows more.
2. **Readability of the reports** — measured by answering: *can a non-developer open it alone
   and understand something in it?* A self-contained HTML file beats a desktop application to
   install.
3. **Functional coverage** — the three kinds of data sought, scored separately and **ranked**:
   executed lines and call tree are the objective; the parameter values are a **second-priority
   option**. No average: a tool that does two thirds of the work is not worth 66 %, it is worth
   "a second one is needed". And a tool that excelled on the option while being weak on the
   first two would not be retained.
4. **IDE integration** — IntelliJ first, or the web equivalent: a page of annotated code one
   navigates outside an IDE.
5. **Cost** — decides only between comparable capabilities, and includes the hidden cost (a
   server to maintain, a configuration to write, a licence to activate online).

**Outside the criteria, measured as a bonus**: computation time and RAM. The application
reports them itself, which gives an independent reference for estimating a tool's overhead.

Those figures serve only that. **Optimising the performance of the analysed code is a subject
in its own right, and it comes later**: first understand what the code does — that is this
repository's object — then redesign it, and only then optimise it according to performance
constraints of its own. The measurements reported here prejudge nothing of that step: they
serve to know whether an analysis tool slows the run too much to stay usable, not to judge the
code.

## 3. Three modes of integration — and why it matters

These tools **are not Maven dependencies**. That is the exercise's main structural surprise,
and it conditions the way a tool is added to the repository.

| Mode | Examples | What it implies |
|---|---|---|
| JVM agent | JaCoCo, async-profiler, JProfiler, Glowroot | A flag at launch, nothing to change in the code |
| Native JDK option | JFR | Nothing to install at all |
| Attaching to a live process | VisualVM, JMC, Arthas | The JVM must still be running — hence the calibration to 10 s |
| Build plugin | OpenClover, JaCoCo in test mode | Measures only what the tests cover — another question |

## 4. The verification statuses

Every claim carries its level of proof. It is the most important rule of the document.

| Status | Meaning |
|---|---|
| ✅ **Tested here** | Run on `sample-app`, output versioned in `reports-demo/generated/` |
| ⛔ **Blocked by licence** | Demands a licence or a trial activated by a human |
| 🚧 **Blocked technically** | Free, but not runnable in this environment — the reason is written down |
| 📄 **On documentation** | Not run; sheet established on public sources, to be confirmed |

A figure coming from a third-party site carries **the date it was read**. An unverified figure
is flagged as such rather than presented with assurance.

## 5. The no-licence / with-licence separation

The tools are put in two categories, never mixed in the same ranking. The boundary almost
exactly follows the offline constraint and the budget question: comparing a free tool and a
$549 tool in the same "overall score" column would make the only question that matters
disappear — *what exactly does the money buy?*

## 6. Neutrality

Every claim must be traceable to a source: an output versioned in this repository, or a dated
URL. Unsupported value judgements have no place here — "rich" or "powerful" mean nothing until
one has said *on what criterion*.

When a measurement contradicts an expectation, it is the measurement that wins, and the
difference is written down: several of this repository's findings come from there — JFR
tracing absent under Java 21, the 129 MB of recording for 10 s, the profile at first dominated
by building the test data.

## 7. What remains to be done

See [the results](../resultat/resultats.md#what-remains-to-be-done-to-settle-it) — the human
actions, commercial trials first, are listed there.
