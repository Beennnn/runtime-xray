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
import argparse, json, pathlib, re, sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[2]

def parse_args():
    ap = argparse.ArgumentParser(description="Assemble la vue intégrée à partir des sorties d'outils.")
    ap.add_argument("--gen", default=str(ROOT / "reports-demo" / "generated"),
                    help="Répertoire contenant jacoco/, async-profiler/ et arthas/")
    ap.add_argument("--sources", default=str(ROOT / "sample-app" / "src" / "main" / "java"),
                    help="Répertoires de sources .java, séparés par ':'")
    ap.add_argument("--traced", default="",
                    help="Classe inspectée (paquet.Classe). Par défaut, déduite du contexte "
                         "de chaque exécution : rien n'est codé en dur.")
    ap.add_argument("--out", default=None,
                    help="Répertoire où écrire index.html (défaut : le répertoire <gen> lui-même, "
                         "pour qu'ouvrir le dossier suffise à trouver la vue)")
    return ap.parse_args()

ARGS = parse_args()
GEN = pathlib.Path(ARGS.gen)
SRC_DIRS = [pathlib.Path(d) for d in ARGS.sources.split(":") if d]
OUT = pathlib.Path(ARGS.out) if ARGS.out else GEN
TRACED = ARGS.traced.replace(".", "/")

# ---------------------------------------------------------------- couverture
def load_coverage(GEN):
    """-> (lignes par fichier source, méthodes par classe, arbre des packages)"""
    xml = GEN / "jacoco" / "html" / "jacoco.xml"
    if not xml.exists():
        sys.exit(f"jacoco.xml absent sous {xml.parent} — lancer la collecte d'abord")
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

def load_calltree(GEN, limit_depth=40):
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

def first_existing(GEN, *names):
    for n in names:
        p = GEN / "arthas" / n
        if p.exists():
            return p
    return None

def load_trace(GEN):
    """Pour un appel : quelle ligne appelle quoi, et en combien de temps."""
    path = first_existing(GEN, "trace-calltree.txt", "trace-params.txt", "trace.txt")
    if path is None:
        return {}
    text = re.sub(r"\x1b\[[0-9;]*m", "", path.read_text())
    per_line = {}
    for meta, cls, method, nr in CALL_RE.findall(text):
        m = re.search(r"([\d.]+)ms", meta.replace(",", "."))
        cost = f"{m.group(1)}ms" if m else ""
        pct = re.match(r"\s*([\d.,]+)%", meta)
        per_line.setdefault(int(nr), []).append(
            {"callee": f"{cls.split('.')[-1]}.{method}()", "cost": cost,
             "frame": cls.replace(".", "/") + "." + method,
             "pct": pct.group(1) + "%" if pct else ""})
    return per_line

def load_values(GEN, limit_per_method=8):
    """Valeurs capturées, groupées PAR MÉTHODE.

    La sortie de l'observateur a cette forme, et l'indentation porte le sens :

        method=paquet.Classe.methode location=AtExit
        ts=…; [cost=1.24ms] result=@ArrayList[
            @Object[][                 <- le tableau des paramètres
                @Leg[Leg[from=Auch…]], <- un paramètre par ligne, indenté de 8
                @Mode[CAR],
            ],
            @Double[29.72],            <- indenté de 4 : la valeur de RETOUR
        ]

    Deux pièges qui ont produit un affichage faux avant correction : les crochets sont
    **imbriqués** (une expression régulière非 gourmande tronque la valeur), et la ligne de
    retour est **hors** du bloc des paramètres (une capture ligne-à-ligne naïve l'ignore).
    On s'appuie donc sur l'indentation plutôt que sur un motif global.
    """
    path = first_existing(GEN, "watch-params.txt", "watch.txt")
    if path is None:
        return {}
    text = re.sub(r"\x1b\[[0-9;]*m", "", path.read_text())
    lines = text.splitlines()

    def entry(line):
        """'        @Leg[Leg[from=…]],' -> ('Leg', 'Leg[from=…]')"""
        m = re.match(r"\s*@(\w+)\[(.*?),?\s*$", line)
        if not m:
            return None
        typ, rest = m.group(1), m.group(2)
        rest = rest.rstrip().rstrip(",").rstrip()
        # Retirer UN seul crochet fermant — celui de @Type[…]. En retirer davantage
        # tronquerait les valeurs imbriquées comme Leg[from=…, to=…].
        if rest.endswith("]"):
            rest = rest[:-1]
        return {"type": typ, "value": rest.strip()}

    per_method, i = {}, 0
    while i < len(lines):
        m = re.match(r"method=([\w.$]+)\s", lines[i])
        if not m:
            i += 1
            continue
        fqmn = m.group(1)
        cls, _, meth = fqmn.rpartition(".")
        frame = cls.replace(".", "/") + "." + meth
        i += 1
        ts = cost = ""
        if i < len(lines):
            t = re.match(r"ts=([\d\- :.]+);\s*\[cost=([^\]]*)\]", lines[i])
            if t:
                ts, cost = t.group(1).strip(), t.group(2)
                i += 1
        params, retour = [], None
        while i < len(lines) and not lines[i].startswith("method="):
            line = lines[i]
            indent = len(line) - len(line.lstrip())
            if line.strip().startswith("@") and indent >= 8:
                e = entry(line)
                if e and e["value"]:
                    params.append(e)
            elif line.strip().startswith("@") and indent == 4 and "@Object[][" not in line:
                e = entry(line)
                if e and e["value"]:
                    retour = e
            elif line.strip() in ("]", "],") and indent == 0:
                i += 1
                break
            i += 1
        calls = per_method.setdefault(frame, [])
        if len(calls) < limit_per_method and (params or retour):
            calls.append({"ts": ts, "cost": cost, "params": params, "retour": retour})
    return per_method

# -------------------------------------------------------------------- rendu
def load_context(GEN):
    """Contexte de l'exécution, s'il a été enregistré. Sans lui, un rapport retrouvé plus
    tard ne dit pas de quoi il parle : quel programme, quels arguments, quand, où."""
    path = GEN / "run-context.json"
    if not path.exists():
        return None
    try:
        return json.loads(path.read_text())
    except Exception:
        return None

def load_sources():
    """Les chemins sont relatifs à leur racine de sources, comme dans le rapport JaCoCo
    (paquet/Fichier.java) — ce qui permet de rapprocher source et couverture."""
    out = {}
    for root in SRC_DIRS:
        if not root.exists():
            continue
        for f in root.rglob("*.java"):
            out[str(f.relative_to(root))] = f.read_text(errors="replace").splitlines()
    return out

# Renommage après coup : un simple fichier à la racine du répertoire commun, qui associe
# l'identifiant permanent d'une exécution à un nom choisi plus tard. Le nom d'origine
# reste dans run-context.json — supprimer l'entrée suffit à revenir en arrière.
NAMES_FILE = "noms.json"

def load_overrides():
    path = GEN / NAMES_FILE
    if not path.exists():
        return {}
    try:
        return json.loads(path.read_text())
    except Exception:
        print(f"⚠️  {path} illisible — noms d'origine conservés", file=sys.stderr)
        return {}

def find_runs(root, depth=3):
    """Toute exécution est un répertoire contenant run-context.json. On cherche en
    profondeur limitée, ce qui permet de rassembler plusieurs répertoires de sortie
    dans un répertoire commun et de les traiter ensemble."""
    found = []
    def walk(d, level):
        if level > depth or not d.is_dir():
            return
        if (d / "run-context.json").exists() or (d / "jacoco" / "html" / "jacoco.xml").exists():
            found.append(d); return
        for sub in sorted(d.iterdir()):
            if sub.is_dir() and not sub.name.startswith("."):
                walk(sub, level + 1)
    walk(root, 0)
    return found

def _traced_class(ctx, values):
    racine = (ctx or {}).get("methodeRacine") or ""
    if racine:
        return racine.split("::")[0].replace(".", "/")
    if values:
        first = next(iter(values))
        return first[:first.rfind(".")]
    return TRACED

def load_run(base, label, overrides):
    """Charge une exécution. Les sources ne sont PAS chargées ici : elles sont communes
    à toutes les exécutions d'un même projet, donc stockées une seule fois."""
    lines, methods, packages = load_coverage(base)
    calltree, note = load_calltree(base)
    ctx = load_context(base)
    values = load_values(base)
    try:
        rel = str(base.resolve().relative_to(OUT.resolve())) + "/"
    except ValueError:
        rel = ""
    uuid = (ctx or {}).get("uuid", "")
    origine = (ctx or {}).get("nomOrigine") or (ctx or {}).get("nom") or label
    exists = lambda rel: (base / rel).exists()
    return {
        "rapports": {
            "couverture": exists("jacoco/html/index.html"),
            "ciblee":     exists("jacoco-focused/html/index.html"),
            "flamegraph": exists("async-profiler/flamegraph.html"),
            "arbre":      exists("async-profiler/tree.html"),
        },
        "uuid": uuid,
        "nomOrigine": origine,
        "renomme": bool(uuid and uuid in overrides),
        "nom": overrides.get(uuid, origine),
        "chemin": "" if rel == "./" else rel,
        "coverage": lines,
        "methods": methods,
        "packages": packages,
        "calltree": calltree,
        "profileNote": note,
        "trace": {str(k): v for k, v in load_trace(base).items()},
        "values": values,
        "context": ctx,
        # La classe inspectée se déduit, dans l'ordre : du contexte de l'exécution, sinon
        # des valeurs réellement capturées, sinon de l'option. Rien n'est codé en dur —
        # le rapport doit marcher pour n'importe quelle application.
        "tracedClass": _traced_class(ctx, values),
    }

def main():
    overrides = load_overrides()
    bases = find_runs(GEN)
    if not bases:
        sys.exit(f"aucune exécution trouvée sous {GEN} "
                 f"(un répertoire d'exécution contient run-context.json)")
    bases.sort(key=lambda d: d.stat().st_mtime, reverse=True)
    runs = [load_run(b, b.name, overrides) for b in bases]

    data = {"runs": runs, "sources": load_sources()}
    OUT.mkdir(parents=True, exist_ok=True)
    template = (pathlib.Path(__file__).parent / "dashboard.html").read_text()
    page = template.replace("/*__DATA__*/", json.dumps(data, ensure_ascii=False))
    (OUT / "index.html").write_text(page)
    size = (OUT / "index.html").stat().st_size / 1024
    print(f"→ {OUT / 'index.html'} ({size:.0f} Ko, {len(runs)} exécution(s))")

if __name__ == "__main__":
    main()
