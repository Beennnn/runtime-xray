#!/usr/bin/env bash
# Résolution du JDK utilisé par tous les collecteurs.
#
# Le projet évalue DEUX plateformes : Java 21 (l'existant) et Java 25 (cible possible).
# JAVA_TARGET choisit laquelle — 21 par défaut, car c'est la plateforme réelle aujourd'hui.
#
#   JAVA_TARGET=25 ./tools/jfr/collect.sh
#
# Fixer explicitement la version est indispensable : prendre "le java du PATH" reviendrait
# à documenter des capacités que la plateforme cible n'a pas — cas vécu avec le traçage de
# méthodes JFR (JEP 520), présent en 25 et absent en 21.
JAVA_TARGET="${JAVA_TARGET:-21}"

for candidate in \
  "/opt/homebrew/opt/openjdk@${JAVA_TARGET}/libexec/openjdk.jdk/Contents/Home" \
  "/opt/homebrew/Cellar/openjdk/${JAVA_TARGET}"*/libexec/openjdk.jdk/Contents/Home \
  "$HOME/.sdkman/candidates/java/${JAVA_TARGET}"* ; do
  if [ -x "$candidate/bin/java" ]; then
    export JAVA_HOME="$candidate"
    export PATH="$JAVA_HOME/bin:$PATH"
    break
  fi
done
JAVA_MAJOR="$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')"
export JAVA_MAJOR
echo "JDK utilisé : $(java -version 2>&1 | head -1)"
