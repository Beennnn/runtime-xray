# Reprendre ce dépôt

Ce fichier est lu au démarrage de toute session Claude Code ouverte sur ce dépôt. Il
contient ce qu'il faut savoir avant de toucher au code, et l'état des travaux en cours.
Le [README](README.md) explique l'outil ; ici, on explique le dépôt.

---

## Construire et éprouver

```bash
mvn test                            # 154 tests, aucun n'accède au réseau
mvn -DskipTests package             # orchestrator/target/runtime-xray.jar   (175 Ko)
mvn -Pjacoco  -DskipTests package   # runtime-xray-jacoco.jar               (950 Ko)
mvn -Pcomplet -DskipTests package   # runtime-xray-complet.jar               (19 Mo)
```

Les deux derniers profils embarquent les composants d'analyse et les déposent dans
`~/.runtime-xray` au premier lancement : c'est ce qui rend l'outil utilisable sur une
machine sans réseau. Ils ne sont **pas** la construction par défaut, et c'est délibéré —
le jar ordinaire ne redistribue rien, ceux-là redistribuent de l'EPL-2.0 et de
l'Apache-2.0. Voir [THIRD-PARTY.md](THIRD-PARTY.md), qui dit lequel porte quoi.

Deux recettes de bout en bout vivent dans `bin/` : `recette-locale.sh` éprouve ce qu'on
s'apprête à publier, `recette-central.sh` éprouve l'artefact publié. Elles lancent une
vraie JVM observée, ce que les tests unitaires ne font jamais.

## Publier une version

Pousser une étiquette suffit — `.github/workflows/release.yml` fait le reste :

```bash
git tag -a v1.2.0 -m "runtime-xray 1.2.0" && git push origin v1.2.0
```

Le workflow refuse de publier si l'étiquette ne correspond pas à la version du `pom.xml`.
Penser donc à monter cette version **avant** d'étiqueter : trois fichiers, `pom.xml`,
`orchestrator/pom.xml`, `sample-app/pom.xml`.

**Aucun binaire n'est versionné dans ce dépôt.** Git garde chaque exemplaire pour
toujours ; le dépôt pèse 3,3 Mo, un seul jar complet le sextuplerait, et chaque version
suivante s'ajouterait sans retour possible sans réécrire l'histoire. Les artefacts vivent
attachés à une étiquette. Le seul fichier `.jar` suivi est un faux de 40 octets, en
ressource de test.

## Comment l'outil trouve ses composants

C'est le mécanisme le plus souvent touché, et celui qui décide si l'outil démarre sur la
machine visée — laquelle, par hypothèse, n'a pas de réseau. `Toolbox` cherche dans cet
ordre, et ne sort sur le réseau qu'en dernier :

| Ordre | Endroit |
|---|---|
| 1 | `~/.runtime-xray` — le cache que l'outil remplit lui-même |
| 2 | `--composants <rép>`, `COMPOSANTS=`, `RUNTIME_XRAY_COMPOSANTS` |
| 3 | le répertoire du jar, et son sous-répertoire `composants/` |
| 4 | `~/.m2/repository` (ou `MAVEN_REPO_LOCAL`), en disposition Maven |
| 5 | les composants embarqués, pour les éditions qui en portent |
| 6 | le réseau — `--repo` / `MAVEN_REPO`, miroir interne compris |

Les noms de Maven sont reconnus, ceux des distributions officielles aussi
(`jacocoagent.jar`, `jacococli.jar`, `arthas-bin.zip`, un Arthas déjà décompressé). Quand
un fichier déclare sa version dans son manifeste, elle est vérifiée et l'outil se tait si
elle correspond. Quand rien n'aboutit, le message liste tous les chemins essayés : sur une
machine fermée, c'est lui qui doit suffire à s'en sortir, et il doit le rester.

`bin/kit-hors-ligne.sh` assemble le paquet équivalent à emporter, empreintes comprises.

## Conventions

- **Tout est en français** : code, commentaires, messages de commit, noms de tests
  affichés (`@DisplayName`). Les noms de méthodes de test restent en anglais, comme
  l'exige la lisibilité des rapports JUnit.
- **Un commit explique pourquoi**, pas ce que le diff montre déjà. Le sujet est une
  phrase, pas une étiquette.
- **Un test garde une décision.** Les tests d'ici ne vérifient pas des lignes mais des
  choix : que le dépôt Maven soit pointé sur une adresse morte dans `ToolboxTest` n'est
  pas un détail — un test qui passerait en sortant sur le réseau ne prouverait rien.
- **Les versions des composants ont deux sources** : les propriétés du `pom.xml`, qui
  décident de ce qu'on embarque, et les constantes de `Toolbox`, qui décident de ce qu'on
  cherche. `ToolboxTest.thePomVersionsMatchTheCode` les compare — divergentes, elles
  donneraient un jar d'apparence complète qui retéléchargerait tout.
- **Fins de ligne** : `.gitattributes` fige la copie de travail en LF. Sur un poste
  Windows qui avait cloné avant, `git add --renormalize .` une fois. Rien dans le code ne
  doit dépendre de la fin de ligne — c'est ce qui a cassé douze tests en août 2026.

## Pièges de plateforme

- **Pas de mesure de temps sous Windows** : async-profiler ne publie que des binaires
  Linux et macOS. La couverture et les valeurs fonctionnent ; l'outil le dit au lancement
  plutôt que d'échouer.
- **Terminal Windows** : l'outil écrit en UTF-8. Un terminal en cp850 — le défaut de bien
  des postes — rend les accents illisibles. Corriger côté terminal (mintty → Options →
  Text → UTF-8), ou lancer avec `-Dstdout.encoding=cp850`. Ne pas « corriger » cela dans
  le code : quand la sortie part dans un tuyau, la JVM ne peut pas savoir quel jeu de
  caractères le terminal utilisera.
