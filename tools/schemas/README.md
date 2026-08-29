# The architecture diagrams

Two diagrams, drawn with the grammar of
[`logo-libres`](https://github.com/Beennnn/logo-libres) — its eight shapes, its six
layer colours, its arrow style and its accent, plus the review checklist that decides
what a diagram is allowed to look like.

| File | What it shows |
|---|---|
| [`docs/assets/architecture.svg`](../../docs/assets/architecture.svg) | The overall architecture: who launches what, who writes what, and where the network appears |
| [`docs/assets/architecture-reference.svg`](../../docs/assets/architecture-reference.svg) | The reference use case: one run, three tools, three answers that only hold together |
| [`docs/assets/architecture.drawio`](../../docs/assets/architecture.drawio) | Both of them, editable — two pages in one file |

## Regenerating

```bash
git clone https://github.com/Beennnn/logo-libres ../logo-libres
python3 tools/schemas/archi.py
python3 tools/schemas/reference.py
python3 tools/schemas/drawio.py
```

`LOGO_LIBRES=/somewhere/else` if the sign set lives elsewhere. Python 3 alone, no package
to install: the scripts read the badges as files and write SVG as text.

**The draw.io styles are not retyped.** `drawio.py` decodes `grammaire.xml` from the sign
set and reads the style string of each shape out of it, so this file cannot drift from
the grammar it claims to follow. The signs are embedded as data URIs: the `.drawio` opens
on a machine that has never heard of `logo-libres`.

## What the checklist changed

The [review checklist](https://github.com/Beennnn/logo-libres/blob/main/docs/checklist-relecture-schema.md)
is ordered by measured impact, and following it moved real things:

- **No arrow crosses another.** The first version had the orchestrator in the middle with
  the flow around it; the return arrow cut through three boxes. A vertical spine removed
  the crossing by construction — which is the checklist's first point, and the one it says
  everybody skips.
- **Every arrow carries its intention and its transport** — "launches the application,
  agents attached" and `JAVA_TOOL_OPTIONS`, not "uses".
- **One level of abstraction per diagram.** That is why there are two: the overall view
  stops at the components, and the reference use case goes down to the root method. Mixing
  them was the tempting single picture, and it would have left the reader unsure which
  altitude they were at.
- **Real names, in monospace.** `runs/`, `~/.runtime-xray`, `jacoco.exec`,
  `RoutePlanner::travelTimeMinutes` — a box that names a category cannot be checked.
- **One accented subject, declared in the legend.** The orchestrator in the first diagram,
  the root method in the second.
