"""The same two diagrams as an editable draw.io file.

Styles come verbatim from logo-libres/drawio/grammaire.xml — decoded from the
library rather than retyped, so the file cannot drift from the grammar.
"""
import sys, pathlib, base64, html, json, re, zlib, urllib.parse
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from lib import LL, XRAY, LAYERS, ACCENT

lib = json.loads(re.search(r'<mxlibrary>(.*)</mxlibrary>',
                           open(f'{LL}/drawio/grammaire.xml', encoding='utf-8').read(), re.S).group(1))
STYLE = {}
for e in lib:
    xml = urllib.parse.unquote(zlib.decompress(base64.b64decode(e['xml']), -15).decode('utf-8'))
    STYLE[e['title']] = re.search(r'style="([^"]*)"', xml).group(1)

# The library's shape titles are French: they are keys into somebody else's file, and
# renaming them there is not ours to do. Naming them once, here, keeps the rest of this
# script English and leaves exactly one place to look when the grammar moves.
ZONE, NESTED_ZONE = STYLE['Zone'], STYLE['Zone imbriquée']
ACTOR, SUBJECT = STYLE['Acteur'], STYLE['Sujet du schéma']
SERVICE, APPLICATION = STYLE['Service'], STYLE['Application']
STORAGE, EXTERNAL = STYLE['Stockage'], STYLE['Externe']
ARROW = STYLE['Flèche annotée']

def img(path):
    raw = open(path, 'rb').read()
    return 'data:image/svg+xml,' + base64.b64encode(raw).decode('ascii')

def esc(s):
    return html.escape(str(s), quote=True)

class Page:
    def __init__(self, name):
        self.name, self.cells, self.n = name, [], 1
    def _id(self):
        self.n += 1
        return f'n{self.n}'
    def node(self, x, y, w, h, label, style, parent='1'):
        i = self._id()
        self.cells.append(
            f'<mxCell id="{i}" value="{esc(label)}" style="{esc(style)}" vertex="1" parent="{parent}">'
            f'<mxGeometry x="{x}" y="{y}" width="{w}" height="{h}" as="geometry"/></mxCell>')
        return i
    def icon(self, x, y, size, path):
        return self.node(x, y, size, size, '',
                         f'shape=image;imageAspect=0;aspect=fixed;html=1;image={img(path)};')
    def edge(self, src, dst, label='', dashed=False, exitp=None, entryp=None):
        st = ARROW
        if dashed:
            st += 'dashed=1;dashPattern=6 5;'
        if exitp:
            st += f'exitX={exitp[0]};exitY={exitp[1]};exitDx=0;exitDy=0;'
        if entryp:
            st += f'entryX={entryp[0]};entryY={entryp[1]};entryDx=0;entryDy=0;'
        i = self._id()
        self.cells.append(
            f'<mxCell id="{i}" value="{esc(label)}" style="{esc(st)}" edge="1" parent="1" '
            f'source="{src}" target="{dst}"><mxGeometry relative="1" as="geometry"/></mxCell>')
        return i
    def label(self, x, y, w, h, text, size=11, colour='#5B6873', bold=False, mono=False, align='left'):
        st = (f'text;html=1;whiteSpace=wrap;fontSize={size};fontColor={colour};'
              f'align={align};verticalAlign=top;'
              + ('fontStyle=1;' if bold else '')
              + ("fontFamily=Courier New;" if mono else ''))
        return self.node(x, y, w, h, text, st)
    def xml(self):
        body = ('<mxGraphModel dx="1400" dy="900" grid="1" gridSize="10" guides="1" tooltips="1" '
                'connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="1600" '
                'pageHeight="1200" math="0" shadow="0"><root>'
                '<mxCell id="0"/><mxCell id="1" parent="0"/>'
                + ''.join(self.cells) + '</root></mxGraphModel>')
        return f'<diagram name="{esc(self.name)}">{body}</diagram>'

# ============================================================ page 1 — overall
p = Page("Overall architecture")

zone = p.node(40, 60, 1000, 860, "NO NETWORK ACCESS WHILE IT RUNS", ZONE)
actor = p.node(280, 100, 240, 60, "Acceptance&#10;or taking over code", ACTOR)
p.icon(292, 110, 36, f'{LL}/symboles/utilisateur.svg')

orch = p.node(220, 220, 360, 90,
              "&lt;b&gt;Orchestrator&lt;/b&gt;&#10;runtime-xray.jar&#10;"
              "instruments nothing, profiles nothing — no dependency outside the Java Development Kit",
              SUBJECT + 'verticalAlign=middle;spacingLeft=54;align=left;')
p.icon(232, 245, 40, f'{XRAY}/docs/assets/icons/runtime-xray.svg')

jvm = p.node(100, 380, 560, 250, "OBSERVED JAVA VIRTUAL MACHINE", NESTED_ZONE)
app = p.node(130, 420, 260, 70, "&lt;b&gt;Analysed application&lt;/b&gt;&#10;sample-app.jar&#10;22 classes, no dependency",
             SERVICE + 'spacingLeft=44;align=left;')
p.icon(140, 435, 34, f'{LL}/symboles/java.svg')
p.label(410, 430, 230, 60, "All three measure the SAME run.&lt;br&gt;&lt;b&gt;None answers all three questions.&lt;/b&gt;")

tools = []
for i, (icon, name, how, what, layer) in enumerate([
        (f'{XRAY}/docs/assets/icons/jacoco.svg', 'JaCoCo', 'agent', 'the executed lines', 'fichiers'),
        (f'{XRAY}/docs/assets/icons/async-profiler.svg', 'async-profiler', 'agent', 'where the time goes', 'infra'),
        (f'{XRAY}/docs/assets/icons/arthas.svg', 'Arthas', 'attached', 'the values passed', 'api')]):
    y = 510 + i * 40
    tools.append(p.node(130, y, 500, 34, f"&lt;b&gt;{name}&lt;/b&gt; — {how} — {what}",
                        SERVICE + f'spacingLeft=36;align=left;fontColor={LAYERS[layer][1]};'))
    p.icon(138, y + 6, 22, icon)

runs = p.node(220, 690, 360, 110,
              "&lt;b&gt;The measurements&lt;/b&gt;&#10;runs/&#10;"
              "jacoco.exec · profil.collapsed · watch-params.txt&#10;"
              "the only durable thing: everything else is derived from it",
              STORAGE)
report = p.node(220, 840, 360, 70,
                "&lt;b&gt;The report&lt;/b&gt;&#10;index.html · faits.jsonl · diagnostic.json&#10;"
                "one file to open, no external resource",
                APPLICATION)
cache = p.node(700, 220, 300, 70, "&lt;b&gt;Component cache&lt;/b&gt;&#10;~/.runtime-xray", STORAGE)
maven = p.node(1100, 220, 260, 70, "&lt;b&gt;Maven repository&lt;/b&gt;&#10;or an internal mirror", EXTERNAL)
p.icon(1112, 236, 32, f'{LL}/symboles/maven.svg')

p.edge(actor, orch, "starts a campaign&#10;command line")
p.edge(orch, jvm, "launches the application, agents attached&#10;JAVA_TOOL_OPTIONS")
p.edge(jvm, runs, "write their outputs, each in its own format")
p.edge(runs, report, "read back and assembled — by the same jar&#10;runtime-xray --report-only")
p.edge(cache, orch, "supplies the three tools")
p.edge(maven, cache, "fills the cache, once — at install time&#10;HTTPS", dashed=True)

p.label(1090, 380, 300, 260,
        "&lt;b&gt;WHAT THIS DIAGRAM SAYS&lt;/b&gt;&lt;br&gt;&lt;br&gt;"
        "Three tools, three questions, and not one of them answers all three: it is their union "
        "that answers, and the orchestrator only lays it out.&lt;br&gt;&lt;br&gt;"
        "Everything that is read — the page, the facts, the diagnostic — is derived from runs/. "
        "Deleting the page loses nothing; deleting runs/ loses the measurement.&lt;br&gt;&lt;br&gt;"
        "The network appears once, outside the zone, and at install time.")
p.label(40, 940, 1000, 40,
        "SHAPES actor · service · application · storage · external · zone&nbsp;&nbsp;&nbsp;"
        "COLOURS Files · Infrastructure · API and code&nbsp;&nbsp;&nbsp;"
        "The subject of the diagram is outlined in magenta: the orchestrator.", 10)

# ============================================================ page 2 — reference
q = Page("Reference use case")
zq = q.node(40, 60, 420, 320, "ONE RUN, MEASURED THREE TIMES OVER", ZONE)
qapp = q.node(70, 100, 360, 70, "&lt;b&gt;The analysed application&lt;/b&gt;&#10;sample-app.jar&#10;"
                                "22 classes · no dependency · no clock, no randomness",
              SERVICE + 'spacingLeft=44;align=left;')
q.icon(80, 115, 34, f'{LL}/symboles/java.svg')
root = q.node(70, 200, 360, 80, "&lt;b&gt;The root method&lt;/b&gt;&#10;RoutePlanner::travelTimeMinutes&#10;"
                                "named at launch — the only one whose values are captured",
              SUBJECT)
q.label(70, 300, 380, 70,
        "Deterministic: two runs produce the same trace.&lt;br&gt;"
        "Calibrated to ~10 s: long enough to attach to a live process.&lt;br&gt;"
        "Replayed under Java 21, then Java 25.")

q.label(520, 70, 420, 300,
        "&lt;b&gt;WHY THIS PROGRAM, AND NOT ANOTHER&lt;/b&gt;&lt;br&gt;&lt;br&gt;"
        "It is built to tell tools apart:&lt;br&gt;"
        "· calls conditioned by the context&lt;br&gt;"
        "· two paths never taken — PlaneSpeed, the SNOW branch&lt;br&gt;"
        "· a recursion, so the call tree has real depth&lt;br&gt;"
        "· a virtual dispatch&lt;br&gt;"
        "· a blocking operation, so wall-clock and processor time diverge")

LANES = [
    (f'{XRAY}/docs/assets/icons/jacoco.svg', 'JaCoCo', 'javaagent', 'fichiers', 'jacoco.exec',
     'Which lines were executed', '91.1 % of instructions, 81.8 % of branches · PlaneSpeed never loaded',
     'Says nothing about time, nor about the order of the calls.'),
    (f'{XRAY}/docs/assets/icons/async-profiler.svg', 'async-profiler', 'agentpath', 'infra', 'profil.collapsed',
     'Where the time goes', '71 % of the samples on business methods · the aggregated call tree',
     'Samples: it can never prove that a piece of code did NOT run.'),
    (f'{XRAY}/docs/assets/icons/arthas.svg', 'Arthas', 'attach', 'api', 'watch-params.txt',
     'Which values went through', 'Trip[id=TRIP-56, mode=CAR, weather=SUNNY] → 52.008… minutes',
     'A console, not a report — and one method at a time.'),
]
for i, (icon, name, how, layer, out, question, finding, limit) in enumerate(LANES):
    y = 430 + i * 130
    t = q.node(70, y, 300, 100, f"&lt;b&gt;{name}&lt;/b&gt;&#10;{how}&#10;&#10;{limit}",
               SERVICE + f'spacingLeft=44;align=left;fontColor={LAYERS[layer][1]};')
    q.icon(80, y + 14, 32, icon)
    f = q.node(450, y + 16, 220, 68, out + "&#10;inside runs/", STORAGE)
    a = q.node(750, y, 560, 100, f"&lt;b&gt;{question}&lt;/b&gt;&#10;{finding}",
               APPLICATION + f'align=left;spacingLeft=14;fontColor={LAYERS[layer][1]};')
    q.edge(t, f, "writes")
    q.edge(f, a, "answers")
q.node(70, 830, 1240, 60,
       "&lt;b&gt;None of the three answers the other two's question.&lt;/b&gt;&#10;"
       "That is the whole finding of the study: the answer is a combination, and what is left "
       "to settle is which second tool — not which one wins.", ZONE + 'align=left;spacingLeft=16;')

out = ('<mxfile host="app.diagrams.net" type="device">'
       + p.xml() + q.xml() + '</mxfile>\n')
open(XRAY + '/docs/assets/architecture.drawio', 'w', encoding='utf-8').write(out)
print('written', len(out), 'bytes')
