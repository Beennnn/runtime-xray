#!/usr/bin/env bash
# JFR (Java Flight Recorder) — inclus dans le JDK, coût zéro, aucune installation.
#
# JEP 520 (JDK 25) ajoute jdk.MethodTiming et jdk.MethodTrace : traçage ciblé d'une
# méthode, avec sa pile d'appel. C'est ce qui rend JFR pertinent ici — avant JDK 25,
# il fallait se contenter de l'échantillonnage.
set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
OUT="$REPO_ROOT/reports-demo/generated/jfr"
TARGET="lab.sample.RoutePlanner::travelTimeMinutes"
# Cible du traçage : la consultation d'horaires, appelée quelques dizaines de fois
# seulement (le reste est en cache). Tracer une méthode du chemin chaud produisait
# 129 Mo d'enregistrement pour 10 s d'exécution — au-dessus de la limite de fichier
# de GitHub, et illisible. Le traçage fin se cible, il ne se saupoudre pas.
TRACED="lab.sample.transfer.Timetable::frequencyMinutes"

cd "$REPO_ROOT"
mvn -q clean package
mkdir -p "$OUT"

java "-XX:StartFlightRecording:jdk.MethodTiming#filter=${TARGET},jdk.MethodTrace#filter=${TRACED},settings=profile,maxsize=12M,filename=$OUT/recording.jfr" \
     -jar sample-app/target/sample-app.jar > "$OUT/run.log" 2>&1

jfr summary "$OUT/recording.jfr"                      > "$OUT/summary.txt"
jfr print --events jdk.MethodTiming "$OUT/recording.jfr" > "$OUT/method-timing.txt"
jfr print --events jdk.MethodTrace  "$OUT/recording.jfr" | head -120 > "$OUT/method-trace-extrait.txt"
jfr print --events jdk.ExecutionSample "$OUT/recording.jfr" | head -200 > "$OUT/execution-sample-extrait.txt"

echo "→ enregistrement : $OUT/recording.jfr (à ouvrir dans JDK Mission Control)"
