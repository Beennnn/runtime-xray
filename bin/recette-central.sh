#!/usr/bin/env bash
# Recette de l'artefact publié sur Maven Central.
#
# On ne vérifie pas que le jar se télécharge — ça, un HTTP 200 le dit. On vérifie qu'il
# FONCTIONNE : récupéré depuis Central, dans un répertoire vierge, sans rien du dépôt de
# développement, et qu'il produit réellement un rapport sur une application quelconque.
#
# C'est la seule épreuve qui distingue « publié » de « utilisable ».
set -uo pipefail

VERSION="${1:-1.0.0}"
DEPOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE="https://repo1.maven.org/maven2/io/github/beennnn/runtime-xray-cli/$VERSION"
JAR="runtime-xray-cli-$VERSION.jar"
BAC="$(mktemp -d)"
APP="$DEPOT/sample-app/target/sample-app.jar"
ok=0 ko=0 na=0
etape(){ if [ "$1" = 0 ]; then echo "  ✅ $2"; ok=$((ok+1)); else echo "  ❌ $2"; ko=$((ko+1)); fi; }
# Une version publiée avant une fonctionnalité ne peut pas la porter : la compter en échec
# ferait passer une recette juste pour une recette ratée. On le dit, sans compter contre.
horsSujet(){ echo "  ○ $1 — ${2:-pas dans la version $VERSION}"; na=$((na+1)); }
# Ce que le jar téléchargé sait faire, lu sur son aide plutôt que déduit du numéro de
# version : c'est la seule source qui ne se trompe pas.
sait(){ grep -q -- "$1" aide.txt; }

cd "$BAC" || exit 1
echo "Répertoire vierge : $BAC"
echo

echo "1. Récupération depuis Maven Central"
curl -sf -O "$BASE/$JAR";              etape $? "le jar se télécharge"
curl -sf -O "$BASE/$JAR.asc";          etape $? "sa signature se télécharge"
curl -sf -O "$BASE/runtime-xray-cli-$VERSION-sources.jar"; etape $? "les sources sont là"
curl -sf -O "$BASE/runtime-xray-cli-$VERSION-javadoc.jar"; etape $? "la javadoc est là"
echo

echo "2. Signature — la clé est récupérée depuis un serveur public, comme le ferait un tiers"
# Sans la clé, on ne peut RIEN conclure : ni que la signature est bonne, ni qu'elle est
# mauvaise. Un réseau qui bloque les serveurs de clés — c'est courant en entreprise — ne
# doit pas faire passer un artefact valide pour un artefact douteux.
if gpg --keyserver keys.openpgp.org --recv-keys 8D181AA1F3545E3C43804355D7D3E62B52C66FCA >/dev/null 2>&1
then
  gpg --status-fd 1 --verify "$JAR.asc" "$JAR" 2>/dev/null | grep -q GOODSIG
  etape $? "la signature du jar est valide"
else
  horsSujet "vérification de signature" "clé introuvable depuis ce réseau : on ne peut \
rien conclure, ni dans un sens ni dans l'autre"
fi
echo

echo "3. Le jar est exécutable"
java -jar "$JAR" --help > aide.txt 2>&1
etape $? "l'aide s'affiche"
echo

echo "4. Il analyse réellement une application"
java -jar "$JAR" \
  --java "java -jar $APP --iterations 400000" \
  --root "lab.sample.RoutePlanner::travelTimeMinutes" \
  --sources "$DEPOT/sample-app/src/main/java" \
  --name "Recette Central" --out sortie > analyse.log 2>&1
etape $? "l'analyse se termine sans erreur"

# Sans accent : ce contrôle lit la sortie d'une AUTRE version, produite sur une machine dont
# on ne choisit pas l'encodage. Chercher « analysées » ferait échouer une version correcte
# dont la console est en ASCII.
grep -q "Classes analys" analyse.log
etape $? "le bytecode est déduit tout seul (aucun --classes)"

[ -s sortie/index.html ];        etape $? "la page est produite"
[ -s sortie/rapport.md ];        etape $? "le rapport Markdown est produit"
ls sortie/runs/*/jacoco/html/index.html >/dev/null 2>&1
etape $? "la couverture est rendue"
ls sortie/runs/*/async-profiler/flamegraph.html >/dev/null 2>&1
etape $? "le profil natif est rendu"

# La page doit contenir des données, pas seulement le gabarit.
python3 - <<'PY'
import re, pathlib, sys
p = pathlib.Path("sortie/index.html")
t = p.read_text()
m = re.search(r'const D = (\{.*?\});\n', t, re.S)
sys.exit(0 if m and len(m.group(1)) > 10000 and '"runs"' in m.group(1) else 1)
PY
etape $? "la page contient les données de l'exécution, pas un gabarit vide"
echo

echo "5. Ce que cette version sait faire en plus"
if sait "--export"; then
  java -jar "$JAR" --report-only --out sortie --export tout >> analyse.log 2>&1
  etape $? "les exports se produisent"
  ls sortie/runs/*/exports/couverture.lcov >/dev/null 2>&1
  etape $? "la couverture est réécrite en LCOV"
  ls sortie/runs/*/exports/profil.cpuprofile >/dev/null 2>&1
  etape $? "le profil est réécrit en cpuprofile"
else
  horsSujet "réécriture des mesures pour d'autres outils (--export)"
fi

if sait "--niveau"; then
  java -jar "$JAR" --java "java -jar $APP --iterations 200000" --niveau couverture \
    --out leger > leger.log 2>&1
  etape $? "une mesure au niveau « couverture » se termine"
  [ ! -s "$(ls leger/runs/*/async-profiler/profil.collapsed 2>/dev/null | head -1)" ]
  etape $? "elle n'écrit aucun profil, comme annoncé"
else
  horsSujet "niveaux d'observation (--niveau)"
fi

if sait "--serve"; then
  PORT="$(python3 -c 'import socket;s=socket.socket();s.bind(("127.0.0.1",0));print(s.getsockname()[1]);s.close()')"
  java -jar "$JAR" --report-only --out sortie --serve "$PORT" > serveur.log 2>&1 &
  pid=$!
  for _ in $(seq 1 40); do curl -sf "http://127.0.0.1:$PORT/__xray/ping" >/dev/null && break; sleep 0.25; done
  curl -sf "http://127.0.0.1:$PORT/__xray/ping" | grep -q '"peutEcrire":true'
  etape $? "le rapport se sert, et la page peut y écrire"
  kill "$pid" 2>/dev/null
else
  horsSujet "rapport servi et annotations écrites (--serve)"
fi
echo

echo "────────────────────────────────────"
echo "  $ok réussis · $ko échoués · $na hors sujet pour cette version"
echo "  sortie conservée : $BAC"
[ "$ko" = 0 ] || tail -20 analyse.log
exit "$ko"
