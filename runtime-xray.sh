#!/usr/bin/env bash
# Runtime X-Ray — produit une vue intégrée de ce qu'une exécution Java a réellement fait.
#
#   ./runtime-xray.sh --config mon-projet.conf
#   ./runtime-xray.sh --java "java -jar target/app.jar" --root "com.foo.Bar::traiter" \
#                     --classes target/classes --sources src/main/java
#
# Le principe : on n'impose RIEN sur la façon de lancer l'application. La commande fournie
# est exécutée telle quelle ; les agents d'analyse sont injectés par la variable
# d'environnement JAVA_TOOL_OPTIONS, que toute JVM lit au démarrage. Cela fonctionne donc
# aussi bien avec `java -jar`, `mvn exec:java`, `gradle run` ou un script maison.
set -euo pipefail

JACOCO_VERSION="${JACOCO_VERSION:-0.8.13}"
ARTHAS_VERSION="${ARTHAS_VERSION:-4.3.4}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ---------------------------------------------------------------- paramètres
JAVA_CMD=""            # commande qui lance l'application (obligatoire)
ROOT_METHOD=""         # "paquet.Classe::methode" — la fonction racine à inspecter
CLASSES_DIR=""         # .class compilés (obligatoire pour la couverture)
SOURCE_DIRS=""         # sources .java, pour afficher le code annoté
CLASS_FILTER=""        # ex. "com/foo/*" — restreint le profil au code applicatif
OUT_DIR="runtime-xray-out"
ATTACH_AFTER=8         # secondes avant d'attacher l'inspecteur de valeurs
MAX_SECONDS=600        # garde-fou si l'application ne se termine pas
WATCH_COUNT=10         # nombre d'appels capturés, toutes méthodes de la classe racine

usage() {
  sed -n '2,14p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
  cat <<'USAGE'

Options
  --config <fichier>   Charge les paramètres depuis un fichier (voir runtime-xray.conf.example)
  --java "<commande>"  Commande qui lance l'application. Obligatoire.
  --root "<C::m>"      Méthode racine à inspecter (valeurs des paramètres, arbre d'un appel)
  --classes <dir>      Répertoire des .class compilés. Obligatoire.
  --sources <dirs>     Répertoires de sources, séparés par ':' — pour le code annoté
  --filter "<motif>"   Restreint le profil, ex. "com/foo/*" (défaut : déduit de --root)
  --out <dir>          Répertoire de sortie (défaut : runtime-xray-out)
  --attach-after <s>   Délai avant l'attachement de l'inspecteur (défaut : 8)
  --max-seconds <s>    Durée maximale de l'exécution (défaut : 600)
  --no-values          N'inspecte pas les valeurs (pas d'Arthas) : profil non perturbé
USAGE
}

WANT_VALUES=1
while [ $# -gt 0 ]; do
  case "$1" in
    --config)       # shellcheck disable=SC1090
                    source "$2"; shift 2 ;;
    --java)         JAVA_CMD="$2"; shift 2 ;;
    --root)         ROOT_METHOD="$2"; shift 2 ;;
    --classes)      CLASSES_DIR="$2"; shift 2 ;;
    --sources)      SOURCE_DIRS="$2"; shift 2 ;;
    --filter)       CLASS_FILTER="$2"; shift 2 ;;
    --out)          OUT_DIR="$2"; shift 2 ;;
    --attach-after) ATTACH_AFTER="$2"; shift 2 ;;
    --max-seconds)  MAX_SECONDS="$2"; shift 2 ;;
    --no-values)    WANT_VALUES=0; shift ;;
    -h|--help)      usage; exit 0 ;;
    *) echo "Option inconnue : $1" >&2; usage; exit 2 ;;
  esac
done

[ -n "$JAVA_CMD" ]    || { echo "❌ --java est obligatoire" >&2; usage; exit 2; }
[ -n "$CLASSES_DIR" ] || { echo "❌ --classes est obligatoire" >&2; usage; exit 2; }
[ -d "$CLASSES_DIR" ] || { echo "❌ répertoire de classes introuvable : $CLASSES_DIR" >&2; exit 2; }

# Filtre de profil déduit du paquet de la méthode racine, à défaut de consigne explicite.
if [ -z "$CLASS_FILTER" ] && [ -n "$ROOT_METHOD" ]; then
  pkg="${ROOT_METHOD%%::*}"; pkg="${pkg%.*}"
  CLASS_FILTER="${pkg//./\/}/*"
fi

mkdir -p "$OUT_DIR"/{jacoco,async-profiler,arthas}
CACHE="${RUNTIME_XRAY_CACHE:-$HOME/.runtime-xray}"
mkdir -p "$CACHE"

# ------------------------------------------------------------------- outils
echo "▶ Vérification des outils"
JACOCO_AGENT="$CACHE/org.jacoco.agent-${JACOCO_VERSION}-runtime.jar"
JACOCO_CLI="$CACHE/org.jacoco.cli-${JACOCO_VERSION}-nodeps.jar"
fetch() { # groupId:artifactId:version[:packaging[:classifier]] -> $CACHE
  mvn -q dependency:copy -Dartifact="$1" -DoutputDirectory="$CACHE" 2>/dev/null
}
[ -f "$JACOCO_AGENT" ] || fetch "org.jacoco:org.jacoco.agent:${JACOCO_VERSION}:jar:runtime"
[ -f "$JACOCO_CLI" ]   || fetch "org.jacoco:org.jacoco.cli:${JACOCO_VERSION}:jar:nodeps"
[ -f "$JACOCO_AGENT" ] || { echo "❌ agent JaCoCo introuvable (Maven requis pour le récupérer une fois)" >&2; exit 1; }

ASYNC_LIB="${ASYNC_PROFILER_LIB:-}"
if [ -z "$ASYNC_LIB" ] && command -v brew >/dev/null 2>&1; then
  ASYNC_LIB="$(brew --prefix async-profiler 2>/dev/null)/lib/libasyncProfiler.dylib"
fi
[ -f "${ASYNC_LIB:-/dev/null}" ] || { echo "❌ async-profiler introuvable. Installer, ou définir ASYNC_PROFILER_LIB" >&2; exit 1; }

ARTHAS_HOME="$CACHE/arthas-${ARTHAS_VERSION}"
if [ "$WANT_VALUES" = "1" ] && [ ! -f "$ARTHAS_HOME/arthas-boot.jar" ]; then
  echo "   installation d'Arthas (une seule fois)"
  fetch "com.taobao.arthas:arthas-packaging:${ARTHAS_VERSION}:zip:bin"
  mkdir -p "$ARTHAS_HOME"
  unzip -oq "$CACHE/arthas-packaging-${ARTHAS_VERSION}-bin.zip" -d "$ARTHAS_HOME"
fi

# ------------------------------------------------------------- l'exécution
# JAVA_TOOL_OPTIONS : le seul moyen d'injecter des agents sans toucher à la commande.
AGENT_OPTS="-javaagent:${JACOCO_AGENT}=destfile=${PWD}/${OUT_DIR}/jacoco/jacoco.exec"
ASYNC_OPTS="start,event=itimer,interval=1ms,collapsed,file=${PWD}/${OUT_DIR}/async-profiler/profil.collapsed"
[ -n "$CLASS_FILTER" ] && ASYNC_OPTS="${ASYNC_OPTS},include=${CLASS_FILTER}"
AGENT_OPTS="${AGENT_OPTS} -agentpath:${ASYNC_LIB}=${ASYNC_OPTS}"
AGENT_OPTS="${AGENT_OPTS} -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints"

START_ISO="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
START_LOCAL="$(date '+%Y-%m-%d %H:%M:%S %Z')"
START_EPOCH="$(date +%s)"

echo "▶ Exécution de l'application"
echo "   $JAVA_CMD"
JAVA_TOOL_OPTIONS="$AGENT_OPTS" bash -c "$JAVA_CMD" > "$OUT_DIR/execution.log" 2>&1 &
RUNNER=$!
trap 'kill $RUNNER 2>/dev/null || true' EXIT

# ----------------------------------------------- inspection des valeurs
if [ "$WANT_VALUES" = "1" ] && [ -n "$ROOT_METHOD" ]; then
  sleep "$ATTACH_AFTER"
  ROOT_CLASS="${ROOT_METHOD%%::*}"; ROOT_NAME="${ROOT_METHOD##*::}"
  # Trouver la JVM lancée par la commande fournie. `bash -c` remplace souvent son propre
  # processus par la commande (exec implicite) : $RUNNER EST alors la JVM. Sinon, la JVM
  # est un enfant. On teste les deux, puis on se rabat sur la liste des JVM vivantes.
  TARGET_PID=""
  jvms="$(jcmd -l 2>/dev/null | grep -v 'sun.tools.jcmd' | awk '{print $1}')"
  is_jvm() { echo "$jvms" | grep -qx "$1"; }
  if is_jvm "$RUNNER"; then
    TARGET_PID="$RUNNER"
  else
    for child in $(pgrep -P "$RUNNER" 2>/dev/null || true); do
      if is_jvm "$child"; then TARGET_PID="$child"; break; fi
      for grand in $(pgrep -P "$child" 2>/dev/null || true); do
        if is_jvm "$grand"; then TARGET_PID="$grand"; break 2; fi
      done
    done
  fi
  if [ -n "${TARGET_PID:-}" ]; then
    echo "▶ Inspection des valeurs sur $ROOT_METHOD (pid $TARGET_PID)"
    # On observe TOUTES les méthodes de la classe racine : une classe en contient
    # plusieurs, et rattacher les valeurs à la classe entière ne dirait pas laquelle
    # a été appelée avec quoi.
    printf "watch %s * '{params, returnObj}' -n %s -x 2\nstop\n" \
      "$ROOT_CLASS" "$WATCH_COUNT" > "$OUT_DIR/arthas/watch.as"
    printf "trace %s %s -n 2\n" "$ROOT_CLASS" "$ROOT_NAME" > "$OUT_DIR/arthas/trace.as"
    for b in watch trace; do
      java -jar "$ARTHAS_HOME/arthas-boot.jar" "$TARGET_PID" --arthas-home "$ARTHAS_HOME" \
           -f "$OUT_DIR/arthas/$b.as" > "$OUT_DIR/arthas/${b}-params.txt" 2>&1 &
      boot=$!; g=0
      while [ $g -lt 6 ] && kill -0 $boot 2>/dev/null; do sleep 3; g=$((g+1)); done
      kill $boot 2>/dev/null || true
    done
    mv -f "$OUT_DIR/arthas/trace-params.txt" "$OUT_DIR/arthas/trace-calltree.txt" 2>/dev/null || true
  else
    echo "   ⚠️ JVM introuvable : inspection des valeurs sautée"
  fi
fi

echo "▶ Attente de la fin de l'exécution"
elapsed=0
while kill -0 $RUNNER 2>/dev/null && [ $elapsed -lt "$MAX_SECONDS" ]; do sleep 2; elapsed=$((elapsed+2)); done
RUN_STATUS="terminée normalement"
if [ $elapsed -ge "$MAX_SECONDS" ]; then RUN_STATUS="interrompue après $MAX_SECONDS s (garde-fou)"; fi
kill $RUNNER 2>/dev/null || true; trap - EXIT
sleep 2   # laisse les agents écrire leurs fichiers

END_LOCAL="$(date '+%Y-%m-%d %H:%M:%S %Z')"
export RX_CMD="$JAVA_CMD" RX_ROOT="$ROOT_METHOD" RX_FILTER="$CLASS_FILTER" \
       RX_CLASSES="$CLASSES_DIR" RX_SOURCES="$SOURCE_DIRS" RX_VALUES="$WANT_VALUES" \
       RX_START="$START_LOCAL" RX_END="$END_LOCAL" RX_STATUS="$RUN_STATUS" \
       RX_JACOCO="$JACOCO_VERSION" RX_ASYNC="$(asprof --version 2>/dev/null | head -1 || echo installé)" \
       RX_ARTHAS="$ARTHAS_VERSION"
export RX_DURATION=$(( $(date +%s) - START_EPOCH ))

# Contexte de l'exécution : sans lui, un rapport retrouvé trois mois plus tard ne dit pas
# de QUOI il parle. Écrit en JSON pour être relu par le générateur de la vue.
echo "▶ Enregistrement du contexte d'exécution"
python3 - "$OUT_DIR/run-context.json" <<PYCTX
import json, os, platform, subprocess, sys

def run(*cmd):
    try:
        return subprocess.run(cmd, capture_output=True, text=True, timeout=10).stdout.strip() \
            or subprocess.run(cmd, capture_output=True, text=True, timeout=10).stderr.strip()
    except Exception:
        return ""

java_v = run("java", "-version").splitlines()
ctx = {
  "commande":        os.environ.get("RX_CMD", ""),
  "methodeRacine":   os.environ.get("RX_ROOT", "") or None,
  "filtreClasses":   os.environ.get("RX_FILTER", "") or None,
  "repertoireClasses": os.environ.get("RX_CLASSES", ""),
  "repertoiresSources": os.environ.get("RX_SOURCES", "") or None,
  "valeursInspectees": os.environ.get("RX_VALUES", "") == "1",
  "debut":           os.environ.get("RX_START", ""),
  "fin":             os.environ.get("RX_END", ""),
  "dureeSecondes":   int(os.environ.get("RX_DURATION", "0") or 0),
  "statut":          os.environ.get("RX_STATUS", ""),
  "machine":         platform.node(),
  "utilisateur":     os.environ.get("USER", ""),
  "systeme":         f"{platform.system()} {platform.release()} ({platform.machine()})",
  "processeurs":     os.cpu_count(),
  "java":            java_v[0] if java_v else "",
  "javaHome":        os.environ.get("JAVA_HOME", ""),
  "repertoireTravail": os.getcwd(),
  "outils": {
    "JaCoCo":         os.environ.get("RX_JACOCO", ""),
    "async-profiler": os.environ.get("RX_ASYNC", ""),
    "Arthas":         os.environ.get("RX_ARTHAS", "") if os.environ.get("RX_VALUES") == "1" else "non utilisé",
  },
}
json.dump(ctx, open(sys.argv[1], "w"), ensure_ascii=False, indent=2)
PYCTX

# ---------------------------------------------------------------- rapports
echo "▶ Rendu de la couverture"
SRC_ARGS=()
if [ -n "$SOURCE_DIRS" ]; then
  IFS=':' read -r -a dirs <<< "$SOURCE_DIRS"
  for d in "${dirs[@]}"; do SRC_ARGS+=(--sourcefiles "$d"); done
fi
mkdir -p "$OUT_DIR/jacoco/html"   # la CLI n'crée pas l'arborescence des sorties XML/CSV
java -jar "$JACOCO_CLI" report "$OUT_DIR/jacoco/jacoco.exec" \
  --classfiles "$CLASSES_DIR" "${SRC_ARGS[@]}" \
  --html "$OUT_DIR/jacoco/html" --xml "$OUT_DIR/jacoco/html/jacoco.xml" \
  --csv "$OUT_DIR/jacoco/html/jacoco.csv" --name "Runtime X-Ray" --quiet

echo "▶ Assemblage de la vue intégrée"
python3 "$HERE/tools/summary/build-dashboard.py" \
  --gen "$OUT_DIR" --sources "${SOURCE_DIRS:-}" --traced "${ROOT_METHOD%%::*}"

echo
echo "✅ Terminé — ouvrir : $OUT_DIR/index.html"
