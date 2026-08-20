#!/usr/bin/env python3
"""Vue intégrée : arbre d'appel + couverture + valeurs, dans une seule page.

Aucune mesure n'est faite ici. Le script relit les sorties *machine* déjà produites par
les trois outils et les met côte à côte :

  - jacoco.xml            -> couverture ligne par ligne, et ligne de début de chaque méthode
  - profil.collapsed      -> l'arbre d'appel agrégé (async-profiler)
  - trace-calltree.txt    -> pour UN appel, quel appel part de quelle ligne (Arthas)
  - watch-params.txt      -> les valeurs réellement passées (Arthas)

Le tout est embarqué dans un HTML autonome : aucune ressource externe, donc lisible hors
ligne et transmissible en pièce jointe.
"""
import json, pathlib, re, sys, html as htmlmod
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[2]
GEN = ROOT / "reports-demo" / "generated"
SRC = ROOT / "sample-app" / "src" / "main" / "java"
OUT = GEN / "vue-integree"

# ---------------------------------------------------------------- couverture
def load_coverage():
    """-> (lignes par fichier source, méthodes par classe, arbre des packages)"""
    xml = GEN / "jacoco" / "html" / "jacoco.xml"
    if not xml.exists():
        sys.exit("jacoco.xml absent — lancer ./tools/run-all.sh")
    # Le rapport JaCoCo référence une DTD externe : on la neutralise pour rester hors ligne.
    parser = ET.XMLParser()
    tree = ET.parse(xml, parser=parser)
    lines, methods, packages = {}, {}, {}
    for pkg in tree.getroot().iter("package"):
        pkg_name = pkg.get("name")
        classes = []
        for cls in pkg.findall("class"):
            cname = cls.get("name")
            src = f"{pkg_name}/{cls.get('sourcefilename')}"
            mlist = []
            for m in cls.findall("method"):
                if not m.get("line"):
                    continue
                # Couverture de la méthode elle-même : c'est JaCoCo qui sait EXACTEMENT
                # ce qui a été exécuté. L'échantillonnage d'async-profiler, lui, rate les
                # méthodes rares — il sert à pondérer, pas à établir la liste.
                ic = 0
                for c in m.findall("counter"):
                    if c.get("type") == "INSTRUCTION":
                        ic = int(c.get("covered", 0))
                mlist.append({"name": m.get("name"), "line": int(m.get("line")),
                              "covered": ic})
            methods[cname] = {"source": src, "methods": sorted(mlist, key=lambda m: m["line"])}
            counters = {c.get("type"): c for c in cls.findall("counter")}
            inst = counters.get("INSTRUCTION")
            cov = int(inst.get("covered")) if inst is not None else 0
            miss = int(inst.get("missed")) if inst is not None else 0
            classes.append({"name": cname, "simple": cname.split("/")[-1],
                            "source": src, "covered": cov, "missed": miss})
        packages[pkg_name] = sorted(classes, key=lambda c: c["simple"])
        for sf in pkg.findall("sourcefile"):
            key = f"{pkg_name}/{sf.get('name')}"
            per_line = {}
            for ln in sf.findall("line"):
                nr, ci, mi = int(ln.get("nr")), int(ln.get("ci")), int(ln.get("mi"))
                mb, cb = int(ln.get("mb", 0)), int(ln.get("cb", 0))
                if ci == 0 and mi > 0:
                    status = "miss"
                elif mb > 0:
                    status = "part"
                else:
                    status = "full"
                per_line[nr] = {"s": status, "b": (cb, cb + mb) if (cb + mb) else None}
            lines[key] = per_line
    return lines, methods, packages

# ------------------------------------------------------------- arbre d'appel
# Frames de l'instrumentation d'Arthas. Quand les trois outils tournent en une seule
# passe, Arthas intercepte chaque entrée et sortie de la méthode observée : ses frames
# captent l'essentiel des échantillons et écrasent le profil.
#
# On les REPLIE sur leur appelant plutôt que de les afficher : les échantillons restent
# comptés, mais attribués à la méthode applicative qui les a déclenchés. La forme de
# l'arbre est ainsi restituée. Réserve à garder en tête : le coût de la méthode observée
# reste surestimé, puisqu'on mesure aussi le travail de l'instrumentation.
ARTHAS_FRAME = re.compile(r"arthas|SpyAPI|taobao", re.I)

def load_calltree(limit_depth=40):
    path = GEN / "async-profiler" / "profil.collapsed"
    if not path.exists():
        return {"name": "(profil absent)", "total": 0, "children": []}, None
    root = {"name": "tout", "total": 0, "children": {}}
    folded = 0
    for line in path.read_text().splitlines():
        stack, _, count = line.rpartition(" ")
        if not count.isdigit():
            continue
        n = int(count)
        frames = stack.split(";")
        kept = [f for f in frames if not ARTHAS_FRAME.search(f)]
        if len(kept) != len(frames):
            folded += n
        root["total"] += n
        node = root
        for frame in kept[:limit_depth]:
            node = node["children"].setdefault(frame, {"name": frame, "total": 0, "children": {}})
            node["total"] += n

    def to_list(node):
        kids = sorted(node["children"].values(), key=lambda c: -c["total"])
        return {"name": node["name"], "total": node["total"],
                "children": [to_list(k) for k in kids]}
    note = None
    if folded:
        share = 100 * folded / root["total"] if root["total"] else 0
        note = (f"Profil collecté pendant qu'Arthas observait le programme : "
                f"{share:.0f} % des échantillons tombaient dans son instrumentation. "
                f"Ils ont été repliés sur la méthode applicative qui les a déclenchés, "
                f"ce qui restitue la forme de l'arbre. Réserve : le coût affiché de la "
                f"méthode observée reste surestimé — le temps n'étant pas un critère du "
                f"projet, cela ne remet pas en cause les objectifs.")
    return to_list(root), note

# ------------------------------------------------------------------- arthas
CALL_RE = re.compile(r"[+`]---\[([^\]]*)\]\s+([\w.$]+):(\w+)\(\)\s+#(\d+)")

def load_trace():
    """Pour un appel : quelle ligne appelle quoi, et en combien de temps."""
    path = GEN / "arthas" / "trace-calltree.txt"
    if not path.exists():
        return {}
    text = re.sub(r"\x1b\[[0-9;]*m", "", path.read_text())
    per_line = {}
    for meta, cls, method, nr in CALL_RE.findall(text):
        m = re.search(r"([\d.]+)ms", meta.replace(",", "."))
        cost = f"{m.group(1)}ms" if m else ""
        pct = re.match(r"\s*([\d.,]+)%", meta)
        per_line.setdefault(int(nr), []).append(
            {"callee": f"{cls.split('.')[-1]}.{method}()", "cost": cost,
             "pct": pct.group(1) + "%" if pct else ""})
    return per_line

def load_values(limit=6):
    path = GEN / "arthas" / "watch-params.txt"
    if not path.exists():
        return []
    text = re.sub(r"\x1b\[[0-9;]*m", "", path.read_text())
    out = []
    for block in re.findall(r"ts=([\d\- :.]+);\s*\[cost=([^\]]*)\][^\n]*\n((?:\s+@[^\n]*\n)+)", text):
        vals = re.findall(r"@(\w+)\[([^\]]*)\]", block[2])
        out.append({"ts": block[0].strip(), "cost": block[1],
                    "values": [{"type": t, "value": v} for t, v in vals]})
        if len(out) >= limit:
            break
    return out

# -------------------------------------------------------------------- rendu
def load_sources():
    return {str(p.relative_to(SRC)): p.read_text().splitlines()
            for p in SRC.rglob("*.java")}

def main():
    lines, methods, packages = load_coverage()
    calltree, profile_note = load_calltree()
    data = {
        "coverage": lines,
        "methods": methods,
        "packages": packages,
        "calltree": calltree,
        "profileNote": profile_note,
        "trace": {str(k): v for k, v in load_trace().items()},
        "values": load_values(),
        "sources": load_sources(),
        "tracedClass": "lab/sample/RoutePlanner",
    }
    OUT.mkdir(parents=True, exist_ok=True)
    template = (pathlib.Path(__file__).parent / "dashboard.html").read_text()
    page = template.replace("/*__DATA__*/", json.dumps(data, ensure_ascii=False))
    (OUT / "index.html").write_text(page)
    size = (OUT / "index.html").stat().st_size / 1024
    print(f"→ {OUT / 'index.html'} ({size:.0f} Ko)")

if __name__ == "__main__":
    main()
