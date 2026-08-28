#!/usr/bin/env bash
# Installing Arthas WITHOUT going through arthas.aliyun.com.
#
# The `arthas-boot` launcher normally downloads its modules from the publisher, which makes
# it unusable in a disconnected environment. The complete package is nevertheless published
# on Maven Central: it is enough to lay it down at the expected place, and the launcher never
# goes out on the network again (the --arthas-home option).
#
# In a genuinely cut-off environment: fetch the zip once from a connected machine (or from
# the internal Maven mirror), carry it across, and replay the unzip part.
set -euo pipefail

ARTHAS_VERSION="4.3.4"
REPO_ROOT="$(git rev-parse --show-toplevel)"
HOME_DIR="$HOME/.arthas/lib/${ARTHAS_VERSION}/arthas"

cd "$REPO_ROOT"
if [ -f "$HOME_DIR/arthas-boot.jar" ]; then
  echo "Arthas ${ARTHAS_VERSION} already installed in $HOME_DIR"
  exit 0
fi

mvn -q dependency:copy \
  -Dartifact="com.taobao.arthas:arthas-packaging:${ARTHAS_VERSION}:zip:bin" \
  -DoutputDirectory="$REPO_ROOT/target/agents"

mkdir -p "$HOME_DIR"
unzip -oq "$REPO_ROOT/target/agents/arthas-packaging-${ARTHAS_VERSION}-bin.zip" -d "$HOME_DIR"
echo "Arthas ${ARTHAS_VERSION} installed in $HOME_DIR"
