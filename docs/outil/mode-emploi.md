# L'outil intégré — l'utiliser sur n'importe quel projet Java

> `runtime-xray.jar` produit, à partir d'une exécution réelle, **une page unique** montrant
> tout le code qui a tourné, son arbre d'appel et les valeurs passées à une méthode.
> Aucune licence, aucune connexion pendant l'exécution.

**[→ Exemple de sortie](https://beennnn.github.io/runtime-xray/multi/)**

## Ce qu'il faut avoir

**Java, et rien d'autre.** L'outil est un jar sans aucune dépendance : ni Python, ni shell,
ni Maven, ni rien à installer au préalable.

Les trois outils d'observation (JaCoCo, async-profiler, Arthas) sont récupérés **une seule
fois** depuis un dépôt Maven — celui de l'éditeur, ou le miroir interne de l'entreprise via
`MAVEN_REPO` — et mis en cache dans `~/.runtime-xray`.

**Au deuxième lancement, plus rien ne sort sur le réseau.** C'est ce qui rend l'outil
utilisable en environnement déconnecté : on prépare le cache une fois sur une machine qui a
accès, on transporte le répertoire, et c'est tout.

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

### `--classes` : le jar applicatif convient, dépendances comprises

C'est le paramètre qui prête le plus à confusion, parce que son nom suggère un répertoire.
Il accepte en réalité **un répertoire, un jar, ou une liste des deux** séparée par `:` —
et il descend dans les jar imbriqués.

```bash
--classes target/classes                      # le cas simple
--classes target/mon-appli.jar                # le jar livré : même résultat
--classes target/mon-appli-boot.jar           # un jar « gras » Spring Boot : BOOT-INF/classes
                                              # ET les dépendances de BOOT-INF/lib sont vues
--classes target/classes:libs/noyau-1.4.jar   # le projet + une dépendance interne à analyser
```

Le dernier cas est le plus intéressant en reprise de code : la bibliothèque maison livrée
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
#    Renseigner au minimum JAVA_CMD et CLASSES_DIR, puis relancer.

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
| `CLASSES_DIR` / `--classes` | souvent déduit | Le bytecode analysé : un répertoire, un jar, ou une liste séparée par `:`. **Déduit tout seul** quand `JAVA_CMD` est un `java -jar …` — le jar *est* le bytecode |
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
| `MAVEN_REPO` / `--repo` | Maven Central | D'où récupérer les composants d'analyse, une seule fois. **Le seul réglage qui compte pour un réseau fermé** : y mettre le miroir interne |
| `--no-values` | — | Ne capture pas les valeurs. Les pourcentages de temps deviennent exacts, puisque plus rien n'instrumente |

### Les deux modes qui ne mesurent rien

| Option | Ce qu'elle fait |
|---|---|
| `--print-options` | Affiche la ligne d'agents à coller dans une commande Java **qu'on ne contrôle pas** — un service systemd, un conteneur, un serveur d'application. L'outil ne lance rien : il vous rend le texte |
| `--report-only` | Réassemble la page depuis des exécutions déjà sur le disque, sans rien relancer. Utile après avoir renommé une exécution, ou changé les paquets masqués |

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

## Ce qu'on obtient

```
runtime-xray-out/
├── index.html                  ← la vue intégrée : ouvrir le dossier suffit à la trouver
├── noms.json                   ← renommer une exécution après coup (facultatif)
└── runs/
    └── 20260820-221009-recette-v2/
        ├── run-context.json    ← identifiant, nom, commande, heure, machine…
        ├── execution.log       ← la sortie de l'application
        ├── jacoco/html/        ← le rapport de couverture détaillé
        ├── async-profiler/     ← les mesures de temps brutes
        └── arthas/             ← les valeurs capturées
```

## Plusieurs exécutions dans un même rapport

Chaque lancement crée une exécution dans `runs/`, et la vue affiche un **sélecteur** en
haut : on passe de l'une à l'autre sans changer de page. Dans la liste des méthodes, un
repère indique celles qui ont **aussi** tourné ailleurs (`+2`) et celles qui sont
**propres à cette exécution** (`seule ici`) — c'est ce qui rend la comparaison lisible.

**[→ Exemple à trois exécutions](https://beennnn.github.io/runtime-xray/multi/)**

### Trois identités, et pourquoi

| | Ce que c'est | Change ? |
|---|---|---|
| **UUID** | Généré au lancement, écrit dans `run-context.json` | **jamais** — c'est la clé stable |
| **Nom d'origine** | Le `--name` donné au lancement | jamais — il permet de revenir en arrière |
| **Nom d'affichage** | Ce que la vue montre | oui, via `noms.json` |

Renommer après coup ne touche à aucune exécution : c'est un fichier à la racine du
répertoire commun, qui associe un identifiant à un nom.

```json
{
  "8BF7DA2E-1C5A-4435-A3E3-4FE50CD41A2F": "Recette — jeu de données réduit"
}
```

Supprimer l'entrée rétablit le nom d'origine. Le script rappelle l'identifiant à la fin de
chaque exécution, prêt à être collé.

### Rassembler des exécutions venues d'ailleurs

La vue accepte **n'importe quelle arborescence** : elle considère comme exécution tout
répertoire contenant un `run-context.json`. Rassembler des répertoires de sortie produits
sur des machines différentes dans un même dossier, puis :

```bash
java -jar runtime-xray.jar --report-only --out dossier-commun --sources src/main/java
```

### Lire la vue

1. **À gauche**, choisir un onglet puis cliquer :
   **Code exécuté** (tout ce qui a tourné, du plus coûteux au moins coûteux),
   **Arbre d'appel** (la même chose en hiérarchie), **Packages** (par fichier).
2. **Au centre**, le code apparaît colorié : vert exécuté, rouge jamais atteint, jaune
   partiel. À droite de chaque ligne, les appels qui en partent et leur coût.
3. **Masquer le code non exécuté** retire les classes mortes et replie les lignes rouges.
4. **Restreindre le périmètre** à ce qui tourne sous une méthode, sans descendre d'appel
   en appel.
5. **Les valeurs des paramètres** s'affichent dans le bandeau du bas, sur la méthode
   racine — repérable dans la liste à sa pastille *valeurs*.

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
