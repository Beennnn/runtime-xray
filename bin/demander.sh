#!/bin/sh
# Poser une question sur un rapport, à un modèle de langage.
#
# CE SCRIPT EST DÉLIBÉRÉMENT MINCE, et c'est tout son intérêt.
#
# Le travail utile — choisir les faits, joindre leur légende, mettre en tête ce qui n'a PAS
# été mesuré, borner le volume — est fait par l'outil lui-même :
#
#     runtime-xray --out <rapport> --contexte "<question>"
#
# Ce qui reste ici, c'est de poster ce texte quelque part. Trois formes de requête couvrent
# aujourd'hui l'essentiel du marché, et elles sont écrites côte à côte plus bas : on voit du
# premier coup d'œil que seule l'enveloppe change. Le jour où une quatrième s'impose, il y a
# une trentaine de lignes à écrire, et rien d'autre à toucher.
#
# Aucune dépendance : curl et jq.
#
#   ./bin/demander.sh --out runtime-xray-out "which classes never ran?"
#   ./bin/demander.sh --api anthropic --modele … "where does the time go?"
#   ./bin/demander.sh --contexte-seul --out runtime-xray-out "…"   # rien n'est envoyé
#
# La clé se lit dans XRAY_CLE, ou dans OPENAI_API_KEY / ANTHROPIC_API_KEY / GEMINI_API_KEY.
# Elle n'est jamais écrite sur la ligne de commande : celle-ci se retrouve dans l'historique
# du terminal et dans la liste des processus, où tout le monde la lit.
set -eu

API="${XRAY_API:-openai}"
URL="${XRAY_URL:-}"
MODELE="${XRAY_MODELE:-}"
SORTIE="${XRAY_OUT:-runtime-xray-out}"
JAR="${XRAY_JAR:-runtime-xray.jar}"
CONTEXTE_SEUL=0
QUESTION=""

usage() {
  cat <<'FIN'
Usage : demander.sh [options] "la question"

  --api openai|anthropic|gemini   forme de la requête      (défaut : openai)
  --url URL                       point d'entrée           (défaut : selon --api)
  --modele NOM                    identifiant du modèle
  --out RÉPERTOIRE                le rapport à interroger  (défaut : runtime-xray-out)
  --jar CHEMIN                    runtime-xray.jar
  --contexte-seul                 n'envoie rien : écrit le contexte sur la sortie standard

« openai » désigne la FORME de la requête, pas le fournisseur : la plupart des passerelles
auto-hébergées et des services commerciaux l'acceptent. Vérifier la sienne, pas supposer.
FIN
}

while [ $# -gt 0 ]; do
  case "$1" in
    --api) API="$2"; shift 2 ;;
    --url) URL="$2"; shift 2 ;;
    --modele) MODELE="$2"; shift 2 ;;
    --out) SORTIE="$2"; shift 2 ;;
    --jar) JAR="$2"; shift 2 ;;
    --contexte-seul) CONTEXTE_SEUL=1; shift ;;
    -h|--help) usage; exit 0 ;;
    --*) echo "Option inconnue : $1" >&2; usage; exit 2 ;;
    *) QUESTION="$1"; shift ;;
  esac
done

[ -n "$QUESTION" ] || { echo "Il faut une question." >&2; usage; exit 2; }
command -v jq >/dev/null 2>&1 || { echo "jq est requis." >&2; exit 3; }

# ---------------------------------------------------------------- le contexte
# C'est l'outil qui le produit, jamais ce script : la sélection des faits est une décision
# d'analyse, elle a sa place dans le code éprouvé par les tests, pas dans un shell.
CONTEXTE=$(java -jar "$JAR" --out "$SORTIE" --contexte "$QUESTION")

if [ "$CONTEXTE_SEUL" = "1" ]; then
  printf '%s\n' "$CONTEXTE"
  exit 0
fi

CONSIGNE="Tu réponds à partir des seules données fournies. Si elles ne permettent pas de
conclure, dis-le au lieu de supposer. Un chiffre à zéro dont la section « ce qui N'A PAS été
mesuré » explique l'absence n'est PAS un constat : ne l'interprète pas. Cite les noms de
classes et de méthodes exactement comme ils apparaissent."

case "$API" in
  openai)
    URL="${URL:-${OPENAI_BASE_URL:-http://localhost:8080/v1}/chat/completions}"
    CLE="${XRAY_CLE:-${OPENAI_API_KEY:-}}"
    CORPS=$(jq -n --arg m "${MODELE:-gpt-4o-mini}" --arg s "$CONSIGNE" --arg u "$CONTEXTE" \
      '{model:$m, messages:[{role:"system",content:$s},{role:"user",content:$u}],
        temperature:0}')
    REPONSE=$(curl -sS "$URL" -H 'Content-Type: application/json' \
      ${CLE:+-H "Authorization: Bearer $CLE"} -d "$CORPS")
    printf '%s' "$REPONSE" | jq -r '.choices[0].message.content // (.error.message // .)'
    ;;

  anthropic)
    URL="${URL:-https://api.anthropic.com/v1/messages}"
    CLE="${XRAY_CLE:-${ANTHROPIC_API_KEY:-}}"
    # La consigne n'est pas un message : elle a son propre champ. C'est toute la différence
    # d'enveloppe avec la forme précédente.
    CORPS=$(jq -n --arg m "${MODELE:-claude-sonnet-4-5}" --arg s "$CONSIGNE" --arg u "$CONTEXTE" \
      '{model:$m, max_tokens:2000, system:$s, messages:[{role:"user",content:$u}]}')
    REPONSE=$(curl -sS "$URL" -H 'Content-Type: application/json' \
      -H 'anthropic-version: 2023-06-01' ${CLE:+-H "x-api-key: $CLE"} -d "$CORPS")
    printf '%s' "$REPONSE" | jq -r '.content[0].text // (.error.message // .)'
    ;;

  gemini)
    CLE="${XRAY_CLE:-${GEMINI_API_KEY:-}}"
    MODELE="${MODELE:-gemini-2.0-flash}"
    URL="${URL:-https://generativelanguage.googleapis.com/v1beta/models/$MODELE:generateContent}"
    CORPS=$(jq -n --arg s "$CONSIGNE" --arg u "$CONTEXTE" \
      '{system_instruction:{parts:[{text:$s}]}, contents:[{parts:[{text:$u}]}]}')
    REPONSE=$(curl -sS "$URL" -H 'Content-Type: application/json' \
      ${CLE:+-H "x-goog-api-key: $CLE"} -d "$CORPS")
    printf '%s' "$REPONSE" | jq -r '.candidates[0].content.parts[0].text // (.error.message // .)'
    ;;

  *)
    echo "API inconnue : $API (openai, anthropic, gemini)" >&2
    exit 2 ;;
esac
