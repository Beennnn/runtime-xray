#!/usr/bin/env bash
# Assembles the kit to carry onto a machine with no network: the jar, and the three
# analysis components the tool would otherwise fetch from a Maven repository.
#
# The README already describes one way of filling the cache: run any analysis on a machine
# that has access. That supposes an analysis that succeeds — hence a platform where
# async-profiler exists, which rules out Windows, and an application to observe. This
# script demands neither: it only downloads, and therefore works from any machine with
# access to the Maven repository.
#
# The versions are not written here: they are read from Toolbox.java, the single source of
# truth. A kit can therefore hold nothing other than what the jar from the same repository
# will ask for.
#
#   bin/offline-kit.sh                       # from Maven Central
#   MAVEN_REPO=https://mirror.internal/maven2 bin/offline-kit.sh
set -uo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="$REPO_DIR/orchestrator/target/runtime-xray.jar"
TOOLBOX="$REPO_DIR/orchestrator/src/main/java/lab/xray/Toolbox.java"
REPO="${MAVEN_REPO:-https://repo1.maven.org/maven2}"
REPO="${REPO%/}"
KIT="$REPO_DIR/target/offline-kit/runtime-xray-kit"
ZIP="$REPO_DIR/target/runtime-xray-offline-kit.zip"

echo "Offline kit — Maven repository: $REPO"

# --------------------------------------------------------------- versions and jar

version(){ grep -o "$1 = \"[^\"]*\"" "$TOOLBOX" | head -1 | cut -d'"' -f2; }
JACOCO="$(version JACOCO_VERSION)"
ARTHAS="$(version ARTHAS_VERSION)"
ASYNC="$(version ASYNC_VERSION)"
if [ -z "$JACOCO" ] || [ -z "$ARTHAS" ] || [ -z "$ASYNC" ]; then
  echo "  ❌ versions unreadable in $TOOLBOX" >&2; exit 1
fi
echo "   JaCoCo $JACOCO · async-profiler $ASYNC · Arthas $ARTHAS"

if [ ! -f "$JAR" ]; then
  echo "   the jar is not built — mvn -DskipTests package"
  (cd "$REPO_DIR" && mvn -q -DskipTests package) || { echo "  ❌ building the jar" >&2; exit 1; }
fi

rm -rf "$REPO_DIR/target/offline-kit"
mkdir -p "$KIT/.runtime-xray"
cp "$JAR" "$KIT/"

# ------------------------------------------------------------------ components
#
# The file names are the ones Toolbox.artifact() recomposes to look in the cache: straying
# from them would give a kit that looks complete and re-downloads everything.

fetch(){ # group artifact version classifier extension
  local path="$1" name="$2" ver="$3" cls="$4" ext="$5"
  local file="$name-$ver${cls:+-$cls}.$ext"
  local url="$REPO/${path//.//}/$name/$ver/$file"
  printf '   %-42s' "$file"
  if curl -fsSL --retry 3 --connect-timeout 20 -o "$KIT/.runtime-xray/$file" "$url"; then
    echo "$(( $(wc -c < "$KIT/.runtime-xray/$file") / 1024 )) KB"
  else
    echo "❌"; echo "      $url" >&2; return 1
  fi
}

{
  fetch org.jacoco       org.jacoco.agent  "$JACOCO" runtime jar &&
  fetch org.jacoco       org.jacoco.cli    "$JACOCO" nodeps  jar &&
  fetch tools.profiler   jfr-converter     "$ASYNC"  ""      jar &&
  fetch tools.profiler   async-profiler    "$ASYNC"  ""      jar &&
  fetch com.taobao.arthas arthas-packaging "$ARTHAS" bin     zip
} || { echo "  ❌ a component is missing: incomplete kit, nothing is assembled" >&2; exit 1; }

# ------------------------------------------------------------------ skills
#
# Two Markdown files, no dependency. They travel with the kit because the isolated machine
# is precisely where one can no longer go and read the documentation.

mkdir -p "$KIT/skills"
cp -r "$REPO_DIR/skills/." "$KIT/skills/"
printf '   %-42s%s\n' "skills/" "running a campaign, and reading a report"

# ---------------------------------------------------------------- instructions

cat > "$KIT/README.txt" <<EOF
Runtime X-Ray — offline kit
===========================

Contents
  runtime-xray.jar   the tool (no dependency: a JDK 21+ is enough)
  .runtime-xray/     the three analysis components, already downloaded
  skills/            two skills for an assistant: running a campaign, and reading
                     a report without drawing a false conclusion from it.
                     Copy them into the project's .claude/skills/, or hand them
                     over as they are — they are two Markdown files.

Installing on the isolated machine
  Linux / macOS   cp -r .runtime-xray ~/
  Windows         xcopy /E /I .runtime-xray %USERPROFILE%\\.runtime-xray

  Then run as usual:  java -jar runtime-xray.jar ...
  The tool finds its components in that cache and opens no connection.

  Copying nothing works too: the files of .runtime-xray left beside the jar are
  taken as they are, like those of a directory named by --components, and like
  those of the local Maven repository (~/.m2/repository) if it already holds them.

Components
  JaCoCo $JACOCO          coverage   (org.jacoco.agent, org.jacoco.cli)
  async-profiler $ASYNC       time       (async-profiler, jfr-converter)
  Arthas $ARTHAS           values     (arthas-packaging)

  Archives published on Maven Central, unmodified; their respective licences
  apply (see THIRD-PARTY.md in the repository).

  The Arthas package is unzipped and async-profiler's native library extracted on
  first launch, for the current platform. Measuring time requires Linux or macOS:
  async-profiler publishes no Windows binary.

Checking the fingerprints (SHA-256)
  Linux / macOS   sha256sum -c SHA256SUMS.txt
  Windows         certutil -hashfile <file> SHA256
EOF

(cd "$KIT" && find . -type f ! -name SHA256SUMS.txt -print0 | sort -z \
  | xargs -0 sha256sum > SHA256SUMS.txt)

# ------------------------------------------------------------------- archive
#
# zip if the machine has it; otherwise the JDK's archiver, which produces the same format —
# the only thing one can count on here, since the tool already demands a JDK.

rm -f "$ZIP"
if command -v zip >/dev/null 2>&1; then
  (cd "$REPO_DIR/target/offline-kit" && zip -qr "$ZIP" runtime-xray-kit)
else
  (cd "$REPO_DIR/target/offline-kit" && jar --create --file "$ZIP" --no-manifest runtime-xray-kit)
fi
[ -s "$ZIP" ] || { echo "  ❌ archive not produced" >&2; exit 1; }

echo
echo "  ✅ $ZIP ($(( $(wc -c < "$ZIP") / 1024 / 1024 )) MB)"
echo "     to attach to a release, or to carry as it is."
