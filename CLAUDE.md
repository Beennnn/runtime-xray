# Reprendre ce dépôt

Ce fichier est lu au démarrage de toute session Claude Code ouverte sur ce dépôt. Il
contient ce qu'il faut savoir avant de toucher au code, et l'état des travaux en cours.
Le [README](README.md) explique l'outil ; ici, on explique le dépôt.

---

## Construire et éprouver

```bash
mvn test                            # 248 tests, aucun n'accède au réseau
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
git tag -a v1.3.0 -m "runtime-xray 1.3.0" && git push origin v1.3.0
```

Le workflow refuse de publier si l'étiquette ne correspond pas à la version du `pom.xml`.
Penser donc à monter cette version **avant** d'étiqueter : trois fichiers, `pom.xml`,
`orchestrator/pom.xml`, `sample-app/pom.xml`.

**Les liens de téléchargement se basculent tout seuls.** La dernière étape de
`release.yml` réécrit sur `main`, une fois la release créée, l'URL de téléchargement, le
nom du jar et la ligne « Version » du tableau — dans `README.md` et
`docs/outil/mode-emploi.md`. Ils désignent la release **publiée**, donc ils ne peuvent
pas se réécrire avant : nommer d'avance une étiquette qui n'existe pas offre un 404 au
premier lecteur. Ne pas les toucher à la main entre l'étiquette et la fin du workflow.

Les autres numéros de version du README parlent d'autre chose — la licence MIT de la
1.0.0 déjà publiée, l'exemple d'étiquetage — et les trois expressions sont écrites
étroitement pour ne pas y toucher. Leur délimiteur est `#` et non `|` : la ligne
« Version » est une ligne de tableau Markdown, pleine de barres verticales, et les
échapper dans une commande délimitée par `|` donne un motif que BSD sed lit de travers —
essayé, il réécrivait le fichier entier.

**Aucun binaire n'est versionné dans ce dépôt.** Git garde chaque exemplaire pour
toujours ; le dépôt pèse 3,3 Mo, un seul jar complet le sextuplerait, et chaque version
suivante s'ajouterait sans retour possible sans réécrire l'histoire. Les artefacts vivent
attachés à une étiquette. Le seul fichier `.jar` suivi est un faux de 40 octets, en
ressource de test.

## Publier la page d'accueil

Rien à faire : `.github/workflows/page.yml` recopie `site/index.html` vers la racine de
`gh-pages` dès que la source change sur `main`. La page se modifie donc **dans `site/`**,
jamais sur `gh-pages`.

Le workflow ne touche qu'`index.html`, et c'est délibéré : `gh-pages` porte aussi `demo/`,
`jfr/` et `multi/` — des rapports engendrés qui n'existent nulle part dans `main`.
Reconstruire la branche à partir de `site/` les effacerait.

`workflow_dispatch` republie à la demande, pour le cas où quelqu'un aurait corrigé
directement sur `gh-pages`. Le déclenchement automatique est étroit — seul
`site/index.html` le provoque — parce qu'une publication à chaque commit sur `main`
n'apprendrait rien et ferait du bruit dans l'historique de la branche.

Le mécanisme naît d'une dérive : le 24 août 2026, la page servie était en retard d'un
paragraphe sur sa source, depuis assez longtemps pour que personne ne sache dire depuis
quand. Deux fichiers qui ont l'air d'exister chacun de leur côté ne divergent pas
bruyamment.

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

## Quand le rapport ne montre pas ce qu'on attendait

C'est le mode d'échec le plus coûteux de l'outil, parce qu'il est **silencieux** : un panneau
de code vide se lit exactement comme un panneau qu'on n'a pas su remplir. Trois choses
distinctes peuvent manquer, elles se corrigent de trois façons opposées, et rien dans la page
ne permettait de les distinguer.

- **`diagnostic.json`** est écrit à côté de la page à chaque assemblage, sans qu'on le
  demande. Il porte l'environnement, la configuration du lancement, les racines réellement
  ouvertes, les exécutions et leurs rapports, et le rapprochement classe par classe. C'est le
  fichier à réclamer quand quelqu'un signale un rapport décevant — il évite le
  aller-retour de captures d'écran. Il ne contient **aucun secret** ; un test le garde.
- **La section « Analyse des sources »** de la vue d'ensemble le rend lisible : l'arbre
  paquet par paquet, coloré par ce qui manque à chaque classe, et une recherche qui répond à
  « cette classe, tu l'as trouvée, et où ? ».
- **Le panneau à la place du code** dit la clé cherchée, les racines consultées et la ligne
  exacte à ajouter à la configuration.

**Les quatre restrictions ne restreignent pas la même chose**, et c'est la confusion la plus
fréquente — on élargit un filtre qui n'y était pour rien, on relance, on obtient le même
rapport. `--sources` ne commande que l'affichage du code ; `--root` la capture des valeurs et
le filtre de temps ; `--filter` la mesure du temps seule ; `--cover` l'instrumentation JaCoCo.
Le tableau est dans la page, écrit à partir du symptôme et non de la documentation.

**Quand il manque des sources, l'outil les cherche** — et ne les devine jamais. Deviner une
racine d'après une convention serait commode là où on n'en a pas besoin, et faux là où on en
aurait besoin : sur la machine où l'application tourne loin de son code, la convention ne
désigne rien, ou pire, désigne les sources d'un autre projet. Du code faux affiché en face
d'une couverture coûte plus cher qu'un panneau vide, parce qu'on le croit.

`Sources.searchRoots` cherche donc, et compte. Elle explore le voisinage du bytecode
analysé, celui des racines déjà configurées et le répertoire de lancement — jamais au-delà —
et ne retient un fichier que si le **paquet qu'il déclare** produit exactement une clé
manquante. Un homonyme venu d'un autre projet ne compte pas ; un test le garde. Chaque
proposition arrive avec son chiffre — « cette racine résoudrait 27 des 27 classes sans
source » — et la ligne `SOURCE_DIRS=` à recopier. Quand rien ne concorde, elle le dit.

**L'index des sources est bâti sur le paquet déclaré**, pas sur le chemin relatif à la racine
passée. `SOURCE_DIRS` peut donc désigner le projet entier, le répertoire de sources, ou un
répertoire de paquet : cela retombe sur ses pieds. C'est ce qui a cassé le 26 août 2026 —
une racine d'un cran trop haute suffisait à décaler l'index entier, donc « Source
indisponible » sur les 447 classes d'une analyse.

## La bande d'activité pendant l'attente

`Progress` écrit une ligne qui se réécrit pendant que l'application observée travaille.
Trois décisions la tiennent, et elles sont liées :

- **Elle ne mesure rien.** Le temps processeur vient de `ProcessHandle.Info.totalCpuDuration`
  — le système le compte de toute façon — et la taille de `execution.log` d'un fichier déjà
  écrit. Rien n'est ajouté à la JVM observée. C'était la condition posée : un affichage de
  confort qui déplacerait la mesure ferait mentir le rapport qu'il accompagne.
- **Un carré par paquet aurait été plus parlant**, et n'a pas été retenu : les deux seules
  façons d'obtenir le code réellement atteint en cours de route sont un `-Xlog:class+load`
  dans `JAVA_TOOL_OPTIONS` — inconnu d'une JVM 8, qui refuserait alors de démarrer, or c'est
  précisément le parc visé — ou un `output=tcpserver` sur l'agent JaCoCo, qui déplace
  l'écriture du `.exec` hors du chemin qui fonctionne. Le coût portait sur la capture ; le
  gain, sur l'affichage.
- **Le compte est en cœurs occupés**, pas en pourcentage de la machine. Sur un serveur à
  trente-deux cœurs, une application qui en sature un travaille à plein régime : ramenée à
  3 %, elle s'afficherait endormie.

La bande, la page de `--suivi` et les messages de la console sont **en anglais**, comme le
reste de ce que l'utilisateur voit. Les nombres suivent : `41.3 s`, `812.0 KB`, un
horodatage en `yyyy-MM-dd HH:mm:ss`. Un nom d'option ou une valeur en français — `--niveau
complet`, `--export tout` — reste accepté à vie ; ce n'est simplement plus lui qu'on
affiche.

Elle se tait hors terminal (`System.console() == null`) : dans un tuyau ou un journal
d'intégration, une ligne par seconde n'apprend rien et noie le reste. `RUNTIME_XRAY_PROGRESSION`
la réclame quand même — un shell qui en lance un autre met souvent un tuyau au milieu alors
qu'un opérateur regarde bien un écran au bout ; `=0` impose l'inverse. Les caractères
`· ░ ▒ ▓ █` existent en CP850 comme en UTF-8, pour la même raison que le reste des messages.

## Suivre une exécution qui n'a pas de terminal

C'est le prolongement de la bande, et la réponse au cas qu'elle ne couvrait pas : l'outil
lancé au fond de scripts imbriqués, sa sortie dans un tuyau, et **personne qui ne voit
plus rien**.

- **`progression.jsonl` s'écrit toujours**, à la racine de `--out` — donc à un chemin qu'on
  connaît sans connaître le nom de l'exécution. Une ligne par seconde, `tail -f` suffit. La
  dernière porte `"event":"end"` : sans elle, un lecteur ne saurait pas s'arrêter.
- **`--suivi [port]` sert une page** qui relit ce fichier, et rien d'autre. Elle montre ce
  qu'une suite de lignes JSON montre mal — la *forme* de l'exécution. Boucle locale
  seulement : elle affiche une commande et la sortie d'une application.
- **Le calcul de charge est dans `Progress`, et nulle part ailleurs.**
  `Progress.tick` le rend, `Follow` le recopie. Deux calculs séparés finiraient par
  diverger, et le fichier contredirait la bande sans que rien ne le signale.
- **Un échec d'écriture du fil n'emporte jamais la mesure** — c'est un confort, elle est ce
  qu'on est venu chercher ; un test le garde, comme il garde que la fin du journal servie
  soit bornée à 64 Ko.

## Couverture cumulée

Une campagne de recette, c'est dix exécutions, et la question posée devant le code est « est-ce
que **quelque chose** a couvert cette ligne ? ». Deux mécanismes y répondent, et ils sont
complémentaires :

- **Dans la page**, cocher « cumuler » réunit les exécutions **cochées** et garde pour chaque
  ligne *laquelle* l'a couverte, en pastilles. Cela marche sur n'importe quel sous-ensemble,
  hors ligne, sans rien relancer — parce que l'union se calcule sur des données déjà là.
- **`jacoco-fusion/`** porte le rapport JaCoCo de toutes les exécutions fusionnées
  (`jacococli merge` puis `report`), pour le chiffre qui fait foi et qu'on transmet. Il perd
  en revanche l'attribution : une fois les `.exec` additionnés, plus rien ne dit qui a couvert
  quoi. Il n'est produit qu'à partir de deux exécutions, et seulement si le bytecode est connu.

JaCoCo ne sait pas choisir ses exécutions à l'affichage : son rapport est un rendu statique,
calculé sur les `.exec` qu'on lui donne. Un sous-ensemble arbitraire demanderait donc un
rapport par combinaison — c'est précisément ce que le cumul dans la page évite.

## Ce qu'on donne à lire à une IA

`faits.jsonl` s'écrit à côté de la page, un objet JSON par ligne, chacune se comprenant
seule — son vocabulaire est dans sa première ligne, parce qu'une documentation posée à côté
se perd dans le premier zip. `--contexte "une question"` en tire un extrait borné et prêt à
coller. Trois décisions le tiennent, chacune gardée par un test dans `ContextTest` :

- **Ce qui n'a PAS été mesuré passe avant les chiffres**, et n'est jamais élagué par le
  budget. Un lecteur qui voit les chiffres d'abord les a déjà interprétés quand il arrive
  aux réserves ; un modèle produira en plus une réponse assurée sur un zéro qui ne mesurait
  rien.
- **Rien n'est coupé en silence** — ni par le budget, ni quand une famille est vide. Un bloc
  vide se lit exactement comme un bloc qu'on n'a pas su remplir, ici comme dans la page.
- **La question a deux rôles**, et c'est ce qui la rend ambiguë : elle choisit les faits,
  grossièrement, et elle voyage dans le paquet. Un texte libre laisse croire à une
  compréhension qui n'existe pas — d'où l'annonce sur **stderr** de ce qui a été retenu, la
  table des mots-clés dans `--help`, et `--familles` pour les scripts, qui court-circuite
  l'interprétation. Une famille inconnue s'arrête là où une phrase inconnue donne la vue
  d'ensemble : la première vient d'un script qui a un défaut, la seconde d'un humain qui
  cherche.
- **Le format des faits est en 2.0, et il n'y a pas de migrateur.** Ce n'est pas un oubli :
  `faits.jsonl` est *dérivé* des mesures de `runs/`, comme la page, le diagnostic et les
  blocs. Un `--report-only` le réécrit entièrement dans le format courant, avec au passage
  tous les correctifs accumulés — un migrateur n'aurait renommé que des clés dans un fichier
  resté périmé par ailleurs, et il aurait fallu le maintenir puis penser à le supprimer. Un
  rapport 1.0 est donc *refusé* à la lecture, avec la commande qui le régénère ; et quand
  `runs/` a disparu, le message le dit au lieu de proposer une commande qui échouerait. Les
  noms de familles de la 1.0 restent acceptés par `--families`, à vie.
- **Les lignes sont recopiées telles quelles.** Relire puis réécrire un JSON rendrait `41`
  en `41.0` : le format ne distingue pas l'entier du flottant, et ce détail fait douter du
  reste.

`skills/` porte deux compétences à recopier dans le `.claude/skills/` d'un projet —
conduire une campagne, lire un rapport — et le kit hors ligne les emporte, parce que c'est
sur la machine isolée qu'on ne peut plus aller lire la documentation. Ce sont deux fichiers
Markdown, mais ils sont **tenus par `SkillsTest`** : toute option qu'ils citent doit exister
dans le `switch` de `Main`, et la table du vocabulaire doit coïncider avec
`Facts.VOCABULARY`, dans les deux sens. Une option renommée ici et laissée écrite là-bas
n'échoue nulle part — elle envoie quelqu'un dans le mur avec « Option inconnue » pour toute
explication, sur la machine où il ne peut rien vérifier.

**L'outil ne parle à personne** : aucun nom de fournisseur, aucune clé, aucun appel réseau.
Le format des requêtes change tous les six mois ; le besoin de donner un texte borné et
exact, non. `bin/demander.sh` porte les trois enveloppes du moment (`openai`, `anthropic`,
`gemini`) côte à côte, en une trentaine de lignes chacune — et « openai » y désigne la
**forme** de la requête, pas le fournisseur. Les valeurs capturées ne sortent jamais : elles
restent dans leur bloc, sur le disque, et deux tests le gardent.

## La langue de la vue

La page s'ouvre **en anglais**. Un rapport s'envoie, se joint à un ticket, se lit par
quelqu'un qui n'a pas lancé la mesure : rien ne dit qu'il parle français. Le sélecteur
`EN | FR` de l'en-tête rend le français à qui le veut, et un navigateur qui annonce le
français l'obtient d'emblée. Tout autre navigateur lit l'anglais — il n'y a pas de
troisième langue, et il n'y en aura pas. Deux lettres, pas de drapeau : un drapeau nomme un
pays, pas une langue.

**Le gabarit est resté en français, et c'est la traduction qui parcourt le DOM.** La page
fait cinq mille lignes et la moitié de son texte est composée dans des concaténations de
balises ; y semer des clés aurait demandé de toucher chacune de ces lignes — donc de risquer
un `id`, un sélecteur ou une comparaison sur chaque — pour un gain nul. Le dictionnaire vit
dans un bloc à part, à la fin du fichier, et rien d'autre n'a bougé. Trois règles le tiennent :

- **On ne traduit que des chaînes entières et connues.** Pas de remplacement de mots. Un nom
  de classe, une valeur capturée, une ligne de code de l'application observée ne sont jamais
  un libellé du dictionnaire. La **cellule de code** est en plus écartée du parcours — et
  elle seule, parce que les replis et les annotations d'appel de la même ligne sont, eux, du
  texte de l'outil. Les libellés de l'arbre le sont aussi : une classe nommée `Comment` ne
  doit pas devenir `How`.
- **Le français d'origine est gardé**, pas retraduit à l'envers. Un aller-retour sur un
  dictionnaire non injectif perdrait du texte ; là il rend l'original à l'octet près.
- **Un `title` réécrit après coup est surveillé comme un nœud ajouté.** La page en réaffecte
  plusieurs — le bouton « tout déplier » change le sien à chaque bascule — et cela ne crée
  aucun nœud, donc ne se voit pas dans `childList`.

**Le décrochage est le mode d'échec, et il est silencieux** : quelqu'un reformule un libellé
dans le gabarit, le dictionnaire ne le connaît plus, et la vue anglaise affiche du français
sans que rien ne le signale. `ViewLanguageTest` vérifie pour cela le corps statique en entier —
chaque phrase française qu'il porte doit avoir sa traduction. Le reste du texte naît en
JavaScript et ne se relève qu'au rendu : la vérification s'y fait au navigateur, avant de
pousser, en relevant les chaînes affichées et en n'en gardant aucune française.

## Conventions

- **L'outil parle anglais, et le code aussi désormais.** La bascule s'est faite par
  étapes — l'aide, les exemples, les mots-clés, le format des faits, la vue, les messages
  console, puis les noms du code, ses commentaires et ses `@DisplayName`, y compris ceux
  de `sample-app`, dont le code s'affiche dans le rapport. **Tout nouveau code s'écrit
  donc en anglais**, commentaires et `@DisplayName` compris.

  Trois choses restent en français, chacune pour une raison : le **gabarit**
  `dashboard.html`, parce que c'est la traduction qui parcourt son DOM et non l'inverse
  (voir « La langue de la vue ») ; les **scripts de `bin/`** et la **documentation**, qui
  s'adressent à qui reprend le dépôt et non à qui lance l'outil ; les **messages de
  commit**, pour la même raison. Les exemples, eux, sont en anglais **partout**, y
  compris dans une prose française : `com.example.app`, `--contexte "which classes never
  ran?"`. Un exemple français dans une documentation anglaise est ce qui se remarque en
  premier.
- **Un nom d'option français reste accepté pour toujours**, silencieusement, quand son
  équivalent anglais arrivera. Les scripts déjà déployés ne cassent jamais. Seul
  `faits.jsonl` cassera, en format 2.0, et c'est assumé : il a trois jours.
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
- **La CI éprouve les deux systèmes** : `tests.yml` lance `mvn test` sur `ubuntu-latest`
  ET `windows-latest`, en `fail-fast: false` — savoir qu'un défaut est propre à Windows ou
  commun aux deux change ce qu'il faut aller regarder. La construction des trois éditions
  reste sur un seul poste : elle éprouve les profils Maven, rien qui dépende du système.
  Ajoutée le 26 août 2026, après deux défauts Windows qu'une CI Linux ne pouvait pas voir.
- **Une run peut ne jamais être planifiée**, et alors rien ne la débloque : le 26 août 2026,
  l'une est restée « queued » dix-sept heures sans créer un seul job, l'API refusant jusqu'à
  son annulation. La proposition de fusion attendait derrière une vérification qui ne
  viendrait jamais. Le `concurrency` de `tests.yml` en fait la sortie de secours : **pousser
  un commit annule la run précédente et prend sa place**. C'est le seul geste qui marche —
  ni la relance, ni l'annulation. Et les `timeout-minutes` bornent l'autre forme du même
  problème : un travail parti pour six heures, le défaut de GitHub. Ne pas attendre une CI
  muette plus d'une fois : constater, le dire, et pousser.
- **Le tilde n'est un caractère d'interpréteur qu'en tête de mot** : les noms courts 8.3 de
  Windows en portent un — `C:\Users\RUNNER~1`, `C:\PROGRA~1` — et le compter partout
  envoyait la commande à `cmd /c`. Elle s'exécutait, mais `ClassSources` n'y lisait plus le
  `-jar`, donc plus le bytecode : rapport vide, sans explication. Trouvé par le premier
  passage de la CI Windows, le jour même où elle a été ajoutée.
- **Chemins Windows dans une liste** : `SOURCE_DIRS` et `CLASSES_DIR` se séparent par `:`,
  ce qui va de soi sur Unix et pas du tout sur Windows — `C:\projet\src` commence par un `:`
  qui n'est pas un séparateur. `Config.decouper` rend au chemin le `:` qui suit une lettre
  seule en tête de segment et précède un séparateur, et accepte `;` en plus. Sans cela, un
  chemin absolu valide donnait deux entrées inexistantes et l'outil répondait « introuvable »
  sur un chemin que l'utilisateur avait sous les yeux. Découvert le 26 août 2026, quand la
  recherche de racines s'est mise à proposer des chemins absolus.
- **Les tests ne comparent jamais un chemin à un littéral à séparateurs** : `endsWith(
  "projet/src/main/java")` échoue sous Windows alors que le code a trouvé exactement ce qu'il
  fallait. Construire le chemin attendu avec `resolve` et comparer les deux.
- **Le rapport engendre beaucoup de petits fichiers, et c'est ce qui coûte sur un poste
  bardé d'agents de sécurité.** Le compte croît avec le nombre de classes analysées, pas
  avec le volume : JaCoCo écrit deux fichiers HTML par classe, l'outil lui en fait produire
  **deux sites** par exécution — le complet et le ciblé — et y ajoute une copie du bytecode
  de chaque classe exécutée dans `classes-executees/`. Sur une grosse application, une
  campagne de plusieurs exécutions se compte en centaines de milliers de fichiers, de
  quelques kilo-octets chacun.

  Sur Linux, les ouvrir prend quelques secondes. Sur un poste où chaque ouverture de
  fichier traverse une pile de filtres, cela prend des minutes — et **la machine n'y peut
  rien** : la latence se paie en série, une ouverture après l'autre, donc ni la mémoire ni
  les cœurs ne la rachètent. Ce n'est pas propre à Git Bash : un archiveur natif souffre
  autant, ce qui écarte la traduction POSIX de MSYS2 comme cause. La JVM y échappe parce
  qu'elle ouvre peu de fichiers et garde ses handles.

  **`JACOCO_REPORTS` décide de ce qu'on en écrit**, et de rien d'autre : `full` les deux
  sites (le défaut, inchangé), `detailed` le complet seul, `data` ni l'un ni l'autre. Le
  réglage ne touche ni la mesure ni l'affichage — la couverture rendue vient de
  `jacoco.xml`, écrit dans tous les cas, et `Coverage.parse` le lit directement. Sur
  l'application-exemple, une exécution passe de 186 à 101 fichiers en `detailed`, à 9 en
  `data`, avec la même couverture au chiffre près.

  Trois décisions le tiennent, et ce sont elles qu'il faut connaître avant d'y toucher :

  - **Le défaut ne bouge pas.** Aucun rapport existant ne change de forme sans qu'on l'ait
    demandé. C'est ce qui distingue ce réglage d'une correction.
  - **Le filet est au niveau campagne, pas au niveau exécution.** `data` ne retire pas le
    rendu JaCoCo : `jacoco-fusion/` reste, et `jacoco.xml` aussi. C'est ce qui rend la
    valeur agressive défendable — on retire une commodité par exécution, jamais la
    dernière lecture possible.
  - **Une absence n'est jamais silencieuse.** Le groupe JaCoCo de la page est en
    `toujours: true` : un rapport non produit reste nommé, grisé, et le clic donne la
    commande. Sans cela, le réglage aurait fabriqué exactement le mode d'échec que ce
    projet combat partout — un rapport plus pauvre qui ne dit pas qu'il l'est. Chaque
    groupe porte **sa** commande : les formats ouverts se réécrivent des mesures déjà là,
    les sites JaCoCo demandent un autre réglage.

  Le staging des classes exécutées est en **un jar** et non une arborescence :
  `jacococli` accepte les deux et en tire un rapport identique — vérifié, `diff -r` ne
  trouve rien — et la copie passe par le système de fichiers zip, donc les fichiers
  intermédiaires n'existent jamais. Les écrire pour les effacer aurait coûté ce qu'on
  cherche à éviter.

  `runs/` reste par ailleurs le bon candidat à une exclusion antivirus : un répertoire
  unique, qui ne contient que des artefacts engendrés, dont rien n'est exécuté et tout est
  reproductible.
- **Terminal Windows** : l'outil écrit en UTF-8. Un terminal en cp850 — le défaut de bien
  des postes — rend les accents illisibles. Corriger côté terminal (mintty → Options →
  Text → UTF-8), ou lancer avec `-Dstdout.encoding=cp850`. Ne pas « corriger » cela dans
  le code : quand la sortie part dans un tuyau, la JVM ne peut pas savoir quel jeu de
  caractères le terminal utilisera.
