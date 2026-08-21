#!/usr/bin/env bash
# Recette du jar construit depuis les sources.
#
# Le pendant de recette-central.sh, qui éprouve l'artefact PUBLIÉ : celle-ci éprouve ce
# qu'on s'apprête à publier. Elle ne remplace pas les tests unitaires — ils vérifient des
# décisions, une par une, sans jamais lancer de JVM observée. Ici on prend la chaîne
# entière : une vraie application, les trois outils d'analyse téléchargés et injectés, la
# page assemblée, les exports réécrits, le serveur qui écrit une annotation à côté des
# mesures.
#
# C'est la seule épreuve qui dise que les morceaux, mis bout à bout, fonctionnent encore.
set -uo pipefail

DEPOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="$DEPOT/orchestrator/target/runtime-xray.jar"
APP="$DEPOT/sample-app/target/sample-app.jar"
BAC="$(mktemp -d)"
ok=0 ko=0
etape(){ if [ "$1" = 0 ]; then echo "  ✅ $2"; ok=$((ok+1)); else echo "  ❌ $2"; ko=$((ko+1)); fi; }
serveur_pid=""
nettoyage(){ [ -n "$serveur_pid" ] && kill "$serveur_pid" 2>/dev/null; }
trap nettoyage EXIT

cd "$BAC" || exit 1
echo "Répertoire vierge : $BAC"
echo

echo "0. Ce qu'il faut avoir construit"
[ -s "$JAR" ]; etape $? "le jar de l'outil existe (mvn package)"
[ -s "$APP" ]; etape $? "le programme de démonstration existe"
if [ "$ko" != 0 ]; then echo "  → lancer d'abord : mvn -q package"; exit "$ko"; fi
echo

# L'application doit vivre plus longtemps que le délai d'attachement, sinon les valeurs ne
# peuvent PAS être capturées — et ce n'est pas un défaut de l'outil, c'est de l'arithmétique.
echo "1. Une analyse complète, sur une application réelle"
java -jar "$JAR" \
  --java "java -jar $APP --iterations 12000000" \
  --root "lab.sample.RoutePlanner::travelTimeMinutes" \
  --sources "$DEPOT/sample-app/src/main/java" \
  --name "Recette locale" --attach-after 4 \
  --out sortie --export tout > analyse.log 2>&1
etape $? "l'analyse se termine sans erreur"

grep -q "Classes analysées" analyse.log
etape $? "le bytecode est déduit tout seul (aucun --classes)"

[ -s sortie/index.html ]; etape $? "la page est produite"
[ -s sortie/rapport.md ]; etape $? "le rapport Markdown est produit"
ls sortie/runs/*/jacoco/html/index.html >/dev/null 2>&1
etape $? "la couverture est rendue"
ls sortie/runs/*/async-profiler/flamegraph.html >/dev/null 2>&1
etape $? "le profil natif est rendu"
[ -s "$(ls sortie/runs/*/arthas/watch-params.txt 2>/dev/null | head -1)" ]
etape $? "les valeurs des paramètres sont capturées"
echo

echo "2. La page porte les trois informations, pas un gabarit"
python3 - <<'PY'
import json, pathlib, re, sys
t = pathlib.Path("sortie/index.html").read_text()
m = re.search(r'const D = (\{.*?\});\n', t, re.S)
if not m: sys.exit(1)
d = json.loads(m.group(1))
run = d["runs"][0]
manque = []
if not d.get("sources"):                      manque.append("sources")
if len(run.get("methods", {})) < 5:           manque.append("couverture")
if run.get("calltree", {}).get("total", 0) < 100: manque.append("arbre d'appel")
if not run.get("values"):                     manque.append("valeurs")
if not run.get("context"):                    manque.append("contexte")
print("   manque : " + ", ".join(manque) if manque else "", end="")
sys.exit(1 if manque else 0)
PY
etape $? "sources, couverture, arbre d'appel, valeurs et contexte sont embarqués"
echo

echo "3. Les exports s'ouvrent ailleurs"
E="$(ls -d sortie/runs/*/exports 2>/dev/null | head -1)"
[ -n "$E" ]; etape $? "le répertoire d'exports est écrit"
grep -q "cpu-clock:" "$E/profil.perf.txt" 2>/dev/null
etape $? "l'export perf a la forme de perf script"
head -2 "$E/profil.perf.txt" 2>/dev/null | tail -1 | grep -q "^	"
etape $? "ses frames sont indentées, comme le lecteur les attend"
python3 - "$E" <<'PY'
import json, pathlib, sys
p = json.loads((pathlib.Path(sys.argv[1]) / "profil.cpuprofile").read_text())
ids = {n["id"] for n in p["nodes"]}
ok = (p["samples"] and len(p["samples"]) == len(p["timeDeltas"])
      and all(s in ids for s in p["samples"])
      and all(c in ids for n in p["nodes"] for c in n.get("children", [])))
sys.exit(0 if ok else 1)
PY
etape $? "l'export cpuprofile est cohérent (nœuds, échantillons, intervalles)"
grep -q "^end_of_record" "$E/couverture.lcov" 2>/dev/null
etape $? "l'export LCOV est complet"
python3 -c "import json,sys;json.load(open(sys.argv[1]))" "$E/valeurs.json" 2>/dev/null
etape $? "l'export des valeurs est du JSON valide"
echo

echo "4. Le niveau d'observation décide de ce qu'on paie"
java -jar "$JAR" \
  --java "java -jar $APP --iterations 200000" \
  --niveau couverture --sources "$DEPOT/sample-app/src/main/java" \
  --name "Couverture seule" --out leger > leger.log 2>&1
etape $? "une mesure au niveau « couverture » se termine"
grep -q "pas d'échantillonnage" leger.log
etape $? "l'outil annonce qu'il n'échantillonne pas"
[ ! -s "$(ls leger/runs/*/async-profiler/profil.collapsed 2>/dev/null | head -1)" ]
etape $? "aucun profil n'est écrit à ce niveau"
ls leger/runs/*/jacoco/html/index.html >/dev/null 2>&1
etape $? "la couverture, elle, est bien là"
echo

echo "5. Le serveur écrit les annotations à côté des mesures"
PORT="$(python3 -c 'import socket;s=socket.socket();s.bind(("127.0.0.1",0));print(s.getsockname()[1]);s.close()')"
java -jar "$JAR" --report-only --out sortie \
  --sources "$DEPOT/sample-app/src/main/java" --serve "$PORT" > serveur.log 2>&1 &
serveur_pid=$!
for _ in $(seq 1 40); do curl -sf "http://127.0.0.1:$PORT/__xray/ping" >/dev/null && break; sleep 0.25; done

curl -sf "http://127.0.0.1:$PORT/__xray/ping" | grep -q '"peutEcrire":true'
etape $? "la page apprend qu'elle peut écrire"

UUID="$(python3 -c "import json,glob;print(json.load(open(glob.glob('sortie/runs/*/run-context.json')[0]))['uuid'])")"
EMPREINTE="$(curl -sf "http://127.0.0.1:$PORT/__xray/noms" | python3 -c "import json,sys;print(json.load(sys.stdin)['empreintes'].get('$UUID',''))")"
curl -sf -X POST "http://127.0.0.1:$PORT/__xray/noms/$UUID" \
  -H 'Content-Type: application/json' \
  -d "{\"base\":\"$EMPREINTE\",\"valeur\":{\"nom\":\"Nommée par la recette\",\"etiquettes\":{\"recette\":\"\"}}}" \
  >/dev/null
etape $? "l'annotation est acceptée"

[ -s "$(ls sortie/runs/*/config.json 2>/dev/null | head -1)" ]
etape $? "elle est écrite DANS le répertoire de l'exécution"

# La même écriture, repartie de la version d'avant : elle doit être refusée, pas appliquée.
code="$(curl -s -o /dev/null -w '%{http_code}' -X POST "http://127.0.0.1:$PORT/__xray/noms/$UUID" \
  -H 'Content-Type: application/json' \
  -d "{\"base\":\"$EMPREINTE\",\"valeur\":{\"nom\":\"écrasement\"}}")"
[ "$code" = "409" ]; etape $? "une écriture partie d'une version périmée est refusée ($code)"

for _ in $(seq 1 40); do grep -q "Nommée par la recette" sortie/index.html && break; sleep 0.25; done
grep -q "Nommée par la recette" sortie/index.html
etape $? "la page a été régénérée avec l'annotation"

curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PORT/../$(basename "$BAC")" | grep -q '40'
etape $? "un chemin qui sort du répertoire servi est refusé"
echo

echo "────────────────────────────────────"
echo "  $ok réussis · $ko échoués"
echo "  sortie conservée : $BAC"
[ "$ko" = 0 ] || tail -25 analyse.log
exit "$ko"
