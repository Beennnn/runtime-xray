#!/usr/bin/env bash
# Arthas — capture of the VALUES passed as parameters, on a running JVM.
#
# It is the panel's only free tool that answers the brief's third need. It is not enabled at
# launch: it attaches to a live process, hence the --hold-seconds.
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

# The application must stay alive for the attachment AND the capture.
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
  # Guard: with no valid PID, arthas-boot waits for interactive input indefinitely.
  while [ $guard -lt 14 ] && kill -0 $boot 2>/dev/null; do sleep 5; guard=$((guard+1)); done
  kill $boot 2>/dev/null || true
}

run_batch watch-params.as "$OUT/watch-params.txt"
# No `stop` in trace-calltree.as: on `trace`, it closes the session before the calls are
# collected. And .as files accept NO comment at all — a line beginning with # is sent as a
# command and comes back with "#: command not found".
run_batch trace-calltree.as "$OUT/trace-calltree.txt"

echo "→ parameter values: $OUT/watch-params.txt"
echo "→ timed call tree: $OUT/trace-calltree.txt"
