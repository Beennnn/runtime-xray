# Having the report read by a language model

> A `runtime-xray` report answers questions that take ten minutes to formulate and an hour to
> work out by hand. A language model works them out in a few seconds — provided it is given
> **the right text**, and not the page.

## The need, stated once

This is not an API problem.

It looks like one: a provider must be chosen, a request shape, a library. But that choice
changes every six months and differs from one service to the next, whereas what does not
change is the need underneath: handing over **a bounded, exact text that is understood on its
own**.

Hence the rule held here, twin to that of the [exports](exports.md):

> **The tool produces the context. It speaks to nobody.**

No class of `runtime-xray` knows a provider's name, opens a connection or reads a key. What
it produces is pasted into a chat window, given to an agent that reads the workspace, or
posted by a three-line command. The day the fashionable protocol changes, only those three
lines have to be redone.

## The facts file

A `runtime-xray` report comes with a `faits.jsonl`: **one JSON object per line**, where each
line is understood on its own.

```jsonl
{"fact":"class.never_executed","classe":"com.example.app.Legacy","runsAnalysed":["acceptance-1","acceptance-2"]}
{"fact":"method.hot","methode":"com.example.app.Repository.load","samples":4821,"pct":31.2,"measure":"async-profiler.samples","execution":"acceptance-1"}
```

There is nothing to install to read it: `grep`, `jq`, a three-line loop, or a language model.
Three decisions make it usable, and it loses all its interest as soon as one is missing.

**A line carries all its meaning.** Ten lines taken from the middle of the file are understood
without the preceding ones. No nested structure to reconstitute, no reference to an identifier
defined higher up, and never a measurement without its unit — a figure without a unit forces
the reader to guess, and they will guess.

**The vocabulary travels with the data.** The first line is a `campagne` fact carrying a
dictionary of the fact families and what each one means. Documentation placed beside gets
lost — in a zip, in an attachment, in a copied folder. This one is copied with the file.

**What was NOT measured is written down.** Under Windows, async-profiler publishes no binary:
there is no time measurement. Without a line to say so, "0 samples" reads exactly like "this
code never ran" — and a reader will decide, the wrong way, with confidence. The `unavailable`
and `caveat` facts exist for that. They say what is missing, why, what one therefore **cannot**
conclude from it, and the remedy.

The format is given in the page's manifest (`faitsFormat`), and the HTML report needs none of
this: `faits.jsonl` **adds itself** without removing anything.

## The context pack

Giving a model the whole of `faits.jsonl` works until the file exceeds the window — that is,
from the first serious campaign onwards. Truncating it at the first N kilobytes works even
less well: the caveats are at the top, the measurements at the bottom, and cutting off the
bottom gives a pack with nothing left to say.

`--context` does that work:

```bash
java -jar runtime-xray.jar --out runtime-xray-out --context "which classes never ran?"
```

On standard output, a ready-to-paste Markdown text, carrying, in this order:

1. **what it is about** — an observation of a Java application while it ran;
2. **three reading traps** — including "a zero may mean *not measured*";
3. **the campaign** and **the fact vocabulary**;
4. **what was NOT measured**, before any figure;
5. **the facts kept** for this question, in JSONL;
6. if facts had to be left out, **how many and which**.

Three decisions hold it, and each one is guarded by a test.

**The order is the information.** The unavailabilities come before the measurements because a
reader who sees the figures first has already interpreted them by the time they reach the
caveats. That is true of a human; it is more so of a model, which will produce a confident
answer about a zero that measured nothing.

**An unavailability is never pruned.** The budget (40,000 characters by default, about ten
thousand tokens — enough to fit in the window of a modest model, as self-hosted ones often
are) cuts measurements, never caveats. A wrong, confident answer costs more than no answer at
all.

**Nothing is cut in silence.** When the budget forces something out, the pack says how many,
from which families, and that the selection is INCOMPLETE — hence that neither a total nor a
ranking is to be drawn from it. An extract believed complete makes one conclude on a sample.

### The question has two roles, and that is what makes it ambiguous

It **chooses** the facts — coarsely, by keywords — and it **travels**, copied as it is at the
top of the pack, because the real addressee is not the program.

The choice is deliberately coarse: the selection must never be the clever part — a fine
selection would leave out the fact that contradicts the hypothesis, exactly the one that had
to be kept. But free text suggests an understanding that does not exist, so two things make
up for it:

- **What was understood is announced on standard error** — "families kept from the question:
  …", or "no keyword recognised — overview: …". Standard output stays the pack alone, hence
  redirectable.
- **`--help` gives the table of recognised words.** Undocumented free text is not
  discoverable; this one is, and a test guards the table against rot.

| If the question holds… | Families attached |
|---|---|
| `never` `dead` `unused` `uncovered` `not covered` | `class.never_executed` |
| `cover` `coverage` `percent` | `coverage.run` + `class` |
| `time` `slow` `hot` `cost` `perf` `fast` `profil` | `method.hot` |
| `source` `missing` `root` | `source.missing` + `source.hint` |
| `run` `campaign` `when` `machine` `command` | `run` |

**A keyword opens a word**, it is not looked for anywhere in the sentence: "screenshot"
therefore does not trigger `hot`, nor "generate" `rate`. Inflections are still caught —
`missing` and `manqu` cover "manquant", "manquantes". **French words are recognised too**,
without being documented: the tool speaks English, and a second table would make the first
unreadable for those it addresses.

```
$ runtime-xray --out report --context "welche Klassen liefen nie" > pack.md
   no keyword recognised in the question — overview: execution, coverage…
```

### For a script: naming the families

A script does not need a sentence interpreted, it needs a reproducible result. `--families`
short-circuits the keywords:

```sh
runtime-xray --out report --context "dead classes of the acceptance run" \
             --families class.never_executed,coverage.run
```

The question still travels — that is its other role — but no longer chooses anything. An
unknown family **stops**, with the list of those that exist: that is the opposite of free
text, and deliberately so. A sentence one does not recognise comes from a human searching, so
one gives them the overview; a family that does not exist comes from a script with a defect,
and keeping quiet about it would produce a pack silently different from what it believes it
is reading.

**No captured value leaves.** The argument values recorded by Arthas stay in their block, on
disk. They are bulky, and they sometimes carry what one does not want travelling to a service
hosted elsewhere. Two tests guard this, one on the facts file, one on the context pack.

## Three levels of effort

### Level 0 — copy and paste, or an agent that reads the folder

Nothing to install, nothing to configure, no key.

```bash
java -jar runtime-xray.jar --out runtime-xray-out --context "where does the time go?" | pbcopy
```

And if one already works with an assistant that reads the workspace — in an editor, in a
terminal — there is not even that to do: pointing it at `runtime-xray-out/faits.jsonl` is
enough, the file explains its own vocabulary.

Two skills ready to copy into `.claude/skills/` carry what needs to be known on both sides —
running a campaign, and reading a report without drawing a false conclusion from it:
[`skills/`](../../skills/README.md).

**That is the level to try first**, and it covers most needs. The two that follow serve only
if one wants to ask the question *with no human in the loop*.

#### The case of an assistant built into the editor

Many code-editing assistants hook onto **an address and a model**, in the request shape
described below — it is the most widespread arrangement, and it imposes nothing about who
publishes the model: a self-hosted gateway declares itself there like a commercial service.

For them, there is **nothing to integrate**: they already read the workspace. One only has to
put the report in it and name the file.

```
Read runtime-xray-out/faits.jsonl and tell me which classes never ran.
```

The file carries its own vocabulary on its first line, and the [reading
skill](../../skills/README.md) carries the traps — a zero that may mean "not measured", a
source that cannot be found and is never "the code does not exist". Copying the two skills
into the project's `.claude/skills/` is enough for the assistant to apply them without having
to be reminded at every question.

The `--context` pack stays useful when the report is large: it chooses, bounds, and puts the
absences first — which an assistant reading a file of several megabytes will not do by
itself.

### Level 1 — one command

`bin/ask.sh` takes a question, produces the context through the tool, posts it, and writes the
answer. No dependency: `curl` and `jq`.

```bash
./bin/ask.sh --out runtime-xray-out "which classes never ran?"
./bin/ask.sh --api anthropic --model … "where does the time go?"
./bin/ask.sh --context-only --out runtime-xray-out "…"   # nothing is sent
```

The script is **deliberately thin**, and that is the whole point: the useful work — choosing
the facts, attaching the legend, putting the absences first, bounding the volume — is done by
the tool, which the tests hold. What is left here is posting a text.

**The key is read from the environment**, never from the command line: that line ends up in
the shell history and in the process list, where everyone can read it.

```bash
export XRAY_KEY=…          # or OPENAI_API_KEY / ANTHROPIC_API_KEY / GEMINI_API_KEY
```

#### Why three request shapes and not one

There is no standard. There is a majority shape, which is not the same thing, and a majority
shape taken for a standard is a bet one has forgotten making. The three envelopes are
therefore written side by side in the script — one glance shows that only the wrapping
changes:

| `--api` | Shape | The system instruction | The answer's text |
|---|---|---|---|
| `openai` | `POST …/chat/completions` | a message with role `system` | `.choices[0].message.content` |
| `anthropic` | `POST …/v1/messages` | a separate `system` field | `.content[0].text` |
| `gemini` | `POST …/models/X:generateContent` | `system_instruction` | `.candidates[0].content.parts[0].text` |

**`openai` names the shape of the request, not the vendor.** This is the point most often
misunderstood: most self-hosted gateways and commercial services accept that shape, whatever
the model behind it. An internal gateway is therefore named this way, with nothing in the
arrangement supposing anything about who publishes the model:

```bash
./bin/ask.sh --url https://gateway.internal/v1/chat/completions --model … "…"
```

The day a fourth shape matters, that is thirty lines to write in a `case`, and nothing else to
touch — neither in the tool, nor in the format.

### Level 2 — in an integration pipeline

The context being a text on standard output, it goes wherever one wants: into a ticket, into a
merge-request comment, into a team message.

```bash
java -jar runtime-xray.jar --out "$OUT" --context "which classes never ran?" \
  > context.md
```

Two precautions, and they are of the same order as for any outgoing call:

- **What leaves the machine** is a text of facts — class and method names, coverage and
  sample figures. No source code, no captured values. On a codebase whose class names are
  themselves sensitive, the question to settle remains open, and `--context-only` allows
  looking at it before sending anything at all.
- **An outgoing call is an outgoing call.** On the estate this tool targets — machines with no
  network — level 0 is the only one that works, and it works very well.

## What is not done, and why

**No HTTP client in the tool.** It would need a provider's name, a key, a retry policy, a
quota. Each of those four points is an operational decision, specific to the site, which has
no business in a dependency-free jar whose reason for being is to run on a closed machine.

**No server.** The report is a folder of files; files are already an interface, readable by
anything that can open a file, and one that outlives the version of the program that wrote
them. A server would add a port to open, a life cycle to watch, and one more reason for the
tool not to start.

**No clever selection of the facts.** See above: that would be precisely where one would lose
the fact that contradicts the hypothesis.
