#!/usr/bin/env bash
# async-profiler — call tree by sampling, rendered as a self-contained HTML flame graph.
#
# Why this tool: it is the only free one that produces, with no server and no account, a
# single navigable HTML (zoom, search, hover) one can send by mail.
set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
# shellcheck source=../java-env.sh
source "$REPO_ROOT/tools/java-env.sh"
OUT="$REPO_ROOT/reports-demo/generated/async-profiler"
LIB="$(brew --prefix async-profiler)/lib/libasyncProfiler.dylib"   # macOS/Homebrew

cd "$REPO_ROOT"
mvn -q clean package
mkdir -p "$OUT"

# itimer: on macOS, perf_events does not exist — it is the sampling mode retained.
# DebugNonSafepoints: without this flag, the frames are attributed to the wrong line number.
for fmt in flamegraph tree; do
  java -agentpath:"$LIB"=start,event=itimer,interval=1ms,include='lab/sample/*',$fmt,file="$OUT/$fmt.html" \
       -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints \
       -jar sample-app/target/sample-app.jar > /dev/null
done

echo "→ flame graph: $OUT/flamegraph.html"
echo "→ call tree: $OUT/tree.html"
