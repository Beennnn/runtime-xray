#!/usr/bin/env bash
# Assembles the package that reproduces the published demonstration, locally, on a machine
# that has neither this repository nor a network.
#
# The offline kit next door answers a different question: it carries the tool towards an
# application one already has. This one carries an application TOO — the one the demo is
# made of — so that somebody can run the campaign, open the report, and compare it with
# https://beennnn.github.io/runtime-xray/multi/ without having anything to build.
#
# The three runs are the demo's own: same names, same root methods, same filters. Only the
# iteration counts are overridable, because they decide how long one waits and nothing else.
#
#   bin/demo-kit.sh
set -uo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KIT="$REPO_DIR/target/demo-kit/runtime-xray-demo"
ZIP="$REPO_DIR/target/runtime-xray-demo.zip"
VERSION="$(grep -m1 '<version>' "$REPO_DIR/pom.xml" | sed 's/.*<version>\(.*\)<\/version>.*/\1/')"

echo "Demo kit — runtime-xray $VERSION"

# The COMPLETE edition, and not the ordinary jar: the point of this package is that one
# unzips it and it runs. The ordinary jar would go looking for its three components on a
# Maven repository at the first launch, which is exactly the machine one does not have.
(cd "$REPO_DIR" && mvn -q -Pcomplet -DskipTests package) \
  || { echo "  ❌ building the complete edition" >&2; exit 1; }

APP_JAR="$REPO_DIR/sample-app/target/sample-app.jar"
TOOL_JAR="$REPO_DIR/orchestrator/target/runtime-xray-complet.jar"
for f in "$APP_JAR" "$TOOL_JAR"; do
  [ -f "$f" ] || { echo "  ❌ missing: $f" >&2; exit 1; }
done

rm -rf "$REPO_DIR/target/demo-kit"
mkdir -p "$KIT/libs" "$KIT/src"

cp "$TOOL_JAR" "$KIT/runtime-xray.jar"
cp "$APP_JAR"  "$KIT/sample-app.jar"
# sample-app.jar names them in its manifest's Class-Path: without them it does not start.
cp "$REPO_DIR"/sample-app/target/libs/*.jar "$KIT/libs/"
# The sources are what the code panel displays. Without them the report opens, measures
# everything, and shows "Source unavailable" on every class — which is the very failure
# this tool exists to explain, and a poor thing to demonstrate.
cp -r "$REPO_DIR/sample-app/src/main/java/." "$KIT/src/"

printf '   %-28s%s\n' "runtime-xray.jar" "$(( $(wc -c < "$KIT/runtime-xray.jar") / 1024 / 1024 )) MB, components embedded"
printf '   %-28s%s\n' "sample-app.jar + libs/" "the observed application"
printf '   %-28s%s\n' "src/" "$(find "$KIT/src" -name '*.java' | wc -l | tr -d ' ') source files"

# ------------------------------------------------------------------ the campaign
#
# Written twice rather than once in a portable form: the two shells share no syntax worth
# the trouble, and a reader on either system must be able to read what is about to run.

cat > "$KIT/demo.sh" <<'EOF'
#!/usr/bin/env bash
# Reproduces the published demonstration: three runs of the same application, observed
# differently, accumulated into one report.
#
#   ./demo.sh                 the three runs, some two minutes
#   ITERATIONS=4000000 ./demo.sh    shorter, same shape
#   OUT=elsewhere ./demo.sh
set -euo pipefail
cd "$(dirname "$0")"

OUT="${OUT:-report}"
ITERATIONS="${ITERATIONS:-24000000}"
SHORT=$(( ITERATIONS / 3 ))

# The application's own classes, and them alone. Adding libs/commons-lang3.jar here — which
# the application does reach — puts its 231 classes into the coverage, none of which has its
# source in this package: the report then opens on "Source unavailable" 231 times over,
# which is the very failure this tool exists to explain and a poor thing to demonstrate.
COMMON=(--sources src --classes sample-app.jar --out "$OUT")

# Values are captured by attaching to the live JVM: attach after it has ended and there is
# nothing to inspect. The default of 8 s suits the full workload; a shortened run needs a
# shortened delay, or one silently loses a third of what the report shows.
attach_for(){ local s=$(( $1 / 3000000 )); [ "$s" -lt 2 ] && s=2; [ "$s" -gt 8 ] && s=8; echo "$s"; }

run(){ # name  root method  filter  iterations
  echo
  echo "▶ $1"
  java -jar runtime-xray.jar \
       --java "java -jar sample-app.jar --iterations $4" \
       --attach-after "$(attach_for "$4")" \
       --name "$1" --root "$2" --filter "$3" "${COMMON[@]}"
}

run "Scenario 1"    "lab.sample.RoutePlanner::travelTimeMinutes"  "lab/sample/*"          "$SHORT"
run "Scenario 2"    "lab.sample.terrain.Terrain::slowdownFactor"  "lab/sample/terrain/*"  "$ITERATIONS"
run "Full scenario" "lab.sample.RoutePlanner::travelTimeMinutes"  "lab/sample/*"          "$ITERATIONS"

echo
echo "Report: $OUT/index.html"
echo "Compare with https://beennnn.github.io/runtime-xray/multi/"
EOF
chmod +x "$KIT/demo.sh"

cat > "$KIT/demo.cmd" <<'EOF'
@echo off
rem Reproduces the published demonstration: three runs of the same application, observed
rem differently, accumulated into one report.
rem
rem   demo.cmd                       the three runs
rem   set ITERATIONS=4000000 & demo.cmd
setlocal
cd /d "%~dp0"

if "%OUT%"=="" set OUT=report
if "%ITERATIONS%"=="" set ITERATIONS=24000000
set /a SHORT=%ITERATIONS%/3

rem The application's own classes and them alone: adding libs\commons-lang3.jar would put
rem its 231 source-less classes into the coverage of a package meant to demonstrate.
set COMMON=--sources src --classes sample-app.jar --out "%OUT%"

rem Values are captured by attaching to the live JVM. The default delay of 8 s suits the
rem full workload; a shortened run needs a shortened delay or the values are lost.
set /a ATTACH=%ITERATIONS%/3000000
if %ATTACH% LSS 2 set ATTACH=2
if %ATTACH% GTR 8 set ATTACH=8
set /a ATTACH_SHORT=%SHORT%/3000000
if %ATTACH_SHORT% LSS 2 set ATTACH_SHORT=2
if %ATTACH_SHORT% GTR 8 set ATTACH_SHORT=8

echo.
echo ^> Scenario 1
java -jar runtime-xray.jar --java "java -jar sample-app.jar --iterations %SHORT%" ^
     --attach-after %ATTACH_SHORT% ^
     --name "Scenario 1" --root "lab.sample.RoutePlanner::travelTimeMinutes" ^
     --filter "lab/sample/*" %COMMON% || exit /b 1

echo.
echo ^> Scenario 2
java -jar runtime-xray.jar --java "java -jar sample-app.jar --iterations %ITERATIONS%" ^
     --attach-after %ATTACH% ^
     --name "Scenario 2" --root "lab.sample.terrain.Terrain::slowdownFactor" ^
     --filter "lab/sample/terrain/*" %COMMON% || exit /b 1

echo.
echo ^> Full scenario
java -jar runtime-xray.jar --java "java -jar sample-app.jar --iterations %ITERATIONS%" ^
     --attach-after %ATTACH% ^
     --name "Full scenario" --root "lab.sample.RoutePlanner::travelTimeMinutes" ^
     --filter "lab/sample/*" %COMMON% || exit /b 1

echo.
echo Report: %OUT%\index.html
echo Compare with https://beennnn.github.io/runtime-xray/multi/
EOF

# ---------------------------------------------------------------- instructions

cat > "$KIT/README.txt" <<EOF
Runtime X-Ray $VERSION — demonstration package
==============================================

Everything needed to reproduce, on your own machine and with no network, the report
published at https://beennnn.github.io/runtime-xray/multi/

Requirement: a JDK 21 or later on the PATH. Nothing else.

  Linux / macOS   ./demo.sh
  Windows         demo.cmd

Then open  report/index.html  in a browser.

Contents
  runtime-xray.jar   the tool, complete edition: the three analysis components
                     travel inside it and are laid down in ~/.runtime-xray at the
                     first launch. It opens no connection.
  sample-app.jar     the observed application — an itinerary calculator
  libs/              its two dependencies, named in its manifest
  src/               its sources: that is what the report's code panel displays
  demo.sh, demo.cmd  the three runs of the demonstration

The three runs
  Scenario 1      a short run, values captured at RoutePlanner::travelTimeMinutes
  Scenario 2      timing restricted to lab/sample/terrain/*, values at
                  Terrain::slowdownFactor
  Full scenario   the same as the first, run long enough for the profile to be dense

  They accumulate in the same output directory: the report shows the campaign, and
  the coverage can be united across the runs one ticks.

  The three runs take some two and a half minutes in all — the workload is what makes
  the profile dense enough to read. ITERATIONS=8000000 ./demo.sh shortens them; the
  shape does not change, only the number of stack samples behind the percentages.

What you will NOT see under Windows
  The call tree, and it is not a defect of the package: time is sampled by
  async-profiler, which publishes no binary for Windows. Coverage, captured values,
  the code panel and the JaCoCo sites work exactly the same. The report says so
  where the tree would be, rather than showing an empty branch.

  Under WSL, Linux or macOS, the three runs give the whole report.

What is written, and where
  Everything goes under the output directory (report/ by default). runs/ holds the
  raw tool outputs, one directory per run; index.html is the report and travels on
  its own only as far as the overview — the tree, the code and the values are read
  beside it, so send the whole directory, not the single file.

  A campaign of three runs writes some five hundred files. On a machine where a
  security filter inspects every file open, exclude that runs/ directory from the
  scan: nothing in it is executed and everything in it is reproducible.

Licences
  runtime-xray is under the MIT licence. The complete edition redistributes JaCoCo
  (EPL-2.0), Arthas and async-profiler (Apache-2.0), unmodified. See THIRD-PARTY.md
  in the repository: https://github.com/Beennnn/runtime-xray

Checking the fingerprints (SHA-256)
  Linux / macOS   sha256sum -c SHA256SUMS.txt
  Windows         certutil -hashfile <file> SHA256
EOF

(cd "$KIT" && find . -type f ! -name SHA256SUMS.txt -print0 | sort -z \
  | xargs -0 sha256sum > SHA256SUMS.txt)

# ------------------------------------------------------------------- archive

rm -f "$ZIP"
if command -v zip >/dev/null 2>&1; then
  (cd "$REPO_DIR/target/demo-kit" && zip -qr "$ZIP" runtime-xray-demo)
else
  (cd "$REPO_DIR/target/demo-kit" && jar --create --file "$ZIP" --no-manifest runtime-xray-demo)
fi
[ -s "$ZIP" ] || { echo "  ❌ archive not produced" >&2; exit 1; }

echo
echo "  ✅ $ZIP ($(( $(wc -c < "$ZIP") / 1024 / 1024 )) MB)"
