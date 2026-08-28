#!/usr/bin/env bash
# Acceptance run on the artefact published to Maven Central.
#
# We do not check that the jar downloads — an HTTP 200 says that. We check that it WORKS:
# fetched from Central, in a fresh directory, with nothing from the development repository,
# and that it really produces a report on some application.
#
# It is the only test that tells "published" apart from "usable".
set -uo pipefail

VERSION="${1:-1.0.0}"
REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE="https://repo1.maven.org/maven2/io/github/beennnn/runtime-xray-cli/$VERSION"
JAR="runtime-xray-cli-$VERSION.jar"
SANDBOX="$(mktemp -d)"
APP="$REPO_DIR/sample-app/target/sample-app.jar"
ok=0 ko=0 na=0
step(){ if [ "$1" = 0 ]; then echo "  ✅ $2"; ok=$((ok+1)); else echo "  ❌ $2"; ko=$((ko+1)); fi; }
# A version published before a feature cannot carry it: counting that as a failure would
# make a sound acceptance run look like a failed one. We say so, without counting against.
outOfScope(){ echo "  ○ $1 — ${2:-not in version $VERSION}"; na=$((na+1)); }
# What the downloaded jar can do, read from its own help rather than deduced from the
# version number: the only source that does not get it wrong.
knows(){ grep -q -- "$1" help.txt; }

cd "$SANDBOX" || exit 1
echo "Fresh directory: $SANDBOX"
echo

echo "1. Fetching from Maven Central"
curl -sf -O "$BASE/$JAR";              step $? "the jar downloads"
curl -sf -O "$BASE/$JAR.asc";          step $? "its signature downloads"
curl -sf -O "$BASE/runtime-xray-cli-$VERSION-sources.jar"; step $? "the sources are there"
curl -sf -O "$BASE/runtime-xray-cli-$VERSION-javadoc.jar"; step $? "the javadoc is there"
echo

echo "2. Signature — the key is fetched from a public server, as a third party would"
# Without the key, NOTHING can be concluded: neither that the signature is good, nor that
# it is bad. A network that blocks key servers — common in a company — must not make a
# valid artefact look like a doubtful one.
if gpg --keyserver keys.openpgp.org --recv-keys 8D181AA1F3545E3C43804355D7D3E62B52C66FCA >/dev/null 2>&1
then
  gpg --status-fd 1 --verify "$JAR.asc" "$JAR" 2>/dev/null | grep -q GOODSIG
  step $? "the jar's signature is valid"
else
  outOfScope "signature check" "key not reachable from this network: nothing can be \
concluded, either way"
fi
echo

echo "3. The jar runs"
java -jar "$JAR" --help > help.txt 2>&1
step $? "the help shows"
echo

echo "4. It really analyses an application"
java -jar "$JAR" \
  --java "java -jar $APP --iterations 400000" \
  --root "lab.sample.RoutePlanner::travelTimeMinutes" \
  --sources "$REPO_DIR/sample-app/src/main/java" \
  --name "Central acceptance" --out out > analysis.log 2>&1
step $? "the analysis ends without error"

# No accent: this check reads the output of ANOTHER version, produced on a machine whose
# encoding we do not choose. Looking for an accented word would fail a sound version whose
# console is ASCII.
grep -q "Classes analys" analysis.log
step $? "the bytecode is deduced on its own (no --classes)"

[ -s out/index.html ];        step $? "the page is produced"
[ -s out/rapport.md ];        step $? "the Markdown report is produced"
ls out/runs/*/jacoco/html/index.html >/dev/null 2>&1
step $? "the coverage is rendered"
ls out/runs/*/async-profiler/flamegraph.html >/dev/null 2>&1
step $? "the native profile is rendered"

# The page must hold data, not just the template.
python3 - <<'PY'
import re, pathlib, sys
p = pathlib.Path("out/index.html")
t = p.read_text()
m = re.search(r'const D = (\{.*?\});\r?\n', t, re.S)
sys.exit(0 if m and len(m.group(1)) > 10000 and '"runs"' in m.group(1) else 1)
PY
step $? "the page holds the run's data, not an empty template"
echo

echo "5. What this version can do on top"
if knows "--export"; then
  java -jar "$JAR" --report-only --out out --export all >> analysis.log 2>&1
  step $? "the exports are produced"
  ls out/runs/*/exports/couverture.lcov >/dev/null 2>&1
  step $? "the coverage is rewritten as LCOV"
  ls out/runs/*/exports/profil.cpuprofile >/dev/null 2>&1
  step $? "the profile is rewritten as cpuprofile"
else
  outOfScope "rewriting the measurements for other tools (--export)"
fi

# Both names, and that is not belt and braces: an old published version only ever knew
# "--niveau", while the help of a recent one documents "--level" alone. Probing one name
# would report this step out of scope on half the versions, silently.
if knows "--level" || knows "--niveau"; then
  java -jar "$JAR" --java "java -jar $APP --iterations 200000" --niveau couverture \
    --out leger > leger.log 2>&1
  step $? "a measurement at the \"coverage\" level ends"
  [ ! -s "$(ls leger/runs/*/async-profiler/profil.collapsed 2>/dev/null | head -1)" ]
  step $? "it writes no profile, as announced"
else
  outOfScope "observation levels (--level)"
fi

if knows "--serve"; then
  PORT="$(python3 -c 'import socket;s=socket.socket();s.bind(("127.0.0.1",0));print(s.getsockname()[1]);s.close()')"
  java -jar "$JAR" --report-only --out out --serve "$PORT" > server.log 2>&1 &
  pid=$!
  for _ in $(seq 1 40); do curl -sf "http://127.0.0.1:$PORT/__xray/ping" >/dev/null && break; sleep 0.25; done
  curl -sf "http://127.0.0.1:$PORT/__xray/ping" | grep -q '"peutEcrire":true'
  step $? "the report is served, and the page may write to it"
  kill "$pid" 2>/dev/null; wait "$pid" 2>/dev/null
else
  outOfScope "served report and written annotations (--serve)"
fi

if knows "--serve-token"; then
  PORT="$(python3 -c 'import socket;s=socket.socket();s.bind(("127.0.0.1",0));print(s.getsockname()[1]);s.close()')"
  java -jar "$JAR" --report-only --out out --serve "$PORT" --serve-token acceptance-secret \
    > guard.log 2>&1 &
  pid=$!
  for _ in $(seq 1 40); do
    curl -s -o /dev/null "http://127.0.0.1:$PORT/__xray/ping" && break; sleep 0.25
  done
  code="$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PORT/__xray/ping")"
  [ "$code" = "401" ]; step $? "without the secret, the served report does not open ($code)"
  curl -s -H "Authorization: Bearer acceptance-secret" "http://127.0.0.1:$PORT/__xray/ping" \
    | grep -q '"garde":true'
  step $? "with the secret, it opens"
  kill "$pid" 2>/dev/null; wait "$pid" 2>/dev/null
else
  outOfScope "shared secret on the served report (--serve-token)"
fi
echo

echo "────────────────────────────────────"
echo "  $ok passed · $ko failed · $na out of scope for this version"
echo "  output kept: $SANDBOX"
[ "$ko" = 0 ] || tail -20 analysis.log
exit "$ko"
