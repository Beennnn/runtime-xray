# L'outil intégré — l'utiliser sur n'importe quel projet Java

> `runtime-xray.jar` produit, à partir d'une exécution réelle, **une page unique** montrant
> tout le code qui a tourné, son arbre d'appel et les valeurs passées à une méthode.
> Aucune licence, aucune connexion pendant l'exécution.

**[→ Exemple de sortie](https://beennnn.github.io/runtime-xray/multi/)**

## Ce qu'il faut avoir

**Java, et rien d'autre.** L'outil est un jar sans aucune dépendance : ni Python, ni shell,
ni Maven, ni rien à installer au préalable.

```bash
curl -LO https://github.com/Beennnn/runtime-xray/releases/download/v1.2.2/runtime-xray-cli-1.2.2.jar
```

Un jar déjà construit, à télécharger et lancer. Aucun clone n'est nécessaire. Préparation
d'une machine sans réseau : [Se procurer l'outil](../../README.md#se-procurer-loutil).

Les trois outils d'observation (JaCoCo, async-profiler, Arthas) sont cherchés **d'abord sur
la machine** : le cache `~/.runtime-xray`, un répertoire désigné par `--composants`, le
voisinage du jar, puis le dépôt Maven local — celui que le moindre `mvn` d'entreprise a déjà
rempli. Le téléchargement depuis un dépôt Maven, celui de l'éditeur ou le miroir interne via
`MAVEN_REPO`, n'intervient qu'en dernier recours, et ce qui en vient est mis en cache.

**Au deuxième lancement, plus rien ne sort sur le réseau.** C'est ce qui rend l'outil
utilisable en environnement déconnecté. Trois façons d'y arriver, selon ce dont on dispose :
préparer le cache d'avance, poser les fichiers à côté du jar, ou emporter une édition qui
porte ses composants — voir [Préparer une machine sans
réseau](../../README.md#préparer-une-machine-sans-réseau).

Quand un composant manque vraiment, le message d'erreur liste tous les chemins essayés : sur
une machine fermée, il est fait pour suffire.

## Le lancer

### En une ligne

```bash
java -jar runtime-xray.jar \
  --java "java -jar target/mon-appli.jar" \
  --root "com.exemple.moteur.Calculateur::calculer" \
  --classes target/mon-appli.jar \
  --sources src/main/java
```

Quatre paramètres, et un seul est vraiment obligatoire (`--java`). Les autres se
comprennent mieux avec leur raison d'être :

| Paramètre | Ce qu'il désigne | Pourquoi il existe |
|---|---|---|
| `--java` | la commande à exécuter, **telle quelle** | l'outil ne lance pas votre application à sa façon : il lance la vôtre |
| `--root` | la méthode dont on veut les valeurs et le chemin d'appel | capturer les valeurs de *toutes* les méthodes produirait un volume ingérable ; on en désigne une |
| `--classes` | où trouver le bytecode analysé | sans lui, pas de couverture — voir juste en dessous, ce n'est pas forcément `target/classes` |
| `--sources` | les racines des sources | pour afficher le code annoté ligne à ligne plutôt que des noms de méthodes |

### `--classes` : normalement inutile

Le bytecode à analyser est déterminé automatiquement. Trois sources sont consultées dans
l'ordre, et **chacune est vérifiée avant d'être retenue** — un chemin deviné mais faux
serait pire que pas de devinette, puisque l'erreur ne se verrait qu'au rapport :

| Source | Ce qu'elle donne | Cas couverts |
|---|---|---|
| Les **arguments réels de la JVM observée**, lus sur le système | le `-jar` lancé, ou les **répertoires** du `-cp` | `java -jar`, `java -cp`, et tout script ou lanceur qui finit par appeler `java` |
| La **commande configurée**, si la précédente est illisible | idem | JVM trop brève pour être observée |
| La **convention du projet**, si le répertoire existe | `target/classes`, `build/classes/java/main`, `out/production/classes`, `bin` | `mvn exec:java`, `./gradlew run` — où le classpath applicatif n'apparaît sur aucune ligne de commande |

Les trois cas ont été vérifiés de bout en bout sur le programme de démonstration, sans
jamais passer `--classes`.

**Les jar de dépendances sont volontairement écartés.** Un classpath réel en compte des
dizaines ; les analyser tous produirait un rapport où le code du projet pèse un pour cent
du total, et où « qu'est-ce qui a tourné ? » n'a plus de réponse lisible. Le code que l'on
possède est un répertoire de classes, ou le jar que l'on a soi-même lancé.

**Quand le préciser quand même** — c'est le seul cas où il reste utile :

```bash
--classes target/classes:libs/noyau-1.4.jar   # analyser une dépendance interne EN PLUS
--classes target/mon-appli-boot.jar           # un jar « gras » : BOOT-INF/classes et les
                                              # jar de BOOT-INF/lib sont parcourus
--classes modules/facturation/target/classes  # restreindre à un module précis
```

Le premier cas est le plus intéressant en reprise de code : la bibliothèque maison livrée
compilée est justement celle dont personne n'a le modèle mental, et rien n'oblige à en avoir
les sources pour voir qu'elle a tourné.

### `--hide` : ne pas voir ce qui n'est pas votre code

Le JDK est toujours replié — personne n'ouvre `java.util.ArrayList` pour comprendre son
application. La même chose vaut pour des bibliothèques qui n'en font pas partie mais que
l'on considère comme de l'infrastructure : un journal, un client HTTP, un cadriciel.

```bash
--hide "org.slf4j, ch.qos.logback"
```

Où passe cette frontière n'est pas une question technique : elle dépend de ce que l'équipe
possède et de ce qu'elle subit. D'où un réglage plutôt qu'une liste codée en dur.

Le temps des paquets masqués **n'est pas perdu** : il est attribué à la méthode applicative
qui les a appelés, exactement comme pour le JDK. Masquer déplace l'information, ça ne la
supprime pas.

**On peut aussi l'apprendre en lisant le rapport.** Dans l'onglet **Code**, chaque classe
et chaque paquet portent un bouton **filtrer** au survol ; le chemin affiché au-dessus du
source rend chaque niveau de paquet cliquable de la même façon. La liste s'accumule dans la
page et y **reste** — le navigateur la retient d'un chargement à l'autre, il n'y a rien à
enregistrer.

Le bouton *« Ne plus le mesurer non plus »* est un second geste, facultatif : il copie la
ligne `HIDDEN_PACKAGES="…"` à coller dans la configuration, pour que les prochaines
exécutions ne **mesurent** même plus ce code — collecte plus courte, rapport plus léger. Une
page statique ne peut pas écrire dans votre fichier de configuration ; c'est aussi ce qui
lui permet d'être publiée telle quelle.

### Avec un fichier de configuration

Il n'y a rien à copier : **le fichier est généré s'il n'existe pas**, avec les valeurs par
défaut, des commentaires et des exemples pour chaque clé.

```bash
java -jar runtime-xray.jar --config mon-projet.conf
# ✅ Fichier de configuration généré : mon-projet.conf
#    Renseigner au minimum JAVA_CMD, puis relancer.

java -jar runtime-xray.jar --config mon-projet.conf
```

Sans aucun argument, l'outil cherche `runtime-xray.conf` dans le répertoire courant et le
crée s'il manque — on peut donc démarrer en tapant simplement
`java -jar runtime-xray.jar`.

## Comment il s'accroche à l'application

C'est le point qui conditionne tout : **rien n'est imposé sur la façon de lancer Java.**
La commande fournie est exécutée telle quelle, et les agents d'analyse sont injectés par
la variable d'environnement **`JAVA_TOOL_OPTIONS`**, que toute JVM lit à son démarrage.

Fonctionne donc avec :

```bash
--java "java -jar app.jar"
--java "mvn -q exec:java -Dexec.mainClass=com.exemple.Main"
--java "./gradlew run"
--java "./scripts/demarrer-en-recette.sh"
```

Aucune modification du code analysé, aucun changement dans le build.

## Les paramètres

### Ce qu'on lui donne

| Paramètre | Obligatoire | À quoi il sert |
|---|:--:|---|
| `JAVA_CMD` / `--java` | ✅ | La commande qui lance l'application, exécutée telle quelle |
| `CLASSES_DIR` / `--classes` | non | Le bytecode analysé. **Déterminé tout seul** dans les cas usuels — voir ci-dessous. On ne l'écrit que pour analyser autre chose |
| `ROOT_METHOD` / `--root` | — | `paquet.Classe::methode` : la fonction racine, la seule dont les valeurs de paramètres seront capturées |
| `SOURCE_DIRS` / `--sources` | — | Les sources, pour afficher le code annoté. Plusieurs racines : séparées par `:` |
| `HIDDEN_PACKAGES` / `--hide` | — | Paquets à taire comme le JDK, ex. `org.slf4j`. Leur temps revient à l'appelant |
| `CLASS_FILTER` / `--filter` | — | Restreint les mesures de temps au code applicatif, ex. `com/exemple/*`. Déduit du paquet de `ROOT_METHOD` si absent |
| `OUT_DIR` / `--out` | — | Répertoire de sortie (défaut `runtime-xray-out`) |
| `RUN_NAME` / `--name` | — | Nom lisible de *cette* exécution. Elles s'accumulent, et la vue permet de passer de l'une à l'autre |

### Les réglages de collecte

| Paramètre | Défaut | À quoi il sert |
|---|---|---|
| `ATTACH_AFTER` / `--attach-after` | 8 s | Délai avant d'inspecter les valeurs. L'application doit avoir démarré **et** être encore en train de travailler |
| `MAX_SECONDS` / `--max-seconds` | 600 s | Garde-fou : au-delà, l'exécution est interrompue et les rapports sont produits quand même |
| `WATCH_COUNT` | 10 | Nombre d'appels dont on capture les valeurs |
| `TRACE_COUNT` | 10 | Nombre d'invocations dont on trace le chemin d'appel. Plus il y en a, plus de lignes portent l'annotation « appelle … » |
| `MAVEN_REPO` / `--repo` | Maven Central | D'où récupérer les composants d'analyse, une seule fois. Sur un réseau fermé, y mettre le miroir interne |
| `COMPOSANTS` / `--composants` | — | Répertoire où les composants sont **déjà** présents. Ce qu'il contient prime sur tout téléchargement. Les noms de Maven (`org.jacoco.agent-0.8.13-runtime.jar`) comme ceux des distributions officielles (`jacocoagent.jar`) sont acceptés |
| `--no-values` | — | Ne capture pas les valeurs. Les pourcentages de temps deviennent exacts, puisque plus rien n'instrumente |

### Ce qu'on accepte de payer

Trois informations, trois coûts. Sur un gros code, on ne les prend pas toutes du premier
coup — **[Réduire l'empreinte sur un gros code](empreinte.md)** détaille la marche à suivre.

| Paramètre | Défaut | À quoi il sert |
|---|---|---|
| `NIVEAU` / `--niveau` | `complet` | Jusqu'où observer : `couverture` (JaCoCo seul), `arbre` (+ échantillonnage), `complet` (+ valeurs). Le premier réglage à baisser quand la mesure coûte trop cher |
| `COVER_INCLUDES` / `--cover` | tout | Classes que JaCoCo instrumente, ex. `com.exemple.*`. **Sans lui, toute classe chargée est instrumentée**, dépendances comprises : c'est le poste de coût principal |
| `SAMPLE_INTERVAL_MS` / `--interval` | 1 ms | Intervalle d'échantillonnage des piles. À 10 ms, dix fois moins de relevés |
| `EXPORT` / `--export` | — | Réécrit les mesures pour d'autres outils : `perf`, `cpuprofile`, `lcov`, `valeurs`, ou `tout` — voir [les exports](exports.md) |

### Les modes qui ne mesurent rien

| Option | Ce qu'elle fait |
|---|---|
| `--print-options` | Affiche la ligne d'agents à coller dans une commande Java **qu'on ne contrôle pas** — un service systemd, un conteneur, un serveur d'application. L'outil ne lance rien : il vous rend le texte |
| `--report-only` | Réassemble la page depuis des exécutions déjà sur le disque, sans rien relancer. Utile après avoir annoté une exécution, ou changé les paquets masqués |
| `--serve [port]` | Sert le rapport (défaut : 8787) et laisse la page **écrire ses annotations** à côté des exécutions, puis la régénère. Plusieurs personnes peuvent annoter à la fois — voir [Annoter les exécutions](annotations.md) |
| `--serve-host <hôte>` | Interface d'écoute (défaut : `127.0.0.1`). `0.0.0.0` pour un serveur partagé |
| `--serve-token [secret]` | Garde le rapport servi par un **secret partagé**, demandé une fois puis retenu douze heures. Sans valeur, un secret est tiré au sort et affiché. `XRAY_SERVE_TOKEN` fait de même sans l'exposer dans `ps`. Sans l'option, rien n'est demandé : à réserver à la boucle locale ou à un réseau déjà filtré — voir [ce que ce secret vaut](annotations.md#ce-que-ce-secret-vaut-et-ce-quil-ne-vaut-pas) |

Ces options se combinent : `--report-only --serve` sert des mesures déjà prises, sans rien
relancer, et c'est le mode d'un serveur où l'on dépose des résultats venus d'ailleurs.

### Choisir la fonction racine

C'est le seul paramètre qui demande une décision. La fonction racine est **le point
d'entrée métier** : celle dont on veut savoir avec quoi elle a été appelée, et ce qu'elle
a déclenché. Un traitement, un calcul, une commande — pas un `main`, pas un accesseur.

Capturer les valeurs de *toutes* les méthodes n'est pas envisageable : sur un chemin
d'exécution fréquenté, la trace se compte en centaines de mégaoctets. D'où le choix d'une
racine, et d'une seule.

### Si l'application est trop rapide

L'inspection des valeurs s'attache à une JVM **vivante**. Si le programme se termine en
deux secondes, il n'y a rien à observer. Deux réponses : augmenter la charge de travail
(le nombre d'itérations, la taille du jeu de données), ou réduire `ATTACH_AFTER`.
Augmenter la charge est préférable — un profil sur deux secondes ne veut pas dire
grand-chose de toute façon.

### Pendant que ça tourne

Une analyse dure ce que dure l'application observée. Tant qu'elle travaille, une bande
d'activité se réécrit sur une seule ligne :

```
   03:12  ··░▒▓███▓▒░··████▓▒·······    cpu 41,3 s | sortie 812,0 Ko
```

Chaque carré vaut une seconde, et sa densité le nombre de cœurs occupés pendant cette
seconde-là : `·` rien, `█` plusieurs. On y lit ce qu'on veut savoir sans attendre la fin —
que le programme travaille encore, qu'il s'est mis à attendre une saisie ou une socket,
qu'une phase de calcul vient de commencer.

Cette bande **ne mesure rien**. Le temps processeur est celui que le système comptabilise
de toute façon pour les processus lancés, la taille de sortie celle d'un fichier déjà
écrit : aucune option n'est ajoutée à la JVM observée, rien n'est échantillonné en plus.
Le rapport est identique, qu'elle s'affiche ou non.

Elle ne s'affiche que devant un terminal. Redirigée dans un fichier ou lue par une chaîne
d'intégration, une ligne qui se réécrit chaque seconde ne donnerait que du bruit : dans ce
cas l'outil se tait, comme avant. `NO_COLOR` retire les couleurs sans retirer les carrés —
c'est la densité qui porte l'information, la couleur ne fait que la redoubler.

## Ce qu'on obtient

```
runtime-xray-out/
├── index.html                   ← la vue intégrée : ouvrir le dossier suffit à la trouver
├── rapport.md                   ← le même contenu, lisible dans une forge (voir plus bas)
├── noms.json                    ← annotations de tout le rapport (facultatif, échangeable)
└── runs/
    └── 20260821-004121-recette-v2/
        ├── run-context.json     ← identifiant, nom, commande, heure, machine…
        ├── config.json          ← annotations de CETTE exécution (facultatif, prioritaire)
        ├── execution.log        ← la sortie de l'application
        ├── jacoco/html/         ← la couverture détaillée, tout le code analysé
        ├── jacoco-focused/html/ ← la même, restreinte aux classes qui ont tourné
        ├── classes-executees/   ← le bytecode retenu pour ce second rapport
        ├── async-profiler/      ← les piles repliées, plus le profil rendu par l'outil
        │                          lui-même (flamegraph.html et son inverse)
        ├── arthas/              ← les valeurs capturées et la trace d'invocation
        └── exports/             ← les mêmes mesures pour d'autres outils, si --export
```

Tout ce que les outils ont écrit reste ainsi atteignable. La page en donne la liste, groupée
par outil : c'est ce qui permet de vérifier la synthèse quand elle surprend — et de rester
exploitable si elle ne s'ouvre plus.

### `rapport.md` — pour une forge

À côté de la page, l'outil écrit un **`rapport.md`** : le même contenu, réduit à ce qui se
lit sans interaction. Il existe parce qu'une forge affiche un fichier `.html` comme du
**code source**, pas comme une page ; le Markdown, lui, est rendu nativement par GitHub
comme par GitLab, dépôt privé compris, sans Pages ni service tiers.

## Plusieurs exécutions dans un même rapport

Chaque lancement crée une exécution dans `runs/`, et la vue affiche un **sélecteur** en
haut : on passe de l'une à l'autre sans changer de page. Dans la liste des méthodes, un
repère indique celles qui ont **aussi** tourné ailleurs (`+2`) et celles qui sont
**propres à cette exécution** (`seule ici`) — c'est ce qui rend la comparaison lisible.

**[→ Exemple à trois exécutions](https://beennnn.github.io/runtime-xray/multi/)**

### Nommer, décrire, étiqueter, élaguer

Une exécution porte trois identités qu'il ne faut pas confondre — l'**identifiant**, qui ne
change jamais ; le **nom donné au lancement** par `--name`, qui dit ce qu'on croyait
mesurer ; le **nom posé après coup**, qui dit ce qu'on a compris. À défaut de nom, c'est
l'identifiant qui désigne l'exécution : rien n'est inventé.

S'y ajoutent une description libre, des étiquettes clé/valeur, et l'élagage de l'arbre
d'appel — couper une branche, ou repartir d'un nœud. Tout cela se saisit dans la fiche
**bandeau d'identité** posé sous le nom de l'exécution, et s'écrit dans un fichier à côté
des mesures. Les gestes — enregistrer, exporter, importer — vivent dans la barre du haut :
le rapport se lit, il ne se remplit pas.

Trois façons de faire vivre ces annotations, qui coexistent :

| Mode | Comment | Ce que ça donne |
|---|---|---|
| **Dans le navigateur** | ouvrir la page | immédiat ; **exporter** rend le fichier, **importer** reprend celui d'un collègue |
| **Sur son poste** | `--serve` | la page écrit à côté des exécutions et le rapport est régénéré |
| **Serveur partagé** | `--serve --serve-host 0.0.0.0` | on y dépose les résultats, tout le monde lit et **annote en parallèle** |
| **… fermé par un secret** | `--serve-token` en plus | une page d'entrée, une session de douze heures ; complète un filtrage réseau, ne le remplace pas |

```bash
java -jar runtime-xray.jar --report-only --out runtime-xray-out --serve
```

L'annotation d'une exécution s'écrit dans **son répertoire** (`config.json`), ce qui la
fait voyager avec la mesure ; un `<exécution>-config.json` posé à côté, ou le `noms.json`
commun, restent lus — dans cet ordre de priorité.

**Tout le détail — les trois modes, l'écriture concurrente, les emplacements de fichier,
les formats et les réserves : [Annoter les exécutions](annotations.md).**

### Rassembler des exécutions venues d'ailleurs

La vue accepte **n'importe quelle arborescence** : elle considère comme exécution tout
répertoire contenant un `run-context.json`. Rassembler des répertoires de sortie produits
sur des machines différentes dans un même dossier, puis :

```bash
java -jar runtime-xray.jar --report-only --out dossier-commun --sources src/main/java
```

### Lire la vue

Deux onglets à gauche mènent au même code : **Code** (par paquet et par classe) et
**Exécutions** (l'arbre d'appel, avec les exécutions pour racines). Les cases à cocher
choisissent les exécutions affichées.

Ce que la page montre exactement, ce qu'elle replie et pourquoi, comment filtrer un paquet
d'un clic et comment déplier une boucle passage par passage : **[Lire le
rapport](lire-le-rapport.md)**.

### Le contexte est écrit dans le rapport

Programme lancé et ses arguments, méthode racine, heure de début et de fin, durée,
machine, utilisateur, système, nombre de cœurs, version de Java, répertoire de travail,
versions des outils. Un rapport retrouvé trois mois plus tard dit de quoi il parle.

## Publier le résultat

La sortie est un **ensemble de fichiers statiques**, et `index.html` est **autonome** :
toutes ses données sont embarquées dans le fichier. C'est ce qui rend la diffusion simple.

### En environnement professionnel

| Support | Comment | Remarques |
|---|---|---|
| **GitLab Pages** | Un job CI publie `runtime-xray-out/` comme artefact de pages | Le plus propre si la forge est un GitLab auto-hébergé. Gratuit, intégré, hors ligne |
| **GitLab — artefact de job** | `artifacts: paths: [runtime-xray-out/]` + `expose_as: "Rapport d'exécution"` | Un lien apparaît **directement dans la merge request**. Consultable en ligne sans télécharger |
| **Confluence** | Joindre `index.html` à une page, ou utiliser la macro *HTML* | ⚠️ La macro HTML est souvent désactivée par les administrateurs. La pièce jointe, elle, marche toujours : le fichier étant autonome, un clic l'ouvre |
| **SharePoint / Teams** | Déposer le dossier ; ouvrir `index.html` | SharePoint peut forcer le téléchargement selon la configuration |
| **Un partage réseau** | Copier le dossier | Le plus simple, et le seul qui marche vraiment partout |
| **Nexus / Artifactory** | Publier le dossier comme artefact *raw* | Souvent déjà présents, souvent oubliés pour cet usage |
| **Serveur web interne** | `python3 -m http.server` ou n'importe quel Apache | Suffisant pour une démonstration |

**Recommandation** : si la forge est un **GitLab**, l'artefact de job avec `expose_as` est
la meilleure option — le rapport devient un lien dans la merge request, sans hébergement
supplémentaire ni téléchargement. Sinon, la **pièce jointe Confluence** est la voie la plus
sûre, parce qu'elle ne dépend d'aucune permission d'administration.

### Un clic depuis GitHub, sans hébergement ni téléchargement

Quatre voies, selon que le dépôt est public ou privé :

| Voie | Dépôt public | Dépôt privé | Coût |
|---|:--:|:--:|---|
| **GitHub Pages** | ✅ un clic | ⚠️ exige une offre payante | gratuit en public |
| **raw.githack.com** | ✅ un clic | ⚠️ URL avec jeton, déconseillé | gratuit |
| **htmlpreview.github.io** | ✅ un clic | ❌ | gratuit |
| Artefact d'un workflow | ⚠️ téléchargement | ⚠️ téléchargement | minutes facturées en privé |

**Sur un dépôt public, GitHub Pages est la réponse** : c'est gratuit, c'est un lien, rien à
installer. C'est ce qu'utilise ce dépôt.

**Sans activer Pages**, l'astuce tient au fait que `index.html` est autonome : des services
comme `raw.githack.com` ou `htmlpreview.github.io` servent un fichier brut de GitHub avec
le bon type MIME, ce qui suffit à l'afficher. Il suffit de préfixer l'URL du fichier :

```
https://raw.githack.com/<compte>/<dépôt>/main/runtime-xray-out/index.html
```

⚠️ Ce sont des **services tiers**, donc hors du périmètre d'un environnement déconnecté, et
ils ne conviennent pas à du code confidentiel. Pour un usage interne, GitLab Pages ou un
partage réseau restent les bonnes réponses.

## Ses limites

- **Les valeurs ne sont capturées que sur la méthode racine** — c'est un choix de volume,
  pas une limite technique.
- **Les pourcentages de temps sont surestimés sur cette méthode racine** : l'inspection des
  valeurs s'intercale à chaque appel. La vue le signale et replie les frames concernées.
  Lancer avec `--no-values` donne des temps exacts, sans les valeurs.
- **Les mesures de temps échantillonnent** : une méthode très brève peut n'y pas figurer.
  La liste du code exécuté, elle, est exhaustive — c'est la couverture qui fait foi.
- **Une exécution, un rapport** : rien n'agrège plusieurs exécutions.
