#!/usr/bin/env bash
# Arthas — capture des VALEURS passées en paramètres, sur une JVM en cours d'exécution.
#
# C'est le seul outil gratuit du panel qui réponde au troisième besoin du brief. Il ne
# s'active pas au lancement : il s'attache à un process vivant, d'où le --hold-seconds.
set -euo pipefail

ARTHAS_VERSION="4.3.4"
REPO_ROOT="$(git rev-parse --show-toplevel)"
# shellcheck source=../java-env.sh
source "$REPO_ROOT/tools/java-env.sh"
OUT="$REPO_ROOT/reports-demo/generated/arthas"
ARTHAS_HOME="$HOME/.arthas/lib/${ARTHAS_VERSION}/arthas"

cd "$REPO_ROOT"
[ -f "$ARTHAS_HOME/arthas-boot.jar" ] || "$REPO_ROOT/tools/arthas/install-offline.sh"

mvn -q clean package
mkdir -p "$OUT"

# L'application doit rester vivante le temps de l'attachement ET de la capture.
java -jar sample-app/target/sample-app.jar --iterations 40000000 --hold-seconds 60 \
  > "$OUT/app.log" 2>&1 &
APP_PID=$!
trap 'kill $APP_PID 2>/dev/null || true' EXIT
sleep 6

run_batch() {
  local batch="$1" out="$2" guard=0
  java -jar "$ARTHAS_HOME/arthas-boot.jar" "$APP_PID" \
      --arthas-home "$ARTHAS_HOME" \
      -f "$REPO_ROOT/tools/arthas/$batch" > "$out" 2>&1 &
  local boot=$!
  # Garde-fou : sans PID valide, arthas-boot attend une saisie interactive indéfiniment.
  while [ $guard -lt 14 ] && kill -0 $boot 2>/dev/null; do sleep 5; guard=$((guard+1)); done
  kill $boot 2>/dev/null || true
}

run_batch watch-params.as "$OUT/watch-params.txt"
# Pas de `stop` dans trace-calltree.as : sur `trace`, il ferme la session avant que les
# appels soient collectés. Et les fichiers .as n'acceptent AUCUN commentaire — une ligne
# commençant par # est envoyée comme commande et renvoie « #: command not found ».
run_batch trace-calltree.as "$OUT/trace-calltree.txt"

echo "→ valeurs des paramètres : $OUT/watch-params.txt"
echo "→ arbre d'appel chronométré : $OUT/trace-calltree.txt"
