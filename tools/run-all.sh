#!/usr/bin/env bash
# UNE exécution, TROIS outils, un rapport intégré.
#
# Les trois agents cohabitent sur la même JVM :
#   - JaCoCo        -javaagent, instrumente le bytecode au chargement
#   - async-profiler -agentpath, échantillonne les piles
#   - Arthas        s'attache en cours de route et interroge les appels vivants
#
# Le point délicat : Arthas RETRANSFORME les classes qu'il observe, alors que JaCoCo les a
# déjà instrumentées. Le script vérifie donc en fin de course que la couverture n'a pas été
# corrompue — c'est la seule façon d'affirmer que l'intégration tient.
set -euo pipefail

JACOCO_VERSION="0.8.13"
ARTHAS_VERSION="4.3.4"
REPO_ROOT="$(git rev-parse --show-toplevel)"
# shellcheck source=java-env.sh
source "$REPO_ROOT/tools/java-env.sh"

GEN="$REPO_ROOT/reports-demo/generated"
AGENTS="$REPO_ROOT/target/agents"
ASYNC_LIB="$(brew --prefix async-profiler)/lib/libasyncProfiler.dylib"
ARTHAS_HOME="$HOME/.arthas/lib/${ARTHAS_VERSION}/arthas"
# ~30 s de calcul : assez long pour qu'Arthas s'attache PENDANT que les appels ont lieu.
# Avec le calibrage à 10 s, la boucle serait finie avant que la console soit connectée.
ITERATIONS=48000000

cd "$REPO_ROOT"
mvn -q clean package
mkdir -p "$GEN"/{jacoco,async-profiler,arthas}

[ -f "$ARTHAS_HOME/arthas-boot.jar" ] || "$REPO_ROOT/tools/arthas/install-offline.sh"
[ -f "$AGENTS/org.jacoco.agent-${JACOCO_VERSION}-runtime.jar" ] || mvn -q dependency:copy \
  -Dartifact="org.jacoco:org.jacoco.agent:${JACOCO_VERSION}:jar:runtime" -DoutputDirectory="$AGENTS"

echo "▶ Lancement unique, deux agents attachés au démarrage"
java -javaagent:"$AGENTS/org.jacoco.agent-${JACOCO_VERSION}-runtime.jar"=destfile="$GEN/jacoco/jacoco.exec" \
     -agentpath:"$ASYNC_LIB"=start,event=itimer,interval=1ms,include='lab/sample/*',collapsed,file="$GEN/async-profiler/profil.collapsed" \
     -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints \
     -jar sample-app/target/sample-app.jar --iterations $ITERATIONS \
     > "$GEN/run.log" 2>&1 &
APP_PID=$!
trap 'kill $APP_PID 2>/dev/null || true' EXIT
sleep 8

echo "▶ Arthas s'attache pendant que les appels ont lieu"
for batch in watch-params trace-calltree; do
  java -jar "$ARTHAS_HOME/arthas-boot.jar" "$APP_PID" --arthas-home "$ARTHAS_HOME" \
       -f "$REPO_ROOT/tools/arthas/${batch}.as" > "$GEN/arthas/${batch}.txt" 2>&1 &
  boot=$!; guard=0
  while [ $guard -lt 5 ] && kill -0 $boot 2>/dev/null; do sleep 3; guard=$((guard+1)); done
  kill $boot 2>/dev/null || true
done

echo "▶ Attente de la fin du calcul (les agents écrivent à la sortie de la JVM)"
wait $APP_PID || true
trap - EXIT

echo "▶ Rendu des rapports"
mvn -q "org.jacoco:jacoco-maven-plugin:${JACOCO_VERSION}:report" \
  -Djacoco.dataFile="$GEN/jacoco/jacoco.exec" -pl sample-app
rm -rf "$GEN/jacoco/html"; cp -R sample-app/target/site/jacoco "$GEN/jacoco/html"
"$REPO_ROOT/tools/jacoco/collect-focused.sh" > /dev/null

echo "▶ Contrôle d'intégrité : la retransformation d'Arthas a-t-elle abîmé la couverture ?"
python3 - "$GEN/jacoco/html/jacoco.csv" <<'PY'
import csv, sys
rows = list(csv.DictReader(open(sys.argv[1])))
target = [r for r in rows if r["CLASS"] == "RoutePlanner"]
if not target:
    sys.exit("RoutePlanner absent du rapport — couverture corrompue")
r = target[0]
covered = int(r["INSTRUCTION_COVERED"])
print(f"   RoutePlanner : {covered} instructions couvertes"
      f" ({'OK' if covered else 'CORROMPU — Arthas a effacé la couverture'})")
PY
echo "→ tout est dans $GEN"
