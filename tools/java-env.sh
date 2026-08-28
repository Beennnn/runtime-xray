#!/usr/bin/env bash
# Resolution of the JDK used by all the collectors.
#
# The project evaluates TWO platforms: Java 21 (what exists) and Java 25 (a possible
# target). JAVA_TARGET chooses which — 21 by default, since that is the real platform today.
#
#   JAVA_TARGET=25 ./tools/jfr/collect.sh
#
# Fixing the version explicitly is indispensable: taking "the java on the PATH" would amount
# to documenting capabilities the target platform does not have — a case lived through with
# JFR method tracing (JEP 520), present in 25 and absent in 21.
JAVA_TARGET="${JAVA_TARGET:-21}"

# The patterns are expanded by `ls` and not by the shell: sourced from zsh, a glob with no
# match raises "no matches found" and interrupts the resolution — which silently falls back
# on the java of the PATH (seen: a test meant to run under Java 21 ran under Java 25).
# Expanding the patterns is therefore delegated to an explicit bash.
for candidate in $(bash -c "ls -d \
      '/opt/homebrew/opt/openjdk@${JAVA_TARGET}/libexec/openjdk.jdk/Contents/Home' \
      /opt/homebrew/Cellar/openjdk/${JAVA_TARGET}*/libexec/openjdk.jdk/Contents/Home \
      $HOME/.sdkman/candidates/java/${JAVA_TARGET}* \
      2>/dev/null"); do
  if [ -x "$candidate/bin/java" ]; then
    export JAVA_HOME="$candidate"
    export PATH="$JAVA_HOME/bin:$PATH"
    break
  fi
done

JAVA_MAJOR="$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')"
export JAVA_MAJOR
if [ "${JAVA_MAJOR}" != "${JAVA_TARGET}" ]; then
  echo "⚠️  JAVA_TARGET=${JAVA_TARGET} asked for but java -version says ${JAVA_MAJOR} — resolution failed" >&2
fi
echo "JDK used: $(java -version 2>&1 | head -1)"
