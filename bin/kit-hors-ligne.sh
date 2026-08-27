#!/usr/bin/env bash
# Assemble le kit à transporter sur une machine sans réseau : le jar, et les trois
# composants d'analyse que l'outil irait sinon chercher sur un dépôt Maven.
#
# Le README décrit déjà une façon de remplir le cache : lancer une analyse quelconque
# sur une machine qui a accès. Elle suppose une analyse qui aboutit — donc une plateforme
# où async-profiler existe, ce qui exclut Windows, et une application à observer. Ce
# script ne demande ni l'un ni l'autre : il ne fait que télécharger, et fonctionne donc
# depuis n'importe quel poste ayant accès au dépôt Maven.
#
# Les versions ne sont pas écrites ici : elles sont lues dans Toolbox.java, qui est la
# seule source de vérité. Un kit ne peut donc pas contenir autre chose que ce que le jar
# du même dépôt réclamera.
#
#   bin/kit-hors-ligne.sh                    # depuis Maven Central
#   MAVEN_REPO=https://miroir.interne/maven2 bin/kit-hors-ligne.sh
set -uo pipefail

DEPOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="$DEPOT/orchestrator/target/runtime-xray.jar"
TOOLBOX="$DEPOT/orchestrator/src/main/java/lab/xray/Toolbox.java"
REPO="${MAVEN_REPO:-https://repo1.maven.org/maven2}"
REPO="${REPO%/}"
KIT="$DEPOT/target/kit-hors-ligne/runtime-xray-kit"
ZIP="$DEPOT/target/runtime-xray-kit-hors-ligne.zip"

echo "Kit hors ligne — dépôt Maven : $REPO"

# --------------------------------------------------------------- versions et jar

version(){ grep -o "$1 = \"[^\"]*\"" "$TOOLBOX" | head -1 | cut -d'"' -f2; }
JACOCO="$(version JACOCO_VERSION)"
ARTHAS="$(version ARTHAS_VERSION)"
ASYNC="$(version ASYNC_VERSION)"
if [ -z "$JACOCO" ] || [ -z "$ARTHAS" ] || [ -z "$ASYNC" ]; then
  echo "  ❌ versions illisibles dans $TOOLBOX" >&2; exit 1
fi
echo "   JaCoCo $JACOCO · async-profiler $ASYNC · Arthas $ARTHAS"

if [ ! -f "$JAR" ]; then
  echo "   le jar n'est pas construit — mvn -DskipTests package"
  (cd "$DEPOT" && mvn -q -DskipTests package) || { echo "  ❌ construction du jar" >&2; exit 1; }
fi

rm -rf "$DEPOT/target/kit-hors-ligne"
mkdir -p "$KIT/.runtime-xray"
cp "$JAR" "$KIT/"

# ------------------------------------------------------------------ composants
#
# Les noms de fichiers sont ceux que Toolbox.artifact() recompose pour chercher dans le
# cache : s'en écarter donnerait un kit d'apparence complète, qui retéléchargerait tout.

recupere(){ # groupe artefact version classifieur extension
  local chemin="$1" nom="$2" ver="$3" cls="$4" ext="$5"
  local fichier="$nom-$ver${cls:+-$cls}.$ext"
  local url="$REPO/${chemin//.//}/$nom/$ver/$fichier"
  printf '   %-42s' "$fichier"
  if curl -fsSL --retry 3 --connect-timeout 20 -o "$KIT/.runtime-xray/$fichier" "$url"; then
    echo "$(( $(wc -c < "$KIT/.runtime-xray/$fichier") / 1024 )) Ko"
  else
    echo "❌"; echo "      $url" >&2; return 1
  fi
}

{
  recupere org.jacoco       org.jacoco.agent  "$JACOCO" runtime jar &&
  recupere org.jacoco       org.jacoco.cli    "$JACOCO" nodeps  jar &&
  recupere tools.profiler   jfr-converter     "$ASYNC"  ""      jar &&
  recupere tools.profiler   async-profiler    "$ASYNC"  ""      jar &&
  recupere com.taobao.arthas arthas-packaging "$ARTHAS" bin     zip
} || { echo "  ❌ un composant manque : kit incomplet, rien n'est assemblé" >&2; exit 1; }

# ------------------------------------------------------------------ compétences
#
# Deux fichiers Markdown, sans dépendance. Ils voyagent avec le kit parce que c'est
# précisément sur la machine isolée qu'on ne peut plus aller lire la documentation.

mkdir -p "$KIT/skills"
cp -r "$DEPOT/skills/." "$KIT/skills/"
printf '   %-42s%s\n' "skills/" "conduire une campagne, et lire un rapport"

# ---------------------------------------------------------------- mode d'emploi

cat > "$KIT/LIRE-MOI.txt" <<EOF
Runtime X-Ray — kit hors ligne
==============================

Contenu
  runtime-xray.jar   l'outil (aucune dépendance : un JDK 21+ suffit)
  .runtime-xray/     les trois composants d'analyse, déjà téléchargés
  skills/            deux compétences pour un assistant : conduire une campagne,
                     et lire un rapport sans en tirer de fausse conclusion.
                     À recopier dans .claude/skills/ du projet, ou à donner
                     telles quelles — ce sont deux fichiers Markdown.

Installation sur la machine isolée
  Linux / macOS   cp -r .runtime-xray ~/
  Windows         xcopy /E /I .runtime-xray %USERPROFILE%\\.runtime-xray

  Puis lancer normalement :  java -jar runtime-xray.jar ...
  L'outil trouve ses composants dans ce cache et n'ouvre aucune connexion.

  Sans rien copier, ça marche aussi : les fichiers de .runtime-xray posés à côté du
  jar sont pris tels quels, comme ceux d'un répertoire désigné par --composants, et
  comme ceux du dépôt Maven local (~/.m2/repository) s'il les contient déjà.

Composants
  JaCoCo $JACOCO          couverture   (org.jacoco.agent, org.jacoco.cli)
  async-profiler $ASYNC       temps        (async-profiler, jfr-converter)
  Arthas $ARTHAS           valeurs      (arthas-packaging)

  Archives publiées sur Maven Central, non modifiées ; leurs licences respectives
  s'appliquent (voir THIRD-PARTY.md du dépôt).

  Le paquet Arthas est décompressé et la bibliothèque native d'async-profiler est
  extraite au premier lancement, pour la plateforme courante. La mesure du temps
  demande Linux ou macOS : async-profiler ne publie pas de binaire Windows.

Vérification des empreintes (SHA-256)
  Linux / macOS   sha256sum -c SHA256SUMS.txt
  Windows         certutil -hashfile <fichier> SHA256
EOF

(cd "$KIT" && find . -type f ! -name SHA256SUMS.txt -print0 | sort -z \
  | xargs -0 sha256sum > SHA256SUMS.txt)

# ------------------------------------------------------------------- archive
#
# zip si le poste l'a ; sinon l'archiveur du JDK, qui produit le même format — c'est la
# seule chose dont on soit sûr ici, puisque l'outil réclame déjà un JDK.

rm -f "$ZIP"
if command -v zip >/dev/null 2>&1; then
  (cd "$DEPOT/target/kit-hors-ligne" && zip -qr "$ZIP" runtime-xray-kit)
else
  (cd "$DEPOT/target/kit-hors-ligne" && jar --create --file "$ZIP" --no-manifest runtime-xray-kit)
fi
[ -s "$ZIP" ] || { echo "  ❌ archive non produite" >&2; exit 1; }

echo
echo "  ✅ $ZIP ($(( $(wc -c < "$ZIP") / 1024 / 1024 )) Mo)"
echo "     à joindre à une release, ou à transporter tel quel."
