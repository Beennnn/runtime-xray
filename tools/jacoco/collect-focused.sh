#!/usr/bin/env bash
# FOCUSED report: only the classes ACTUALLY executed.
#
# Why: on a real project, a coverage report lists thousands of classes, the vast majority of
# which have nothing to do with the run being analysed. Keeping only the classes touched
# turns an inventory into an analysis file.
#
# No home-made tool: we use JaCoCo's native mechanism (its CLI's `--classfiles`), presenting
# it only with the classes having at least one covered instruction. The list is read from the
# CSV JaCoCo itself produced.
set -euo pipefail

JACOCO_VERSION="0.8.13"
REPO_ROOT="$(git rev-parse --show-toplevel)"
source "$REPO_ROOT/tools/java-env.sh"
FULL="$REPO_ROOT/reports-demo/generated/jacoco"
OUT="$REPO_ROOT/reports-demo/generated/jacoco-focused"
STAGE="$REPO_ROOT/target/focused-classes"

cd "$REPO_ROOT"
[ -f "$FULL/jacoco.exec" ] || { echo "Run tools/jacoco/collect.sh first"; exit 1; }

CLI="$REPO_ROOT/target/agents/org.jacoco.cli-${JACOCO_VERSION}-nodeps.jar"
[ -f "$CLI" ] || mvn -q dependency:copy \
  -Dartifact="org.jacoco:org.jacoco.cli:${JACOCO_VERSION}:jar:nodeps" \
  -DoutputDirectory="$REPO_ROOT/target/agents"

# Classes with at least one covered instruction, according to the CSV JaCoCo produced.
rm -rf "$STAGE" "$OUT"; mkdir -p "$STAGE" "$OUT"
kept=0; dropped=0
while IFS=, read -r _group package class im ic _rest; do
  [ "$package" = "PACKAGE" ] && continue
  src="sample-app/target/classes/${package//.//}/${class}.class"
  [ -f "$src" ] || continue
  if [ "${ic:-0}" -gt 0 ]; then
    mkdir -p "$STAGE/${package//.//}"
    cp "$src" "$STAGE/${package//.//}/"
    kept=$((kept+1))
  else
    dropped=$((dropped+1))
    echo "  dropped (never executed): ${package}.${class}"
  fi
done < "$FULL/html/jacoco.csv"

java -jar "$CLI" report "$FULL/jacoco.exec" \
  --classfiles "$STAGE" \
  --sourcefiles sample-app/src/main/java \
  --html "$OUT/html" --csv "$OUT/jacoco.csv" \
  --name "Runtime X-Ray — code actually executed" --quiet

echo "→ $kept classes kept, $dropped dropped"
echo "→ focused report: $OUT/html/index.html"
