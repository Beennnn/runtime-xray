# L'outil intégré — l'utiliser sur n'importe quel projet Java

> `runtime-xray.sh` produit, à partir d'une exécution réelle, **une page unique** montrant
> tout le code qui a tourné, son arbre d'appel et les valeurs passées à une méthode.
> Aucune licence, aucune connexion pendant l'exécution.

**[→ Exemple de sortie](https://beennnn.github.io/runtime-xray/vue-integree/)**

## Ce qu'il faut avoir

| | Pourquoi | Comment |
|---|---|---|
| **Java** | c'est ce qu'on analyse | déjà là |
| **Python 3** | assemble la page finale | déjà présent sur macOS et la plupart des Linux |
| **async-profiler** | les temps et l'arbre d'appel | `brew install async-profiler`, ou définir `ASYNC_PROFILER_LIB` |
| **Maven** | uniquement pour **récupérer** JaCoCo et Arthas, une seule fois | ou déposer les jars à la main dans `~/.runtime-xray` |

Les composants récupérés sont mis en cache dans `~/.runtime-xray` : **au deuxième lancement,
plus rien n'est téléchargé**. C'est ce qui rend l'outil utilisable en environnement
déconnecté — on prépare le cache une fois, on le transporte s'il le faut.

## Le lancer

### En une ligne

```bash
./runtime-xray.sh \
  --java "java -jar target/mon-appli.jar --profil demo" \
  --root "com.exemple.moteur.Calculateur::calculer" \
  --classes target/classes \
  --sources src/main/java
```

### Avec un fichier de configuration

```bash
cp runtime-xray.conf.example mon-projet.conf
# … éditer …
./runtime-xray.sh --config mon-projet.conf
```

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

| Paramètre | Obligatoire | À quoi il sert |
|---|:--:|---|
| `JAVA_CMD` / `--java` | ✅ | La commande qui lance l'application |
| `CLASSES_DIR` / `--classes` | ✅ | Les `.class` compilés — sans eux, pas de couverture |
| `ROOT_METHOD` / `--root` | — | `paquet.Classe::methode` : **la fonction racine**, la seule dont les valeurs de paramètres seront capturées |
| `SOURCE_DIRS` / `--sources` | — | Les sources, pour afficher le code annoté. Plusieurs racines : séparées par `:` |
| `CLASS_FILTER` / `--filter` | — | Restreint les mesures de temps au code applicatif, ex. `com/exemple/*`. Déduit du paquet de `ROOT_METHOD` si absent |
| `OUT_DIR` / `--out` | — | Répertoire de sortie (défaut `runtime-xray-out`) |
| `ATTACH_AFTER` | — | Délai avant l'inspection des valeurs (défaut 8 s) |
| `MAX_SECONDS` | — | Garde-fou si l'application ne se termine pas (défaut 600 s) |
| `WATCH_COUNT` | — | Nombre d'appels dont on capture les valeurs (défaut 5) |
| `--no-values` | — | Ne capture pas les valeurs : les pourcentages de temps deviennent exacts |

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
├── index.html              ← la vue intégrée : ouvrir le dossier suffit à la trouver
├── run-context.json        ← quel programme, quels arguments, quand, sur quelle machine
├── execution.log           ← la sortie de l'application
├── jacoco/html/            ← le rapport de couverture détaillé
├── async-profiler/         ← les mesures de temps brutes
└── arthas/                 ← les valeurs capturées
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
