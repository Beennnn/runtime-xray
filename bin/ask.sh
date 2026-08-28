#!/bin/sh
# Ask a language model a question about a report.
#
# THIS SCRIPT IS DELIBERATELY THIN, and that is the whole point.
#
# The useful work — choosing the facts, attaching their legend, putting what was NOT
# measured first, bounding the size — is done by the tool itself:
#
#     runtime-xray --out <report> --context "<question>"
#
# What is left here is posting that text somewhere. Three request shapes cover most of the
# market today, and they are written side by side below: one glance shows that only the
# envelope changes. The day a fourth one matters, that is thirty lines to write and nothing
# else to touch.
#
# No dependency: curl and jq.
#
#   ./bin/ask.sh --out runtime-xray-out "which classes never ran?"
#   ./bin/ask.sh --api anthropic --model … "where does the time go?"
#   ./bin/ask.sh --context-only --out runtime-xray-out "…"   # nothing is sent
#
# The key is read from XRAY_KEY, or from OPENAI_API_KEY / ANTHROPIC_API_KEY /
# GEMINI_API_KEY. It is never written on the command line: that line ends up in the shell
# history and in the process list, where everyone can read it.
set -eu

API="${XRAY_API:-openai}"
URL="${XRAY_URL:-}"
MODEL="${XRAY_MODEL:-}"
OUT_DIR="${XRAY_OUT:-runtime-xray-out}"
JAR="${XRAY_JAR:-runtime-xray.jar}"
CONTEXT_ONLY=0
QUESTION=""

usage() {
  cat <<'FIN'
Usage: ask.sh [options] "the question"

  --api openai|anthropic|gemini   request shape          (default: openai)
  --url URL                       endpoint               (default: depends on --api)
  --model NAME                    model identifier
  --out DIRECTORY                 the report to ask about (default: runtime-xray-out)
  --jar PATH                      runtime-xray.jar
  --context-only                  sends nothing: writes the context on standard output

"openai" names the SHAPE of the request, not the vendor: most self-hosted gateways and
commercial services accept it. Check yours, do not assume.
FIN
}

while [ $# -gt 0 ]; do
  case "$1" in
    --api) API="$2"; shift 2 ;;
    --url) URL="$2"; shift 2 ;;
    --model|--modele) MODEL="$2"; shift 2 ;;
    --out) OUT_DIR="$2"; shift 2 ;;
    --jar) JAR="$2"; shift 2 ;;
    --context-only|--contexte-seul) CONTEXT_ONLY=1; shift ;;
    -h|--help) usage; exit 0 ;;
    --*) echo "Unknown option: $1" >&2; usage; exit 2 ;;
    *) QUESTION="$1"; shift ;;
  esac
done

[ -n "$QUESTION" ] || { echo "A question is required." >&2; usage; exit 2; }
command -v jq >/dev/null 2>&1 || { echo "jq is required." >&2; exit 3; }

# ------------------------------------------------------------------- the context
# The tool produces it, never this script: choosing the facts is an analysis decision, and
# it belongs in code the tests hold, not in a shell.
CONTEXT=$(java -jar "$JAR" --out "$OUT_DIR" --context "$QUESTION")

if [ "$CONTEXT_ONLY" = "1" ]; then
  printf '%s\n' "$CONTEXT"
  exit 0
fi

INSTRUCTION="Answer from the supplied data alone. If it does not allow a conclusion, say
so instead of assuming. A zero whose absence is explained by the 'what was NOT measured'
section is NOT a finding: do not interpret it. Quote class and method names exactly as they
appear."

case "$API" in
  openai)
    URL="${URL:-${OPENAI_BASE_URL:-http://localhost:8080/v1}/chat/completions}"
    KEY="${XRAY_CLE:-${OPENAI_API_KEY:-}}"
    BODY=$(jq -n --arg m "${MODEL:-gpt-4o-mini}" --arg s "$INSTRUCTION" --arg u "$CONTEXT" \
      '{model:$m, messages:[{role:"system",content:$s},{role:"user",content:$u}],
        temperature:0}')
    RESPONSE=$(curl -sS "$URL" -H 'Content-Type: application/json' \
      ${KEY:+-H "Authorization: Bearer $KEY"} -d "$BODY")
    printf '%s' "$RESPONSE" | jq -r '.choices[0].message.content // (.error.message // .)'
    ;;

  anthropic)
    URL="${URL:-https://api.anthropic.com/v1/messages}"
    KEY="${XRAY_CLE:-${ANTHROPIC_API_KEY:-}}"
    # The instruction is not a message: it has its own field. That is the whole difference
    # in envelope with the previous shape.
    BODY=$(jq -n --arg m "${MODEL:-claude-sonnet-4-5}" --arg s "$INSTRUCTION" --arg u "$CONTEXT" \
      '{model:$m, max_tokens:2000, system:$s, messages:[{role:"user",content:$u}]}')
    RESPONSE=$(curl -sS "$URL" -H 'Content-Type: application/json' \
      -H 'anthropic-version: 2023-06-01' ${KEY:+-H "x-api-key: $KEY"} -d "$BODY")
    printf '%s' "$RESPONSE" | jq -r '.content[0].text // (.error.message // .)'
    ;;

  gemini)
    KEY="${XRAY_CLE:-${GEMINI_API_KEY:-}}"
    MODEL="${MODEL:-gemini-2.0-flash}"
    URL="${URL:-https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent}"
    BODY=$(jq -n --arg s "$INSTRUCTION" --arg u "$CONTEXT" \
      '{system_instruction:{parts:[{text:$s}]}, contents:[{parts:[{text:$u}]}]}')
    RESPONSE=$(curl -sS "$URL" -H 'Content-Type: application/json' \
      ${KEY:+-H "x-goog-api-key: $KEY"} -d "$BODY")
    printf '%s' "$RESPONSE" | jq -r '.candidates[0].content.parts[0].text // (.error.message // .)'
    ;;

  *)
    echo "Unknown API: $API (openai, anthropic, gemini)" >&2
    exit 2 ;;
esac
