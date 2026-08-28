#!/usr/bin/env bash
# JaCoCo — line/branch coverage of a RUN (not of a test suite).
#
# Why the agent and not the Maven plugin: the brief bears on "the lines executed when a
# function is called", hence on a real run. The jacoco-maven-plugin measures only what the
# tests cover — that is not the same question.
set -euo pipefail

JACOCO_VERSION="0.8.13"   # pinned: a floating version would change the report without warning
REPO_ROOT="$(git rev-parse --show-toplevel)"
# shellcheck source=../java-env.sh
source "$REPO_ROOT/tools/java-env.sh"
OUT="$REPO_ROOT/reports-demo/generated/jacoco"
AGENT_DIR="$REPO_ROOT/target/agents"

cd "$REPO_ROOT"
mvn -q clean package

mvn -q dependency:copy \
  -Dartifact="org.jacoco:org.jacoco.agent:${JACOCO_VERSION}:jar:runtime" \
  -DoutputDirectory="$AGENT_DIR"
AGENT="$AGENT_DIR/org.jacoco.agent-${JACOCO_VERSION}-runtime.jar"

mkdir -p "$OUT"
java -javaagent:"$AGENT"=destfile="$OUT/jacoco.exec" \
     -jar sample-app/target/sample-app.jar

# The report goal writes into the module's target/site/jacoco; -Djacoco.outputDirectory is
# NOT honoured (verified: the report lands in target/site anyway). So we copy explicitly
# rather than trusting a phantom property.
mvn -q "org.jacoco:jacoco-maven-plugin:${JACOCO_VERSION}:report" \
  -Djacoco.dataFile="$OUT/jacoco.exec" \
  -pl sample-app

rm -rf "$OUT/html"
cp -R sample-app/target/site/jacoco "$OUT/html"

echo "→ report: $OUT/html/index.html"
