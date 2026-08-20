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
RUN_NAME=""            # nom lisible donné à cette exécution
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
  --name "<texte>"     Nom de CETTE exécution — « recette v2, cas nominal ». Les exécutions
                       s'accumulent dans <out>/runs/ et la vue permet de passer de l'une à
                       l'autre. Sans nom, un horodatage est utilisé.
  --attach-after <s>   Délai avant l'attachement de l'inspecteur (défaut : 8)
  --max-seconds <s>    Durée maximale de l'exécution (défaut : 600)
  --no-values          N'inspecte pas les valeurs (pas d'Arthas) : profil non perturbé
USAGE
}

# ------------------------------------------------- modèle de configuration
# Généré quand le fichier attendu n'existe pas : mieux vaut un gabarit commenté à adapter
# qu'un message d'erreur qui laisse deviner les clés.
write_config_template() {
  cat > "$1" <<'TEMPLATE'
# ---------------------------------------------------------------------------
# Runtime X-Ray — configuration
#
#   ./runtime-xray.sh --config ce-fichier.conf
#
# Seules JAVA_CMD et CLASSES_DIR sont obligatoires. Le reste a des valeurs par
# défaut raisonnables ; les lignes commentées montrent d'autres usages.
# ---------------------------------------------------------------------------

# ── Comment lancer l'application ────────────────────────────────── OBLIGATOIRE
# Aucune contrainte : la commande est exécutée telle quelle. Les agents d'analyse
# sont injectés par JAVA_TOOL_OPTIONS, que toute JVM lit à son démarrage.
JAVA_CMD="java -jar target/mon-appli.jar"
#JAVA_CMD="java -Xmx2g -jar target/mon-appli.jar --profil recette --jeu 42"
#JAVA_CMD="mvn -q exec:java -Dexec.mainClass=com.exemple.Main"
#JAVA_CMD="./gradlew run --args='--profil recette'"
#JAVA_CMD="./scripts/demarrer-en-recette.sh"

# ── Les classes compilées ───────────────────────────────────────── OBLIGATOIRE
# Sans elles, pas de couverture : c'est là que se trouve le bytecode analysé.
CLASSES_DIR="target/classes"
#CLASSES_DIR="build/classes/java/main"          # Gradle
#CLASSES_DIR="target/classes:module-b/target/classes"   # non supporté : un seul répertoire

# ── La méthode racine ───────────────────────────────────────────── recommandé
# La fonction dont on veut voir les valeurs des paramètres et l'arbre d'un appel.
# Format paquet.Classe::methode. Choisir un point d'entrée MÉTIER : un traitement,
# un calcul, une commande — pas un main, pas un accesseur.
# Laisser vide pour n'obtenir que la couverture et les temps.
ROOT_METHOD="com.exemple.moteur.Calculateur::calculer"
#ROOT_METHOD="com.exemple.api.CommandeService::valider"
#ROOT_METHOD=""                                  # sans capture de valeurs

# ── Les sources ─────────────────────────────────────────────────── recommandé
# Pour afficher le code annoté. Plusieurs racines : séparées par ':'.
SOURCE_DIRS="src/main/java"
#SOURCE_DIRS="src/main/java:src/generated/java"

# ── Le nom de cette exécution ───────────────────────────────────── facultatif
# Les exécutions s'accumulent dans <OUT_DIR>/runs/ et la vue permet de passer de
# l'une à l'autre. Un nom parlant vaut mieux qu'un horodatage.
# Il reste modifiable après coup, sans relancer : voir OUT_DIR/noms.json.
#RUN_NAME="recette v2 — cas nominal"
#RUN_NAME="incident 4712 — reproduction"

# ── Le filtre de profil ─────────────────────────────────────────── facultatif
# Restreint les mesures de temps au code applicatif. Sans lui, la majorité des
# relevés concernent le compilateur interne de la JVM : exact, mais illisible.
# Déduit du paquet de ROOT_METHOD s'il est absent.
#CLASS_FILTER="com/exemple/*"

# ── Sortie et garde-fous ────────────────────────────────────────── facultatif
OUT_DIR="runtime-xray-out"

# Délai avant d'inspecter les valeurs. L'application doit avoir démarré ET être
# encore en train de travailler. Si elle est trop rapide, augmenter sa charge
# plutôt que de réduire ce délai : un relevé sur deux secondes ne dit rien.
ATTACH_AFTER=8

# Au-delà, l'exécution est interrompue et les rapports sont tout de même produits.
MAX_SECONDS=600

# Nombre d'appels dont on capture les valeurs, toutes méthodes de la classe racine.
WATCH_COUNT=10
TEMPLATE
}

WANT_VALUES=1
while [ $# -gt 0 ]; do
  case "$1" in
    --config)       if [ ! -f "$2" ]; then
                      write_config_template "$2"
                      echo "✅ Fichier de configuration généré : $2"
                      echo
                      echo "   Il contient les valeurs par défaut, des commentaires et des"
                      echo "   exemples. Ouvrir le fichier, renseigner au minimum JAVA_CMD et"
                      echo "   CLASSES_DIR, puis relancer :"
                      echo
                      echo "     ./runtime-xray.sh --config $2"
                      exit 0
                    fi
                    # shellcheck disable=SC1090
                    source "$2"; shift 2 ;;
    --java)         JAVA_CMD="$2"; shift 2 ;;
    --root)         ROOT_METHOD="$2"; shift 2 ;;
    --classes)      CLASSES_DIR="$2"; shift 2 ;;
    --sources)      SOURCE_DIRS="$2"; shift 2 ;;
    --filter)       CLASS_FILTER="$2"; shift 2 ;;
    --out)          OUT_DIR="$2"; shift 2 ;;
    --name)         RUN_NAME="$2"; shift 2 ;;
    --attach-after) ATTACH_AFTER="$2"; shift 2 ;;
    --max-seconds)  MAX_SECONDS="$2"; shift 2 ;;
    --no-values)    WANT_VALUES=0; shift ;;
    -h|--help)      usage; exit 0 ;;
    *) echo "Option inconnue : $1" >&2; usage; exit 2 ;;
  esac
done

# Sans aucun argument : on cherche la configuration à l'endroit conventionnel, et on la
# crée si elle manque. Personne ne devrait avoir à deviner le format d'un fichier.
if [ -z "$JAVA_CMD" ] && [ -z "$CLASSES_DIR" ]; then
  DEFAULT_CONF="runtime-xray.conf"
  if [ -f "$DEFAULT_CONF" ]; then
    echo "▶ Configuration lue : $DEFAULT_CONF"
    # shellcheck disable=SC1090
    source "$DEFAULT_CONF"
  else
    write_config_template "$DEFAULT_CONF"
    echo "✅ Fichier de configuration généré : $DEFAULT_CONF"
    echo
    echo "   Aucune configuration n'existait ici. Le fichier créé contient les valeurs par"
    echo "   défaut, des commentaires et des exemples. Renseigner au minimum JAVA_CMD et"
    echo "   CLASSES_DIR, puis relancer :"
    echo
    echo "     ./runtime-xray.sh"
    exit 0
  fi
fi

[ -n "$JAVA_CMD" ]    || { echo "❌ --java est obligatoire" >&2; usage; exit 2; }
[ -n "$CLASSES_DIR" ] || { echo "❌ --classes est obligatoire" >&2; usage; exit 2; }
[ -d "$CLASSES_DIR" ] || { echo "❌ répertoire de classes introuvable : $CLASSES_DIR" >&2; exit 2; }

# Filtre de profil déduit du paquet de la méthode racine, à défaut de consigne explicite.
if [ -z "$CLASS_FILTER" ] && [ -n "$ROOT_METHOD" ]; then
  pkg="${ROOT_METHOD%%::*}"; pkg="${pkg%.*}"
  CLASS_FILTER="${pkg//./\/}/*"
fi

# Chaque exécution vit dans son propre répertoire : elles s'accumulent, et la vue
# permet de naviguer de l'une à l'autre. Le nom donné par l'utilisateur sert d'étiquette.
STAMP="$(date +%Y%m%d-%H%M%S)"
# Identifiant permanent de l'exécution : il ne change jamais, contrairement au nom.
# C'est lui qui sert de clé quand on renomme après coup.
RUN_UUID="$(uuidgen 2>/dev/null || python3 -c 'import uuid;print(uuid.uuid4())')"
if [ -n "$RUN_NAME" ]; then
  SLUG="$(printf '%s' "$RUN_NAME" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-//;s/-$//')"
  RUN_DIR="$OUT_DIR/runs/${STAMP}-${SLUG}"
else
  RUN_NAME="exécution du $(date '+%d/%m/%Y à %H:%M')"
  RUN_DIR="$OUT_DIR/runs/${STAMP}"
fi
mkdir -p "$RUN_DIR"/{jacoco,async-profiler,arthas}
echo "▶ Exécution « $RUN_NAME » — identifiant $RUN_UUID"
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
AGENT_OPTS="-javaagent:${JACOCO_AGENT}=destfile=${PWD}/${RUN_DIR}/jacoco/jacoco.exec"
ASYNC_OPTS="start,event=itimer,interval=1ms,collapsed,file=${PWD}/${RUN_DIR}/async-profiler/profil.collapsed"
[ -n "$CLASS_FILTER" ] && ASYNC_OPTS="${ASYNC_OPTS},include=${CLASS_FILTER}"
AGENT_OPTS="${AGENT_OPTS} -agentpath:${ASYNC_LIB}=${ASYNC_OPTS}"
AGENT_OPTS="${AGENT_OPTS} -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints"

START_ISO="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
START_LOCAL="$(date '+%Y-%m-%d %H:%M:%S %Z')"
START_EPOCH="$(date +%s)"

echo "▶ Exécution de l'application"
echo "   $JAVA_CMD"
JAVA_TOOL_OPTIONS="$AGENT_OPTS" bash -c "$JAVA_CMD" > "$RUN_DIR/execution.log" 2>&1 &
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
      "$ROOT_CLASS" "$WATCH_COUNT" > "$RUN_DIR/arthas/watch.as"
    printf "trace %s %s -n 2\n" "$ROOT_CLASS" "$ROOT_NAME" > "$RUN_DIR/arthas/trace.as"
    for b in watch trace; do
      java -jar "$ARTHAS_HOME/arthas-boot.jar" "$TARGET_PID" --arthas-home "$ARTHAS_HOME" \
           -f "$RUN_DIR/arthas/$b.as" > "$RUN_DIR/arthas/${b}-params.txt" 2>&1 &
      boot=$!; g=0
      while [ $g -lt 6 ] && kill -0 $boot 2>/dev/null; do sleep 3; g=$((g+1)); done
      kill $boot 2>/dev/null || true
    done
    mv -f "$RUN_DIR/arthas/trace-params.txt" "$RUN_DIR/arthas/trace-calltree.txt" 2>/dev/null || true
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
export RX_UUID="$RUN_UUID" RX_NAME="$RUN_NAME" RX_CMD="$JAVA_CMD" RX_ROOT="$ROOT_METHOD" RX_FILTER="$CLASS_FILTER" \
       RX_CLASSES="$CLASSES_DIR" RX_SOURCES="$SOURCE_DIRS" RX_VALUES="$WANT_VALUES" \
       RX_START="$START_LOCAL" RX_END="$END_LOCAL" RX_STATUS="$RUN_STATUS" \
       RX_JACOCO="$JACOCO_VERSION" RX_ASYNC="$(asprof --version 2>/dev/null | head -1 || echo installé)" \
       RX_ARTHAS="$ARTHAS_VERSION"
export RX_DURATION=$(( $(date +%s) - START_EPOCH ))

# Contexte de l'exécution : sans lui, un rapport retrouvé trois mois plus tard ne dit pas
# de QUOI il parle. Écrit en JSON pour être relu par le générateur de la vue.
echo "▶ Enregistrement du contexte d'exécution"
python3 - "$RUN_DIR/run-context.json" <<PYCTX
import json, os, platform, subprocess, sys

def run(*cmd):
    try:
        return subprocess.run(cmd, capture_output=True, text=True, timeout=10).stdout.strip() \
            or subprocess.run(cmd, capture_output=True, text=True, timeout=10).stderr.strip()
    except Exception:
        return ""

java_v = run("java", "-version").splitlines()
ctx = {
  "uuid":            os.environ.get("RX_UUID", ""),
  "nomOrigine":      os.environ.get("RX_NAME", ""),
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
mkdir -p "$RUN_DIR/jacoco/html"   # la CLI n'crée pas l'arborescence des sorties XML/CSV
java -jar "$JACOCO_CLI" report "$RUN_DIR/jacoco/jacoco.exec" \
  --classfiles "$CLASSES_DIR" "${SRC_ARGS[@]}" \
  --html "$RUN_DIR/jacoco/html" --xml "$RUN_DIR/jacoco/html/jacoco.xml" \
  --csv "$RUN_DIR/jacoco/html/jacoco.csv" --name "Runtime X-Ray" --quiet

echo "▶ Assemblage de la vue intégrée"
python3 "$HERE/tools/summary/build-dashboard.py" \
  --gen "$OUT_DIR" --sources "${SOURCE_DIRS:-}" --traced "${ROOT_METHOD%%::*}"

echo
echo "✅ Terminé — ouvrir : $OUT_DIR/index.html"
echo "   Pour renommer cette exécution plus tard, ajouter dans $OUT_DIR/noms.json :"
echo "     { \"$RUN_UUID\": \"un nom plus parlant\" }"
