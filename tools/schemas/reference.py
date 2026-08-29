import sys, pathlib; sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from lib import *

W, H = 1420, 930
o = [head(W, H, "Runtime X-Ray — the reference use case",
          "One deterministic run of ~10 s, three tools attached to it, three answers that only hold together.",
          "The reference use case of Runtime X-Ray: one deterministic run of sample-app, its root method "
          "RoutePlanner::travelTimeMinutes, and what each of the three tools answers about it")]

# --------------------------------------------------------------- the run
o.append(box(40, 100, 380, 300, 'zone'))
o.append(text(400, 122, "ONE RUN, MEASURED THREE TIMES OVER", 10.5, FAINT, 600, anchor='end', mono=True))

o.append(box(70, 140, 320, 76, 'service'))
o.append(symbol(f'{LL}/symboles/java.svg', 84, 156, 32))
o.append(text(126, 168, "The analysed application", 12.5, INK, 600))
o.append(text(126, 186, "sample-app.jar", 11.5, COUCHES['api'][1], mono=True))
o.append(text(126, 202, "22 classes · no dependency · no clock, no randomness", 9.5, FAINT))

# the subject of the diagram
o.append(box(70, 236, 320, 84, 'service', accent=True))
o.append(text(84, 260, "The root method", 11.5, ACCENT, 700))
o.append(text(84, 282, "RoutePlanner::travelTimeMinutes", 12.5, ACCENT, mono=True))
o.append(text(84, 302, "named at launch — the only one whose values are captured", 9.5, MUTED))

o.append(text(70, 348, "Deterministic: two runs produce the same trace.", 11, MUTED))
o.append(text(70, 366, "Calibrated to ~10 s: long enough to attach to a live process.", 11, MUTED))
o.append(text(70, 384, "Replayed under Java 21, then Java 25.", 11, MUTED))

# --------------------------------------------------------------- why this program
o.append(text(470, 128, "WHY THIS PROGRAM, AND NOT ANOTHER", 10, FAINT, 600, mono=True))
o.append(text(470, 154, "It is built to tell tools apart. Each trait below is there to make one", 11.5, MUTED))
o.append(text(470, 172, "of them say something the others cannot:", 11.5, MUTED))
TRAITS = [
    ("calls conditioned by the context",  "a branch taken only for a trip by car"),
    ("two paths never taken",             "PlaneSpeed, and the SNOW branch"),
    ("a recursion",                       "so the call tree has real depth"),
    ("a virtual dispatch",                "so the resolved target is not the declared one"),
    ("a blocking operation",              "so wall-clock and processor time diverge"),
]
ty0 = 200
for a, b in TRAITS:
    o.append(f'<circle cx="478" cy="{ty0 - 4}" r="3" fill="{FAINT}"/>')
    o.append(text(492, ty0, a, 11.5, INK, 600))
    o.append(text(492, ty0 + 16, b, 10.5, FAINT))
    ty0 += 40

o.append(text(470, 408, "Without those, two reports would look alike and the comparison", 11.5, MUTED))
o.append(text(470, 426, "would say nothing about the tools.", 11.5, MUTED))

# --------------------------------------------------------------- the three lanes
LANES = [
    (f'{XRAY}/docs/assets/icons/jacoco.svg', "JaCoCo", "javaagent", 'fichiers',
     "jacoco.exec",
     "Which lines were executed",
     ["91.1 % of instructions, 81.8 % of branches",
      "PlaneSpeed: never loaded — so removable",
      "the SNOW branch: never taken"],
     "Says nothing about time, nor about the order of the calls."),
    (f'{XRAY}/docs/assets/icons/async-profiler.svg', "async-profiler", "agentpath", 'infra',
     "profil.collapsed",
     "Where the time goes",
     ["71 % of the samples on business methods",
      "the aggregated call tree, whole run",
      "the share of time under each branch"],
     "Samples: it can never prove that a piece of code did NOT run."),
    (f'{XRAY}/docs/assets/icons/arthas.svg', "Arthas", "attach", 'api',
     "watch-params.txt",
     "Which values went through",
     ["Trip[id=TRIP-56, mode=CAR, weather=SUNNY]",
      "returned 52.008069818… minutes",
      "one call's tree, with its line numbers"],
     "A console, not a report — and one method at a time."),
]

y = 440
for icon, name, how, couche, out_file, question, findings, limit in LANES:
    colour = COUCHES[couche][1]
    o.append(box(70, y, 300, 96, 'service'))
    o.append(symbol(icon, 84, y + 16, 30))
    o.append(text(126, y + 30, name, 13, INK, 600))
    o.append(text(126, y + 48, how, 10.5, FAINT, mono=True))
    o.append(text(84, y + 74, limit, 9.5, MUTED))

    o.append(arrow(f"M370 {y+48} H444", "writes", None, 407, y + 40, anchor='middle'))

    o.append(box(448, y + 14, 210, 64, 'stockage'))
    o.append(text(553, y + 50, out_file, 11.5, colour, anchor='middle', mono=True))
    o.append(text(553, y + 66, "inside runs/", 9.5, FAINT, anchor='middle', mono=True))

    o.append(arrow(f"M658 {y+48} H726", "answers", None, 692, y + 40, anchor='middle'))

    o.append(box(730, y, 620, 96, 'application'))
    o.append(f'<rect x="730" y="{y}" width="5" height="96" fill="{colour}"/>')
    o.append(text(752, y + 26, question, 13, INK, 600))
    for i, f in enumerate(findings):
        mono = '=' in f or '%' in f or f.startswith('Trip')
        o.append(text(752, y + 46 + i * 17, "· " + f, 10.5, MUTED, mono=mono))
    y += 118

# --------------------------------------------------------------- the conclusion
o.append(box(70, 800, 1280, 58, 'zone'))
o.append(text(94, 826, "None of the three answers the other two's question.", 13, INK, 700))
o.append(text(94, 846, "That is the whole finding of the study: the answer is a combination, and what is left to settle "
                       "is which second tool — not which one wins.", 11, MUTED))

# --------------------------------------------------------------- legend
lf, _ = legend_formes(40, 872, [('service', 'service'), ('application', 'application'),
                                ('stockage', 'storage'), ('zone', 'zone')])
o.append(lf)
lc, _ = legend_couleurs(40, 900, ['fichiers', 'infra', 'api'])
o.append(lc)
o.append(f'<rect x="700" y="896" width="26" height="16" rx="0" fill="#FFFFFF" '
         f'stroke="{ACCENT}" stroke-width="2.6"/>')
o.append(text(734, 908, "the subject of the diagram: the root method", 10.5, MUTED))
o.append(text(1380, 908, "figures measured on sample-app under Java 21", 10, FAINT, anchor='end'))

o.append('</svg>\n')
open(XRAY + '/docs/assets/architecture-reference.svg', 'w', encoding='utf-8').write('\n'.join(o))
print('written')
