#!/usr/bin/env bash
# Compile tous les modules puis exécute le générateur de rapport de chacun.
# Convention : chaque module tools/<nom> expose une classe main définie par
# la propriété Maven "exec.mainClass" dans son pom.xml.
set -euo pipefail

cd "$(dirname "$0")/.."

echo "== Build de tous les modules =="
mvn -q clean package

echo "== Génération des rapports =="
for module_pom in tools/*/pom.xml; do
  module_dir="$(dirname "$module_pom")"
  module_name="$(basename "$module_dir")"
  main_class="$(mvn -q -f "$module_pom" help:evaluate -Dexpression=exec.mainClass -DforceStdout)"

  echo "-- ${module_name} (${main_class}) --"
  jar_file="$(find "${module_dir}/target" -maxdepth 1 -name '*.jar' ! -name '*-sources.jar' | head -n1)"

  if [ -z "${jar_file}" ]; then
    echo "   [!] Aucun jar trouvé pour ${module_name}, module ignoré."
    continue
  fi

  java -cp "${jar_file}" "${main_class}"
done

echo "== Terminé. Rapports disponibles dans reports-demo/generated/ =="
