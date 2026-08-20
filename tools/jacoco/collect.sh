#!/usr/bin/env bash
# JaCoCo — couverture de lignes/branches d'une EXÉCUTION (pas d'une suite de tests).
#
# Pourquoi l'agent et non le plugin Maven : le brief porte sur « les lignes exécutées
# à l'appel d'une fonction », donc sur un run réel. Le plugin jacoco-maven-plugin ne
# mesure que ce que les tests couvrent — ce n'est pas la même question.
set -euo pipefail

JACOCO_VERSION="0.8.13"   # épinglé : une version flottante changerait le rapport sans prévenir
REPO_ROOT="$(git rev-parse --show-toplevel)"
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

# Le goal report écrit dans target/site/jacoco du module ; -Djacoco.outputDirectory
# n'est PAS honoré (vérifié : le rapport atterrit quand même dans target/site).
# On copie donc explicitement plutôt que de faire confiance à une propriété fantôme.
mvn -q "org.jacoco:jacoco-maven-plugin:${JACOCO_VERSION}:report" \
  -Djacoco.dataFile="$OUT/jacoco.exec" \
  -pl sample-app

rm -rf "$OUT/html"
cp -R sample-app/target/site/jacoco "$OUT/html"

echo "→ rapport : $OUT/html/index.html"
