#!/usr/bin/env bash
# JFR (Java Flight Recorder) — inclus dans le JDK, coût zéro, aucune installation.
#
# JEP 520 (JDK 25) ajoute jdk.MethodTiming et jdk.MethodTrace : traçage ciblé d'une
# méthode, avec sa pile d'appel. C'est ce qui rend JFR pertinent ici — avant JDK 25,
# il fallait se contenter de l'échantillonnage.
set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
# shellcheck source=../java-env.sh
source "$REPO_ROOT/tools/java-env.sh"
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

# jdk.MethodTiming / jdk.MethodTrace = JEP 520, livré avec le JDK 25.
# Sous Java 21 (la cible du projet) ces réglages N'EXISTENT PAS : la JVM émet
# « The .jfc option/setting 'jdk.MethodTiming#filter' doesn't exist. » et n'enregistre
# que l'échantillonnage. On adapte la ligne de commande au lieu de faire semblant.
if [ "${JAVA_MAJOR:-21}" -ge 25 ]; then
  REC_OPTS="jdk.MethodTiming#filter=${TARGET},jdk.MethodTrace#filter=${TRACED},settings=profile,maxsize=12M"
  echo "JEP 520 disponible : traçage de méthodes activé"
else
  REC_OPTS="settings=profile,maxsize=12M"
  echo "JEP 520 indisponible sous Java ${JAVA_MAJOR} : échantillonnage seul"
fi

java "-XX:StartFlightRecording:${REC_OPTS},filename=$OUT/recording.jfr" \
     -jar sample-app/target/sample-app.jar > "$OUT/run.log" 2>&1

jfr summary "$OUT/recording.jfr"                      > "$OUT/summary.txt"
if [ "${JAVA_MAJOR:-21}" -ge 25 ]; then
  jfr print --events jdk.MethodTiming "$OUT/recording.jfr" > "$OUT/method-timing.txt"
  jfr print --events jdk.MethodTrace  "$OUT/recording.jfr" | head -120 > "$OUT/method-trace-extrait.txt"
fi
jfr print --events jdk.ExecutionSample "$OUT/recording.jfr" | head -200 > "$OUT/execution-sample-extrait.txt"

echo "→ enregistrement : $OUT/recording.jfr (à ouvrir dans JDK Mission Control)"
