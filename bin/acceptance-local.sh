#!/usr/bin/env bash
# Acceptance run on the jar built from source.
#
# The counterpart of acceptance-published.sh, which tests the PUBLISHED artefact: this one
# tests what we are about to publish. It does not replace the unit tests — those check
# decisions, one at a time, without ever launching an observed JVM. Here the whole chain is
# taken: a real application, the three analysis tools downloaded and injected, the page
# assembled, the exports rewritten, the server writing an annotation beside the
# measurements.
#
# It is the only test that says the pieces, end to end, still work together.
set -uo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="$REPO_DIR/orchestrator/target/runtime-xray.jar"
APP="$REPO_DIR/sample-app/target/sample-app.jar"
SANDBOX="$(mktemp -d)"
ok=0 ko=0
step(){ if [ "$1" = 0 ]; then echo "  ✅ $2"; ok=$((ok+1)); else echo "  ❌ $2"; ko=$((ko+1)); fi; }
server_pid=""
cleanup(){ [ -n "$server_pid" ] && kill "$server_pid" 2>/dev/null; }
trap cleanup EXIT

cd "$SANDBOX" || exit 1
echo "Fresh directory: $SANDBOX"
echo

echo "0. What must have been built"
[ -s "$JAR" ]; step $? "the tool's jar exists (mvn package)"
[ -s "$APP" ]; step $? "the demonstration program exists"
if [ "$ko" != 0 ]; then echo "  → run first: mvn -q package"; exit "$ko"; fi
echo

# The application must live longer than the attach delay, otherwise the values CANNOT be
# captured — and that is not a defect of the tool, it is arithmetic.
echo "1. A complete analysis, on a real application"
java -jar "$JAR" \
  --java "java -jar $APP --iterations 12000000" \
  --root "lab.sample.RoutePlanner::travelTimeMinutes" \
  --sources "$REPO_DIR/sample-app/src/main/java" \
  --name "Recette locale" --attach-after 4 \
  --out out --export all > analysis.log 2>&1
step $? "the analysis ends without error"

grep -q "Classes analysed" analysis.log
step $? "the bytecode is deduced on its own (no --classes)"

[ -s out/index.html ]; step $? "the page is produced"
[ -s out/rapport.md ]; step $? "the Markdown report is produced"
ls out/runs/*/jacoco/html/index.html >/dev/null 2>&1
step $? "the coverage is rendered"
ls out/runs/*/async-profiler/flamegraph.html >/dev/null 2>&1
step $? "the native profile is rendered"
[ -s "$(ls out/runs/*/arthas/watch-params.txt 2>/dev/null | head -1)" ]
step $? "the argument values are captured"
echo

echo "2. The page carries the three pieces of information, not a template"
# The page no longer carries everything in "const D": the big blocks are loaded from vue/,
# on demand. So we check the index AND the blocks it names — that pair is what decides
# whether the page has anything to show.
python3 - <<'PY'
import json, pathlib, re, sys
racine = pathlib.Path("out")
t = (racine / "index.html").read_text()
m = re.search(r'const D = (\{.*?\});\r?\n', t, re.S)
if not m: sys.exit(1)
d = json.loads(m.group(1))
run = d["runs"][0]
blocs = {pathlib.Path(b).name: (racine / b) for b in run.get("blocs", [])}

def porte(nom, minimum):
    f = blocs.get(nom)
    return f is not None and f.is_file() and f.stat().st_size > minimum

missing = []
if not list((racine / "vue" / "sources").glob("*.js")): missing.append("sources")
if len(run.get("methods", {})) < 5:                     missing.append("coverage")
if not porte("arbre.js", 500):                          missing.append("arbre d'appel")
if not porte("valeurs.js", 200):                        missing.append("valeurs")
if not run.get("context"):                              missing.append("contexte")
if run.get("mesures", 0) < 100:                         missing.append("time samples")
print("   missing : " + ", ".join(missing) if missing else "", end="")
sys.exit(1 if missing else 0)
PY
step $? "sources, coverage, call tree, values and context are embedded"
echo

echo "3. The exports open elsewhere"
E="$(ls -d out/runs/*/exports 2>/dev/null | head -1)"
[ -n "$E" ]; step $? "the exports directory is written"
grep -q "cpu-clock:" "$E/profil.perf.txt" 2>/dev/null
step $? "the perf export has the shape of perf script"
head -2 "$E/profil.perf.txt" 2>/dev/null | tail -1 | grep -q "^	"
step $? "its frames are indented, as the reader expects them"
python3 - "$E" <<'PY'
import json, pathlib, sys
p = json.loads((pathlib.Path(sys.argv[1]) / "profil.cpuprofile").read_text())
ids = {n["id"] for n in p["nodes"]}
ok = (p["samples"] and len(p["samples"]) == len(p["timeDeltas"])
      and all(s in ids for s in p["samples"])
      and all(c in ids for n in p["nodes"] for c in n.get("children", [])))
sys.exit(0 if ok else 1)
PY
step $? "the cpuprofile export is coherent (nodes, samples, intervals)"
grep -q "^end_of_record" "$E/couverture.lcov" 2>/dev/null
step $? "the LCOV export is complete"
python3 -c "import json,sys;json.load(open(sys.argv[1]))" "$E/valeurs.json" 2>/dev/null
step $? "the values export is valid JSON"
echo

echo "4. The observation level decides what one pays"
java -jar "$JAR" \
  --java "java -jar $APP --iterations 200000" \
  --level coverage --sources "$REPO_DIR/sample-app/src/main/java" \
  --name "Couverture seule" --out light > light.log 2>&1
step $? "a measurement at the \"coverage\" level ends"
grep -q "no stack sampling" light.log
step $? "the tool announces that it is not sampling"
[ ! -s "$(ls light/runs/*/async-profiler/profil.collapsed 2>/dev/null | head -1)" ]
step $? "no profile is written at this level"
ls light/runs/*/jacoco/html/index.html >/dev/null 2>&1
step $? "the coverage, on the other hand, is there"
echo

# What JaCoCo renders on disk is not what the page shows. Checking that takes a real
# measurement: the coverage must come out identical while the file count collapses.
echo "4 bis. What is written to disk is a setting, and does not touch the measurement"
pct(){ grep -o '"pct":[0-9.]*' "$1"/faits.jsonl | head -1; }
count(){ find "$1"/runs -type f | wc -l; }
for v in detailed data minimal; do
  java -jar "$JAR" \
    --java "java -jar $APP --iterations 200000" \
    --level coverage --sources "$REPO_DIR/sample-app/src/main/java" \
    --jacoco-reports "$v" --name "rapports $v" --out "rep-$v" > "rep-$v.log" 2>&1
  step $? "a measurement with --jacoco-reports $v ends"
  ls rep-$v/runs/*/jacoco/html/jacoco.xml >/dev/null 2>&1
  step $? "  the jacoco.xml is written all the same — it is what the page reads"
  [ "$(pct rep-$v)" = "$(pct light)" ]
  step $? "  the displayed coverage is identical to step 4's"
  [ "$(count rep-$v)" -lt "$(count light)" ]
  step $? "  and the file count went down: $(count light) → $(count rep-$v)"
done
[ ! -e "$(ls -d rep-data/runs/*/jacoco/html/index.html 2>/dev/null | head -1)" ]
step $? "in \"data\", no HTML site is written for the run"
grep -q 'toujours: true' "$REPO_DIR/orchestrator/src/main/resources/lab/xray/dashboard.html"
step $? "and the page still names the absent reports, with their command"
# The whole point of the settings: they give up a RENDERING, never a figure. The count of
# files written per run is in the diagnostic, so the trade is checkable after the fact.
grep -q '"fichiersEcrits"' rep-data/diagnostic.json
step $? "the diagnostic says, run by run, what was left on disk"
grep -q '"conseil":"Exclude' rep-data/diagnostic.json
step $? "and names the directory to exclude from an antivirus scan"
echo

# The only value that also gives up the campaign's merged rendering. It is the one that
# must not take the figure with it: an aggressive setting stays defensible only as long as
# the last possible reading survives it.
echo "4 ter. \"minimal\" gives up the merged SITE, never the merged figure"
java -jar "$JAR" \
  --java "java -jar $APP --iterations 120000" \
  --level coverage --sources "$REPO_DIR/sample-app/src/main/java" \
  --classes "$APP" --jacoco-reports minimal --name "minimal bis" --out rep-minimal \
  > rep-minimal-2.log 2>&1
step $? "a second run under \"minimal\" ends, so there is something to merge"
[ -f rep-minimal/jacoco-fusion/html/jacoco.xml ]
step $? "the merged XML is written"
[ ! -f rep-minimal/jacoco-fusion/html/index.html ]
step $? "and its site is not"
echo

# Gathering the run directories into one file, and — only if asked — putting it in their
# place. Nothing is removed before the archive has been counted against what it replaces.
echo "4 quater. --archive gathers the files, and \"replace\" takes their place"
java -jar "$JAR" \
  --java "java -jar $APP --iterations 120000" \
  --level coverage --sources "$REPO_DIR/sample-app/src/main/java" \
  --classes "$APP" --archive --name "archive kept" --out arch-keep > arch-keep.log 2>&1
step $? "a measurement with --archive ends"
[ -f arch-keep/runs.zip ] && [ -d arch-keep/runs ]
step $? "  the archive is written, and the tree stays: \"keep\" takes nothing away"
[ "$(unzip -l arch-keep/runs.zip | tail -1 | awk '{print $2}')" = "$(find arch-keep/runs -type f | wc -l)" ]
step $? "  and it holds exactly as many entries as there are files"
java -jar "$JAR" \
  --java "java -jar $APP --iterations 120000" \
  --level coverage --sources "$REPO_DIR/sample-app/src/main/java" \
  --classes "$APP" --archive replace --name "archive replaced" --out arch-rep \
  > arch-rep.log 2>&1
step $? "a measurement with --archive replace ends"
[ -f arch-rep/runs.zip ] && [ ! -d arch-rep/runs ]
step $? "  the tree is gone, the archive is there"
[ -f arch-rep/index.html ] && [ -f arch-rep/diagnostic.json ] && [ -f arch-rep/faits.jsonl ]
step $? "  and what is read without the measurements still stands"
echo

# A report is often reassembled without passing the launch options again. What was given to
# the measurement must not get lost on the way — and above all, must not be denied.
echo "4 quinquies. Reassembling without --sources finds the annotated code again"
rm -rf reassemble && cp -r out reassemble
java -jar "$JAR" --report-only --out reassemble > reassemble.log 2>&1
step $? "reassembling without --sources ends"
grep -q "taking back what the run(s) recorded" reassemble.log
step $? "the tool takes back the roots the run had recorded"
! grep -q "no source directory was given" reassemble.log
step $? "and no longer claims that no root had been given"
grep -q '"sourcesDisponibles":{"' reassemble/index.html
step $? "the annotated code is back in the report"
echo

# A configuration file and a command line say different things: the line wins. It used to
# win on nine settings out of twenty-two, and to lose in silence on the others.
echo "4 sexies. A --config beside the options does not swallow them"
cat > both.conf <<CONF
JAVA_CMD="java -jar $APP --iterations 200000"
SOURCE_DIRS="$REPO_DIR/sample-app/src/main/java"
CLASSES_DIR="$APP"
OUT_DIR="both"
LEVEL="full"
JACOCO_REPORTS="full"
SERVE_HOST="127.0.0.1"
CONF
java -jar "$JAR" --config both.conf --level coverage --jacoco-reports data \
  --name "the line wins" > both.log 2>&1
step $? "a measurement with a --config AND options ends"
grep -q "no stack sampling" both.log
step $? "  --level coverage was obeyed, not the file's \"full\""
grep -q "JACOCO_REPORTS=data" both.log
step $? "  and --jacoco-reports data too"
[ "$(count both)" -lt 20 ]
step $? "  which shows on the disk: $(count both) files, not the file's hundreds"

# The same, with the file found by its name instead of named on the line: it is still a
# file, and the options typed beside it were still typed.
mkdir -p implicit
( cd implicit && cat > runtime-xray.conf <<CONF
JAVA_CMD="java -jar $APP --iterations 200000"
SOURCE_DIRS="$REPO_DIR/sample-app/src/main/java"
CLASSES_DIR="$APP"
OUT_DIR="out"
LEVEL="full"
CONF
  java -jar "$JAR" --level coverage --name "implicit" > implicit.log 2>&1 )
step $? "a measurement with the implicit runtime-xray.conf ends"
grep -q "Configuration read from" implicit/implicit.log
step $? "  the file was indeed found by its name"
grep -q "no stack sampling" implicit/implicit.log
step $? "  and --level coverage was obeyed all the same"

# The interface is a setting; putting the tool into listening is a gesture. A file that
# travels — into a repository, a ticket, another machine — must not be able to open a port.
java -jar "$JAR" --config both.conf --report-only > serves-not.log 2>&1
step $? "reading with SERVE_HOST set and no --serve ends instead of listening"
! grep -q "Report served at" serves-not.log
step $? "  nothing was put into listening"
echo

echo "5. The server writes the annotations beside the measurements"
PORT="$(python3 -c 'import socket;s=socket.socket();s.bind(("127.0.0.1",0));print(s.getsockname()[1]);s.close()')"
java -jar "$JAR" --report-only --out out \
  --sources "$REPO_DIR/sample-app/src/main/java" --serve "$PORT" > serveur.log 2>&1 &
server_pid=$!
for _ in $(seq 1 40); do curl -sf "http://127.0.0.1:$PORT/__xray/ping" >/dev/null && break; sleep 0.25; done

curl -sf "http://127.0.0.1:$PORT/__xray/ping" | grep -q '"peutEcrire":true'
step $? "the page learns it may write"

UUID="$(python3 -c "import json,glob;print(json.load(open(glob.glob('out/runs/*/run-context.json')[0]))['uuid'])")"
FINGERPRINT="$(curl -sf "http://127.0.0.1:$PORT/__xray/noms" | python3 -c "import json,sys;print(json.load(sys.stdin)['empreintes'].get('$UUID',''))")"
curl -sf -X POST "http://127.0.0.1:$PORT/__xray/noms/$UUID" \
  -H 'Content-Type: application/json' \
  -d "{\"base\":\"$FINGERPRINT\",\"valeur\":{\"nom\":\"Named by the acceptance run\",\"etiquettes\":{\"acceptance\":\"\"}}}" \
  >/dev/null
step $? "the annotation is accepted"

[ -s "$(ls out/runs/*/config.json 2>/dev/null | head -1)" ]
step $? "it is written INSIDE the run's directory"

# The same write, starting from the previous version: it must be refused, not applied.
code="$(curl -s -o /dev/null -w '%{http_code}' -X POST "http://127.0.0.1:$PORT/__xray/noms/$UUID" \
  -H 'Content-Type: application/json' \
  -d "{\"base\":\"$FINGERPRINT\",\"valeur\":{\"nom\":\"overwrite\"}}")"
[ "$code" = "409" ]; step $? "a write starting from a stale version is refused ($code)"

for _ in $(seq 1 40); do grep -q "Named by the acceptance run" out/index.html && break; sleep 0.25; done
grep -q "Named by the acceptance run" out/index.html
step $? "the page was regenerated with the annotation"

curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PORT/../$(basename "$SANDBOX")" | grep -q '40'
step $? "a path that leaves the served directory is refused"

kill "$server_pid" 2>/dev/null; wait "$server_pid" 2>/dev/null; server_pid=""
echo

echo "6. The same server, guarded by a shared secret"
SECRET="acceptance-$$"
PORT2="$(python3 -c 'import socket;s=socket.socket();s.bind(("127.0.0.1",0));print(s.getsockname()[1]);s.close()')"
java -jar "$JAR" --report-only --out out --serve "$PORT2" --serve-token "$SECRET" \
  > garde.log 2>&1 &
server_pid=$!
for _ in $(seq 1 40); do
  curl -s -o /dev/null "http://127.0.0.1:$PORT2/__xray/ping" && break; sleep 0.25
done

code="$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PORT2/__xray/ping")"
[ "$code" = "401" ]; step $? "without the secret, the page can read nothing ($code)"

# The refusal that matters: nobody writes to the annotation files without the secret.
code="$(curl -s -o /dev/null -w '%{http_code}' -X POST \
  "http://127.0.0.1:$PORT2/__xray/noms/$UUID" -H 'Content-Type: application/json' \
  -d '{"valeur":{"nom":"intrus"}}')"
[ "$code" = "401" ]; step $? "without the secret, no annotation is written ($code)"
grep -q intrus "$(ls out/runs/*/config.json | head -1)" && bogus=1 || bogus=0
[ "$bogus" = 0 ]; step $? "and nothing was written to disk"

code="$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PORT2/")"
[ "$code" = "302" ]; step $? "a browser is sent to the entry page ($code)"

curl -s -H "Authorization: Bearer $SECRET" "http://127.0.0.1:$PORT2/__xray/ping" \
  | grep -q '"garde":true'
step $? "with the secret, a script gets through and knows the door exists"

curl -s -c bocal.txt -o /dev/null -X POST "http://127.0.0.1:$PORT2/__xray/entrer" \
  --data-urlencode "jeton=$SECRET" --data-urlencode "vers=/"
grep -q xray_session bocal.txt
step $? "the form opens a browser session"
grep -q "$SECRET" bocal.txt && leak=1 || leak=0
[ "$leak" = 0 ]; step $? "and that session does not carry the secret itself"

curl -s -b bocal.txt -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PORT2/" | grep -q 200
step $? "with that session, the report opens"
echo

echo "────────────────────────────────────"
echo "  $ok passed · $ko failed"
echo "  output kept: $SANDBOX"
[ "$ko" = 0 ] || tail -25 analysis.log
exit "$ko"
