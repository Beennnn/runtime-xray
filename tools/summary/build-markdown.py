#!/usr/bin/env python3
"""Résumé Markdown des sorties d'outils.

Pourquoi : GitHub n'affiche PAS un fichier .html — il en montre le source brut. Le
rapport navigable reste donc illisible dans la forge. Le Markdown, lui, est rendu
nativement, y compris sur un dépôt privé et sans GitHub Pages.

Ce script ne mesure rien : il relit les sorties *machine* que les outils ont déjà
produites (le CSV de JaCoCo, les piles repliées d'async-profiler, la capture d'Arthas)
et les met en forme. Aucune donnée n'est inventée ici.
"""
import csv, collections, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
GEN = ROOT / "reports-demo" / "generated"
OUT = ROOT / "reports-demo" / "RAPPORT.md"

def coverage_section():
    csv_path = GEN / "jacoco" / "html" / "jacoco.csv"
    if not csv_path.exists():
        return "_Rapport JaCoCo absent — lancer `./tools/jacoco/collect.sh`._\n"
    rows = list(csv.DictReader(csv_path.open()))
    tot = collections.Counter()
    by_pkg = collections.defaultdict(lambda: collections.Counter())
    never = []
    for r in rows:
        im, ic = int(r["INSTRUCTION_MISSED"]), int(r["INSTRUCTION_COVERED"])
        bm, bc = int(r["BRANCH_MISSED"]), int(r["BRANCH_COVERED"])
        tot.update(im=im, ic=ic, bm=bm, bc=bc)
        by_pkg[r["PACKAGE"]].update(im=im, ic=ic, bm=bm, bc=bc)
        if ic == 0:
            never.append(f"{r['PACKAGE']}.{r['CLASS']}")

    pct = lambda c, m: 100 * c / (c + m) if (c + m) else 100.0
    out = [f"**{pct(tot['ic'], tot['im']):.1f} % des instructions** et "
           f"**{pct(tot['bc'], tot['bm']):.1f} % des branches** ont été exécutées.\n",
           "| Package | Instructions | Branches |", "|---|---:|---:|"]
    for pkg in sorted(by_pkg):
        c = by_pkg[pkg]
        out.append(f"| `{pkg}` | {pct(c['ic'], c['im']):.0f} % | {pct(c['bc'], c['bm']):.0f} % |")
    if never:
        out.append("\n**Jamais exécuté** — " + ", ".join(f"`{n}`" for n in never))
    return "\n".join(out) + "\n"

def calltree_section(limit=12):
    path = GEN / "formats" / "profil.collapsed"
    if not path.exists():
        return "_Profil absent — lancer `./tools/async-profiler/collect.sh`._\n"
    leaves = collections.Counter()
    total = 0
    for line in path.read_text().splitlines():
        stack, _, count = line.rpartition(" ")
        if not count.isdigit():
            continue
        total += int(count)
        leaves[stack.split(";")[-1]] += int(count)
    out = [f"{total} échantillons. Méthodes où le temps est réellement passé :\n",
           "| Méthode | Part |", "|---|---:|"]
    for name, n in leaves.most_common(limit):
        out.append(f"| `{name.replace('/', '.')}` | {100 * n / total:.1f} % |")
    return "\n".join(out) + "\n"

def params_section(limit=3):
    path = GEN / "arthas" / "watch-params.txt"
    if not path.exists():
        return "_Capture Arthas absente — lancer `./tools/arthas/collect.sh`._\n"
    text = re.sub(r"\x1b\[[0-9;]*m", "", path.read_text())
    blocks = re.findall(r"ts=[^\n]*\n(?:\s+@[^\n]*\n)+", text)[:limit]
    if not blocks:
        return "_Aucun appel capturé dans la sortie Arthas._\n"
    body = "".join(blocks)
    return ("Valeurs réellement passées à `RoutePlanner.travelTimeMinutes`, "
            "telles qu'Arthas les a lues :\n\n```\n" + body.rstrip() + "\n```\n")

OUT.write_text(f"""# Rapport d'analyse dynamique

> Généré depuis les sorties machine des outils par `tools/summary/build-markdown.py`.
> **Ce fichier existe parce que GitHub affiche un `.html` comme du code source, pas comme
> une page.** Le Markdown, lui, est rendu nativement — c'est le seul format qui se lit
> directement dans la forge.
>
> Version navigable : [GitHub Pages](https://beennnn.github.io/runtime-xray/) ·
> Version hors ligne : [le rapport en un fichier](https://github.com/Beennnn/runtime-xray/releases/latest)

## 1. Par où le code est passé — JaCoCo

{coverage_section()}
## 2. Où le temps est passé — async-profiler

{calltree_section()}
## 3. Avec quelles valeurs — Arthas

{params_section()}
---

Les trois outils sont gratuits, open source et fonctionnent hors ligne.
Méthode : [docs/METHODOLOGY.md](../docs/METHODOLOGY.md).
""")
print(f"→ {OUT}")
