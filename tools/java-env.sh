#!/usr/bin/env bash
# Résolution du JDK utilisé par tous les collecteurs.
#
# La cible du projet est **Java 21** (contrainte). Les sorties versionnées doivent donc
# être produites sous 21, pas sous le JDK le plus récent installé sur le poste : sinon on
# documente des capacités que la plateforme cible n'a pas — cas vécu avec le traçage de
# méthodes JFR (JEP 520), disponible en 25 et absent en 21.
for candidate in \
  "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home" \
  "$HOME/.sdkman/candidates/java/21"* ; do
  if [ -x "$candidate/bin/java" ]; then
    export JAVA_HOME="$candidate"
    export PATH="$JAVA_HOME/bin:$PATH"
    break
  fi
done
JAVA_MAJOR="$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')"
export JAVA_MAJOR
echo "JDK utilisé : $(java -version 2>&1 | head -1)"
