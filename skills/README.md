# Two skills, to copy into your project

They are two Markdown files. Nothing to install, nothing to compile, no dependency: they
tell an assistant what it needs to know about `runtime-xray` before using it.

```sh
mkdir -p .claude/skills
cp -r skills/runtime-xray skills/runtime-xray-read .claude/skills/
```

They trigger on their own, on the vocabulary of the question or on what the working
directory holds — a `faits.jsonl` is enough to call for the second.

| Skill | What it knows |
|---|---|
| [`runtime-xray`](runtime-xray/SKILL.md) | running a campaign: the command, choosing the root, what each level costs, injecting into a process one does not launch, the machine with no network |
| [`runtime-xray-read`](runtime-xray-read/SKILL.md) | answering from a report already produced: where to start, the vocabulary of the facts, and what one is not allowed to conclude from them |

## Why two, and not one

These are **two moments separated by hours**, and often by two people. Whoever runs an
acceptance campaign is not whoever reads it; whoever reads it generally no longer has a hand
on the observed machine. A single skill would make each of them carry what the other needs,
and — worse — would let a report's reader believe they can re-run the measurement.

## What they carry, and what cannot be guessed

The content is not a summary of the manual. These are the mistakes that cost a whole
campaign, and that no documentation read *afterwards* makes up for:

- **The four restrictions do not restrict the same thing.** `--sources` commands the
  display, `--root` the values, `--filter` the time, `--cover` the coverage. One widens the
  wrong one, re-runs, and gets the same report.
- **A zero may mean "not measured".** Under Windows there is no time measurement at all;
  without that sentence, everything there looks like code that was never called.
- **A source root is not to be guessed.** Wrong code displayed against a coverage costs more
  than an empty panel, because it is believed.
- **`index.html` is not the data.** It is an application; the facts are in `faits.jsonl`,
  and that file carries its own dictionary on its first line.

## For an assistant that has no skills

These files stay readable as they are: hand them over as an attachment, or point at
`skills/runtime-xray-read/SKILL.md`. They depend on no proprietary format — it is Markdown
with a header.

See also [Having the report read by a language model](../docs/outil/integration-ia.md), which covers
the case where it is the *report* that is given to be read, and not the procedure.
