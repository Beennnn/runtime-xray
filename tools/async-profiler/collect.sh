#!/usr/bin/env bash
# async-profiler — arbre d'appel par échantillonnage, rendu en flame graph HTML autonome.
#
# Pourquoi cet outil : c'est le seul gratuit qui produit, sans serveur ni compte, un
# HTML unique navigable (zoom, recherche, survol) qu'on peut envoyer par mail.
set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
# shellcheck source=../java-env.sh
source "$REPO_ROOT/tools/java-env.sh"
OUT="$REPO_ROOT/reports-demo/generated/async-profiler"
LIB="$(brew --prefix async-profiler)/lib/libasyncProfiler.dylib"   # macOS/Homebrew

cd "$REPO_ROOT"
mvn -q clean package
mkdir -p "$OUT"

# itimer : sur macOS, perf_events n'existe pas — c'est le mode d'échantillonnage retenu.
# DebugNonSafepoints : sans ce flag, les frames sont attribuées au mauvais numéro de ligne.
for fmt in flamegraph tree; do
  java -agentpath:"$LIB"=start,event=itimer,interval=1ms,include='lab/sample/*',$fmt,file="$OUT/$fmt.html" \
       -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints \
       -jar sample-app/target/sample-app.jar > /dev/null
done

echo "→ flame graph : $OUT/flamegraph.html"
echo "→ arbre d'appel : $OUT/tree.html"
