#!/usr/bin/env bash
# Rapport CIBLÉ : uniquement les classes RÉELLEMENT exécutées.
#
# Pourquoi : sur un vrai projet, un rapport de couverture liste des milliers de classes
# dont l'immense majorité n'a rien à voir avec l'exécution qu'on analyse. Ne garder que
# les classes touchées, c'est passer d'un inventaire à un dossier d'analyse.
#
# Aucun outil maison : on utilise le mécanisme natif de JaCoCo (`--classfiles` de sa CLI),
# en ne lui présentant que les classes ayant au moins une instruction couverte. La liste
# est lue dans le CSV que JaCoCo a lui-même produit.
set -euo pipefail

JACOCO_VERSION="0.8.13"
REPO_ROOT="$(git rev-parse --show-toplevel)"
source "$REPO_ROOT/tools/java-env.sh"
FULL="$REPO_ROOT/reports-demo/generated/jacoco"
OUT="$REPO_ROOT/reports-demo/generated/jacoco-focused"
STAGE="$REPO_ROOT/target/focused-classes"

cd "$REPO_ROOT"
[ -f "$FULL/jacoco.exec" ] || { echo "Lance d'abord tools/jacoco/collect.sh"; exit 1; }

CLI="$REPO_ROOT/target/agents/org.jacoco.cli-${JACOCO_VERSION}-nodeps.jar"
[ -f "$CLI" ] || mvn -q dependency:copy \
  -Dartifact="org.jacoco:org.jacoco.cli:${JACOCO_VERSION}:jar:nodeps" \
  -DoutputDirectory="$REPO_ROOT/target/agents"

# Classes avec au moins une instruction couverte, d'après le CSV produit par JaCoCo.
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
    echo "  écartée (jamais exécutée) : ${package}.${class}"
  fi
done < "$FULL/html/jacoco.csv"

java -jar "$CLI" report "$FULL/jacoco.exec" \
  --classfiles "$STAGE" \
  --sourcefiles sample-app/src/main/java \
  --html "$OUT/html" --csv "$OUT/jacoco.csv" \
  --name "Runtime X-Ray — code réellement exécuté" --quiet

echo "→ $kept classes retenues, $dropped écartées"
echo "→ rapport ciblé : $OUT/html/index.html"
