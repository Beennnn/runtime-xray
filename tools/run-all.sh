#!/usr/bin/env bash
# Le scénario de référence, sans licence : UNE exécution, trois outils, une vue.
#
#   JaCoCo           -javaagent   : instrumente le bytecode au chargement
#   async-profiler   -agentpath   : échantillonne les piles
#   Arthas           attachement  : interroge les appels vivants pendant que ça tourne
#
# Pourquoi une seule passe alors qu'Arthas perturbe le profil : parce que ce qu'il
# perturbe n'est pas un critère du projet. Détail et réserves : docs/SOLUTION.md.
#
#   TWO_RUNS=1 ./tools/run-all.sh   sépare mesure et inspection en deux exécutions,
#                                   pour obtenir des pourcentages de temps non faussés.
set -euo pipefail

JACOCO_VERSION="0.8.13"
ARTHAS_VERSION="4.3.4"
TWO_RUNS="${TWO_RUNS:-0}"
REPO_ROOT="$(git rev-parse --show-toplevel)"
# shellcheck source=java-env.sh
source "$REPO_ROOT/tools/java-env.sh"

GEN="$REPO_ROOT/reports-demo/generated"
AGENTS="$REPO_ROOT/target/agents"
ASYNC_LIB="$(brew --prefix async-profiler)/lib/libasyncProfiler.dylib"
ARTHAS_HOME="$HOME/.arthas/lib/${ARTHAS_VERSION}/arthas"
# ~30 s : il faut que les appels aient encore lieu quand Arthas se connecte.
ITERATIONS=24000000

cd "$REPO_ROOT"
mvn -q clean package
rm -rf "$GEN"/{jacoco,jacoco-focused,async-profiler,arthas} "$GEN/index.html"
mkdir -p "$GEN"/{jacoco,async-profiler,arthas}

[ -f "$ARTHAS_HOME/arthas-boot.jar" ] || "$REPO_ROOT/tools/arthas/install-offline.sh"
[ -f "$AGENTS/org.jacoco.agent-${JACOCO_VERSION}-runtime.jar" ] || mvn -q dependency:copy \
  -Dartifact="org.jacoco:org.jacoco.agent:${JACOCO_VERSION}:jar:runtime" -DoutputDirectory="$AGENTS"

attach_arthas() {   # $1 = pid
  for batch in watch-params trace-calltree; do
    java -jar "$ARTHAS_HOME/arthas-boot.jar" "$1" --arthas-home "$ARTHAS_HOME" \
         -f "$REPO_ROOT/tools/arthas/${batch}.as" > "$GEN/arthas/${batch}.txt" 2>&1 &
    local boot=$! guard=0
    while [ $guard -lt 5 ] && kill -0 $boot 2>/dev/null; do sleep 3; guard=$((guard+1)); done
    kill $boot 2>/dev/null || true
  done
}

if [ "$TWO_RUNS" = "1" ]; then
  echo "▶ Mode deux passes — exécution 1/2 : couverture + profil non faussé"
  java -javaagent:"$AGENTS/org.jacoco.agent-${JACOCO_VERSION}-runtime.jar"=destfile="$GEN/jacoco/jacoco.exec" \
       -agentpath:"$ASYNC_LIB"=start,event=itimer,interval=1ms,include='lab/sample/*',collapsed,file="$GEN/async-profiler/profil.collapsed" \
       -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints \
       -jar sample-app/target/sample-app.jar > "$GEN/run-mesure.log" 2>&1
  echo "▶ Exécution 2/2 : valeurs des paramètres"
  java -jar sample-app/target/sample-app.jar --iterations $ITERATIONS --hold-seconds 30 \
       > "$GEN/run-inspection.log" 2>&1 &
  APP=$!; trap 'kill $APP 2>/dev/null || true' EXIT; sleep 8
  attach_arthas "$APP"; kill $APP 2>/dev/null || true; trap - EXIT
else
  echo "▶ Passe unique — les trois outils sur la même exécution"
  java -javaagent:"$AGENTS/org.jacoco.agent-${JACOCO_VERSION}-runtime.jar"=destfile="$GEN/jacoco/jacoco.exec" \
       -agentpath:"$ASYNC_LIB"=start,event=itimer,interval=1ms,include='lab/sample/*',collapsed,file="$GEN/async-profiler/profil.collapsed" \
       -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints \
       -jar sample-app/target/sample-app.jar --iterations $ITERATIONS > "$GEN/run.log" 2>&1 &
  APP=$!; trap 'kill $APP 2>/dev/null || true' EXIT; sleep 8
  attach_arthas "$APP"
  echo "▶ Attente de la fin (les agents écrivent à la sortie de la JVM)"
  wait $APP || true; trap - EXIT
fi

# Vues natives d'async-profiler, sur une exécution courte et non perturbée.
for fmt in flamegraph tree; do
  java -agentpath:"$ASYNC_LIB"=start,event=itimer,interval=1ms,include='lab/sample/*',$fmt,file="$GEN/async-profiler/$fmt.html" \
       -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints \
       -jar sample-app/target/sample-app.jar > /dev/null 2>&1
done

echo "▶ Rendu des rapports JaCoCo"
mvn -q "org.jacoco:jacoco-maven-plugin:${JACOCO_VERSION}:report" \
  -Djacoco.dataFile="$GEN/jacoco/jacoco.exec" -pl sample-app
rm -rf "$GEN/jacoco/html"; cp -R sample-app/target/site/jacoco "$GEN/jacoco/html"
"$REPO_ROOT/tools/jacoco/collect-focused.sh" > /dev/null

echo "▶ Contrôle d'intégrité : la couverture a-t-elle survécu à la retransformation d'Arthas ?"
python3 - "$GEN/jacoco/html/jacoco.csv" <<'PY'
import csv, sys
r = next((r for r in csv.DictReader(open(sys.argv[1])) if r["CLASS"] == "RoutePlanner"), None)
if not r:
    sys.exit("   ÉCHEC : RoutePlanner absent du rapport")
n = int(r["INSTRUCTION_COVERED"])
print(f"   RoutePlanner : {n} instructions couvertes — {'OK' if n else 'CORROMPU'}")
PY

echo "▶ Assemblage"
python3 "$REPO_ROOT/tools/summary/build-dashboard.py"
python3 "$REPO_ROOT/tools/summary/build-markdown.py"
