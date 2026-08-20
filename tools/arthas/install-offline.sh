#!/usr/bin/env bash
# Installation d'Arthas SANS passer par arthas.aliyun.com.
#
# Le lanceur `arthas-boot` télécharge normalement ses modules chez l'éditeur, ce qui le
# rend inutilisable en environnement déconnecté. Le paquet complet est pourtant publié
# sur Maven Central : il suffit de le déposer à l'endroit attendu, et le lanceur ne sort
# plus jamais sur le réseau (option --arthas-home).
#
# En environnement réellement coupé : récupérer une fois le zip depuis un poste connecté
# (ou depuis le miroir Maven interne), le transporter, et rejouer la partie unzip.
set -euo pipefail

ARTHAS_VERSION="4.3.4"
REPO_ROOT="$(git rev-parse --show-toplevel)"
HOME_DIR="$HOME/.arthas/lib/${ARTHAS_VERSION}/arthas"

cd "$REPO_ROOT"
if [ -f "$HOME_DIR/arthas-boot.jar" ]; then
  echo "Arthas ${ARTHAS_VERSION} déjà installé dans $HOME_DIR"
  exit 0
fi

mvn -q dependency:copy \
  -Dartifact="com.taobao.arthas:arthas-packaging:${ARTHAS_VERSION}:zip:bin" \
  -DoutputDirectory="$REPO_ROOT/target/agents"

mkdir -p "$HOME_DIR"
unzip -oq "$REPO_ROOT/target/agents/arthas-packaging-${ARTHAS_VERSION}-bin.zip" -d "$HOME_DIR"
echo "Arthas ${ARTHAS_VERSION} installé dans $HOME_DIR"
