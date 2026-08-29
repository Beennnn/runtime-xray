#!/usr/bin/env bash
# JFR (Java Flight Recorder) — included in the JDK, zero cost, no installation.
#
# JEP 520 (JDK 25) adds jdk.MethodTiming and jdk.MethodTrace: targeted tracing of a method,
# with its call stack. That is what makes JFR relevant here — before JDK 25, one had to make
# do with sampling.
set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
# shellcheck source=../java-env.sh
source "$REPO_ROOT/tools/java-env.sh"
OUT="$REPO_ROOT/reports-demo/generated/jfr"
TARGET="lab.sample.RoutePlanner::travelTimeMinutes"
# The tracing target: the timetable lookup, called a few dozen times only (the rest is
# cached). Tracing a method of the hot path produced 129 MB of recording for 10 s of running
# — above GitHub's file limit, and unreadable. Fine tracing is targeted, it is not sprinkled
# about.
TRACED="lab.sample.transfer.Timetable::frequencyMinutes"

cd "$REPO_ROOT"
mvn -q clean package
mkdir -p "$OUT"

# jdk.MethodTiming / jdk.MethodTrace = JEP 520, shipped with JDK 25.
# Under Java 21 (the project's target) those settings DO NOT EXIST: the JVM emits
# "The .jfc option/setting 'jdk.MethodTiming#filter' doesn't exist." and records only the
# sampling. We adapt the command line instead of pretending.
if [ "${JAVA_MAJOR:-21}" -ge 25 ]; then
  REC_OPTS="jdk.MethodTiming#filter=${TARGET},jdk.MethodTrace#filter=${TRACED},settings=profile,maxsize=12M"
  echo "JEP 520 available: method tracing enabled"
else
  REC_OPTS="settings=profile,maxsize=12M"
  echo "JEP 520 unavailable under Java ${JAVA_MAJOR}: sampling only"
fi

java "-XX:StartFlightRecording:${REC_OPTS},filename=$OUT/recording.jfr" \
     -jar sample-app/target/sample-app.jar > "$OUT/run.log" 2>&1

jfr summary "$OUT/recording.jfr"                      > "$OUT/summary.txt"
if [ "${JAVA_MAJOR:-21}" -ge 25 ]; then
  jfr print --events jdk.MethodTiming "$OUT/recording.jfr" > "$OUT/method-timing.txt"
  jfr print --events jdk.MethodTrace  "$OUT/recording.jfr" | head -120 > "$OUT/method-trace-excerpt.txt"
fi
jfr print --events jdk.ExecutionSample "$OUT/recording.jfr" | head -200 > "$OUT/execution-sample-excerpt.txt"

echo "→ recording: $OUT/recording.jfr (to be opened in JDK Mission Control)"
