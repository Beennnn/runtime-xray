#!/usr/bin/env bash
# The reference scenario, with no licence: ONE run, three tools, one view.
#
#   JaCoCo           -javaagent   : instruments the bytecode at load time
#   async-profiler   -agentpath   : samples the stacks
#   Arthas           attachment   : queries the live calls while it runs
#
# Why a single pass when Arthas disturbs the profile: because what it disturbs is not a
# criterion of the project. Detail and caveats: docs/resultat/solution.md.
#
#   TWO_RUNS=1 ./tools/run-all.sh   splits measurement and inspection into two runs, to
#                                   obtain time percentages that are not skewed.
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
# ~30 s: the calls must still be happening when Arthas connects.
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
  echo "▶ Two-pass mode — run 1/2: coverage + unskewed profile"
  java -javaagent:"$AGENTS/org.jacoco.agent-${JACOCO_VERSION}-runtime.jar"=destfile="$GEN/jacoco/jacoco.exec" \
       -agentpath:"$ASYNC_LIB"=start,event=itimer,interval=1ms,include='lab/sample/*',collapsed,file="$GEN/async-profiler/profil.collapsed" \
       -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints \
       -jar sample-app/target/sample-app.jar > "$GEN/run-mesure.log" 2>&1
  echo "▶ Run 2/2: parameter values"
  java -jar sample-app/target/sample-app.jar --iterations $ITERATIONS --hold-seconds 30 \
       > "$GEN/run-inspection.log" 2>&1 &
  APP=$!; trap 'kill $APP 2>/dev/null || true' EXIT; sleep 8
  attach_arthas "$APP"; kill $APP 2>/dev/null || true; trap - EXIT
else
  echo "▶ Single pass — the three tools on the same run"
  java -javaagent:"$AGENTS/org.jacoco.agent-${JACOCO_VERSION}-runtime.jar"=destfile="$GEN/jacoco/jacoco.exec" \
       -agentpath:"$ASYNC_LIB"=start,event=itimer,interval=1ms,include='lab/sample/*',collapsed,file="$GEN/async-profiler/profil.collapsed" \
       -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints \
       -jar sample-app/target/sample-app.jar --iterations $ITERATIONS > "$GEN/run.log" 2>&1 &
  APP=$!; trap 'kill $APP 2>/dev/null || true' EXIT; sleep 8
  attach_arthas "$APP"
  echo "▶ Waiting for the end (the agents write when the JVM exits)"
  wait $APP || true; trap - EXIT
fi

# async-profiler's native views, on a short and undisturbed run.
for fmt in flamegraph tree; do
  java -agentpath:"$ASYNC_LIB"=start,event=itimer,interval=1ms,include='lab/sample/*',$fmt,file="$GEN/async-profiler/$fmt.html" \
       -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints \
       -jar sample-app/target/sample-app.jar > /dev/null 2>&1
done

echo "▶ Rendering the JaCoCo reports"
mvn -q "org.jacoco:jacoco-maven-plugin:${JACOCO_VERSION}:report" \
  -Djacoco.dataFile="$GEN/jacoco/jacoco.exec" -pl sample-app
rm -rf "$GEN/jacoco/html"; cp -R sample-app/target/site/jacoco "$GEN/jacoco/html"
"$REPO_ROOT/tools/jacoco/collect-focused.sh" > /dev/null

echo "▶ Integrity check: did the coverage survive Arthas's retransformation?"
# JaCoCo's CSV is enough to answer, and awk avoids demanding one more interpreter on the
# analysed machine — that is the point of rewriting the orchestrator in Java.
awk -F, 'NR==1{for(i=1;i<=NF;i++){if($i=="CLASS")c=i; if($i=="INSTRUCTION_COVERED")n=i}}
         $c=="RoutePlanner"{found=1;
           printf "   RoutePlanner: %d instructions covered — %s\n", $n, ($n>0?"OK":"CORRUPTED")}
         END{if(!found){print "   FAILURE: RoutePlanner absent from the report"; exit 1}}' \
    "$GEN/jacoco/html/jacoco.csv"

echo "▶ Assembly"
# The aggregated page and its Markdown equivalent are produced by the orchestrator, not
# here: this script shows each tool's NATIVE invocation, which is its only object.
echo "   → java -jar orchestrator/target/runtime-xray.jar --report-only --out <directory>"
