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
ok=0 ko=0
etape(){ if [ "$1" = 0 ]; then echo "  ✅ $2"; ok=$((ok+1)); else echo "  ❌ $2"; ko=$((ko+1)); fi; }

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
gpg --keyserver keys.openpgp.org --recv-keys 8D181AA1F3545E3C43804355D7D3E62B52C66FCA >/dev/null 2>&1
gpg --status-fd 1 --verify "$JAR.asc" "$JAR" 2>/dev/null | grep -q GOODSIG
etape $? "la signature du jar est valide"
echo

echo "3. Le jar est exécutable"
java -jar "$JAR" --help >/dev/null 2>&1
etape $? "l'aide s'affiche"
echo

echo "4. Il analyse réellement une application"
java -jar "$JAR" \
  --java "java -jar $APP --iterations 400000" \
  --root "lab.sample.RoutePlanner::travelTimeMinutes" \
  --sources "$DEPOT/sample-app/src/main/java" \
  --name "Recette Central" --out sortie > analyse.log 2>&1
etape $? "l'analyse se termine sans erreur"

grep -q "Classes analysées" analyse.log
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

echo "────────────────────────────────────"
echo "  $ok réussis · $ko échoués"
echo "  sortie conservée : $BAC"
[ "$ko" = 0 ] || tail -20 analyse.log
exit "$ko"
