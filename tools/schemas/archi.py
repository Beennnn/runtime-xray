import sys, pathlib; sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from lib import *

W, H = 1420, 1080
CX = 340

o = [head(W, H, "Runtime X-Ray — overall architecture",
          "The jar observes nothing itself: it launches the application, lets three tools measure, then reads back what they wrote.",
          "Overall architecture of Runtime X-Ray: an orchestrator launches a Java virtual machine observed by three tools, "
          "which write their measurements to disk; the orchestrator reads them back and assembles a self-contained page")]

# --------------------------------------------------------------- the offline zone
o.append(box(40, 96, 1020, 900, 'zone'))
o.append(text(1000, 118, "NO NETWORK ACCESS WHILE IT RUNS", 10.5, FAINT, 600, anchor='end', mono=True))

# --------------------------------------------------------------- 1. the person
o.append(box(CX - 90, 140, 180, 58, 'acteur'))
o.append(symbol(f'{LL}/symboles/utilisateur.svg', CX - 74, 152, 30))
o.append(text(CX + 14, 166, "Acceptance", 12.5, INK, 600, anchor='middle'))
o.append(text(CX + 14, 183, "or taking over code", 10.5, FAINT, anchor='middle'))
o.append(arrow(f"M{CX} 198 V236", "starts a campaign", "command line", CX + 12, 216, anchor='start'))

# --------------------------------------------------------------- 2. the orchestrator
o.append(box(CX - 180, 240, 360, 88, 'service', accent=True))
o.append(symbol(f'{XRAY}/docs/assets/icons/runtime-xray.svg', CX - 160, 262, 40))
o.append(text(CX - 106, 274, "Orchestrator", 14, ACCENT, 700))
o.append(text(CX - 106, 294, "runtime-xray.jar", 12, ACCENT, mono=True))
o.append(text(CX - 160, 318, "instruments nothing, profiles nothing — no dependency outside the Java Development Kit", 10.5, MUTED))
o.append(arrow(f"M{CX} 328 V372", "launches the application, agents attached", "JAVA_TOOL_OPTIONS", CX + 12, 346, anchor='start'))

# --------------------------------------------------------------- 3. the observed JVM
o.append(box(80, 376, 540, 250, 'zone2'))
o.append(text(600, 398, "OBSERVED JAVA VIRTUAL MACHINE", 10.5, FAINT, 600, anchor='end', mono=True))

o.append(box(108, 410, 250, 66, 'service'))
o.append(symbol(f'{LL}/symboles/java.svg', 120, 424, 32))
o.append(text(162, 436, "Analysed application", 12.5, INK, 600))
o.append(text(162, 454, "sample-app.jar", 11.5, LAYERS['api'][1], mono=True))
o.append(text(162, 469, "22 classes, no dependency", 9.5, FAINT))

o.append(text(376, 448, "All three measure the SAME run.", 11, MUTED))
o.append(text(376, 466, "None answers all three questions.", 11, MUTED, 600))

TOOLS = [
    (f'{XRAY}/docs/assets/icons/jacoco.svg',         "JaCoCo",         "agent",    "the executed lines"),
    (f'{XRAY}/docs/assets/icons/async-profiler.svg', "async-profiler", "agent",    "where the time goes"),
    (f'{XRAY}/docs/assets/icons/arthas.svg',         "Arthas",         "attached", "the values passed"),
]
ty = 492
for icon, name, how, what in TOOLS:
    o.append(box(108, ty, 484, 36, 'service'))
    o.append(symbol(icon, 118, ty + 7, 22))
    o.append(text(150, ty + 23, name, 12, INK, 600))
    o.append(text(274, ty + 23, how, 10, FAINT, mono=True))
    o.append(text(348, ty + 23, what, 11.5, MUTED))
    ty += 42

o.append(arrow(f"M{CX} 626 V672", "write their outputs, each in its own format", None, CX + 12, 652, anchor='start'))

# --------------------------------------------------------------- 4. the measurements
o.append(box(CX - 180, 676, 360, 116, 'stockage'))
o.append(symbol(f'{LL}/symboles/volume.svg', CX - 162, 716, 28))
o.append(text(CX + 20, 716, "The measurements", 12.5, INK, 600, anchor='middle'))
o.append(text(CX + 20, 734, "runs/", 12, LAYERS['fichiers'][1], anchor='middle', mono=True))
o.append(text(CX + 20, 752, "jacoco.exec · profil.collapsed · watch-params.txt", 9.5, FAINT, anchor='middle', mono=True))
o.append(text(CX + 20, 772, "the only durable thing: everything else is derived from it", 10, FAINT, anchor='middle'))

o.append(arrow(f"M{CX} 792 V838", "read back and assembled — by the same jar", "runtime-xray --report-only", CX + 12, 812, anchor='start'))

# --------------------------------------------------------------- 5. the report
o.append(box(CX - 180, 842, 360, 116, 'application'))
o.append(symbol(f'{LL}/symboles/couverture.svg', CX - 160, 872, 28))
o.append(text(CX + 20, 872, "The report", 12.5, INK, 600, anchor='middle'))
o.append(text(CX + 20, 892, "index.html · faits.jsonl · diagnostic.json", 11, LAYERS['fichiers'][1], anchor='middle', mono=True))
o.append(text(CX + 20, 912, "one file to open, no external resource,", 10, FAINT, anchor='middle'))
o.append(text(CX + 20, 926, "readable on a machine with nothing installed", 10, FAINT, anchor='middle'))

# --------------------------------------------------------------- the component cache
o.append(box(700, 244, 300, 66, 'stockage'))
o.append(text(850, 278, "Component cache", 11.5, INK, 600, anchor='middle'))
o.append(text(850, 296, "~/.runtime-xray", 11, LAYERS['infra'][1], anchor='middle', mono=True))
o.append(arrow("M700 280 H528", "supplies the three tools", None, 614, 270, anchor='middle'))

# --------------------------------------------------------------- Maven, outside the zone
o.append(box(1120, 250, 250, 70, 'externe'))
o.append(symbol(f'{LL}/symboles/maven.svg', 1136, 266, 32))
o.append(text(1250, 280, "Maven repository", 12, INK, 600, anchor='middle'))
o.append(text(1250, 298, "or an internal mirror", 10.5, FAINT, anchor='middle'))
# The one line that crosses the boundary — and it crosses it once, at install time.
o.append(arrow("M1120 285 H1008", None, None, dashed=True))
o.append(text(1245, 216, "fills the cache, once", 10.5, MUTED, anchor='middle'))
o.append(text(1245, 232, "at install time · HTTPS", 9.5, FAINT, anchor='middle', mono=True))

# --------------------------------------------------------------- what the diagram says
o.append(text(1100, 400, "WHAT THIS DIAGRAM SAYS", 10, FAINT, 600, mono=True))
for i, line in enumerate([
        "Three tools, three questions, and not one of",
        "them answers all three: it is their union that",
        "answers, and the orchestrator only lays it out.",
        "",
        "Everything that is read — the page, the facts,",
        "the diagnostic — is derived from runs/.",
        "Deleting the page loses nothing; deleting",
        "runs/ loses the measurement.",
        "",
        "The network appears once, outside the zone,",
        "and at install time. While the application",
        "runs, nothing leaves the machine.",
]):
    o.append(text(1100, 426 + i * 18, line, 11.5, MUTED))

# --------------------------------------------------------------- legend
lf, _ = legend_formes(40, 1012, [('acteur', 'actor'), ('service', 'service'),
                                 ('application', 'application'), ('stockage', 'storage'),
                                 ('externe', 'external'), ('zone', 'zone')])
o.append(lf)
lc, _ = legend_couleurs(40, 1042, ['api', 'fichiers', 'infra'])
o.append(lc)
o.append(f'<rect x="760" y="1036" width="26" height="16" rx="0" fill="#FFFFFF" '
         f'stroke="{ACCENT}" stroke-width="2.6"/>')
o.append(text(794, 1048, "the subject of the diagram: the orchestrator", 10.5, MUTED))

o.append('</svg>\n')
open(XRAY + '/docs/assets/architecture.svg', 'w', encoding='utf-8').write('\n'.join(o))
print('written')
