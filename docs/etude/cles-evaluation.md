# Clés d'évaluation — comment les obtenir, où les ranger

> Cette page décrit les démarches à faire pour intégrer les outils sous licence à l'étude.
> **Ce sont des actions humaines** : elles demandent une adresse e-mail nominative et,
> selon l'éditeur, une validation manuelle. Aucune ne peut être automatisée depuis ce
> dépôt, et **aucune n'a été entreprise** — cette page recense les procédures, elle ne
> rend compte d'aucune démarche.

## Règle absolue : aucune clé dans le dépôt

Le dépôt est **public**. Une clé de licence commise reste dans l'historique git même après
suppression du fichier, et les scanners automatiques la trouvent en quelques minutes.
Trois protections, cumulées :

1. **Les clés vivent hors du dépôt**, dans
   `~/gdrive/claude/projects/pro/artifacts/runtime-xray-licences/` — hors de `~/dev`, donc
   hors de toute arborescence git, et répliqué automatiquement (Drive → NAS).
2. **Les scripts les lisent par variable d'environnement**, jamais depuis un fichier du dépôt :

   ```bash
   # ~/gdrive/claude/projects/pro/artifacts/runtime-xray-licences/licences.sh
   export JPROFILER_LICENSE_KEY="..."
   export JPROFILER_LICENSE_NAME="..."
   export YOURKIT_LICENSE_KEY="..."
   ```

   ```bash
   source ~/gdrive/claude/projects/pro/artifacts/runtime-xray-licences/licences.sh
   ./tools/jprofiler/collect.sh
   ```

3. **Le `.gitignore` bloque les formes usuelles** (`*.license`, `licence*.sh`, `.env.local`)
   — filet de sécurité, pas ligne de défense principale : la vraie protection est que le
   fichier ne soit pas dans l'arborescence.

> Alternative si l'on préfère ne rien laisser en clair sur le disque : le trousseau macOS.
> `security add-generic-password -s runtime-xray -a jprofiler -w '<clé>'` pour déposer,
> `security find-generic-password -s runtime-xray -a jprofiler -w` pour relire dans un script.

## Avant de demander quoi que ce soit : ce qu'on a déjà

Demander un essai n'a de sens que si l'on sait **ce qu'on cherche à obtenir en plus**.
Voici l'état vérifié du socle gratuit, sur la même application, sous Java 21 :

| Besoin | Outil | Ce qu'on obtient, concrètement |
|---|---|---|
| Lignes exécutées | JaCoCo | Site HTML, code colorié ligne à ligne, 91,1 % des instructions couvertes, classes mortes en rouge |
| Arbre d'appel cumulé | async-profiler | HTML autonome dépliable, % de temps par branche, 71 % des échantillons sur des méthodes métier |
| Arbre d'appel d'**un** appel | Arthas `trace` | L'arbre d'une invocation, **avec les numéros de ligne**, branches conditionnelles comprises |
| **Valeurs des paramètres** | Arthas `watch` | `@Mode[CAR] @Weather[SUNNY] @TimeOfDay[RUSH_HOUR]` et la valeur de retour |

**Les trois besoins de l'étude sont couverts, pour 0 €, hors ligne.** L'essai commercial ne
sert donc plus à combler un manque : il sert à répondre à une question plus étroite.

## Ce qu'on attend des outils commerciaux — et pourquoi

### Le gain principal : l'agrégation par valeur de paramètre

C'est le seul point où l'on peut raisonnablement attendre mieux, et il mérite d'être
énoncé précisément parce que c'est lui qui justifierait la dépense.

**Ce qu'Arthas fait** : il montre les valeurs de **quelques appels individuels** — les
cinq prochains, par exemple. Utile pour comprendre un cas, insuffisant pour raisonner sur
un ensemble.

**Ce que JProfiler revendique** (*method splitting by parameter values*) : **scinder l'arbre
d'appel par valeur d'argument**. Sur 16 000 000 d'appels, cela donnerait un sous-arbre pour
`mode=CAR`, un autre pour `mode=TRAIN`, avec leurs temps respectifs — c'est-à-dire la
réponse à *« quel contexte coûte cher ? »*, et non seulement *« que s'est-il passé sur cet
appel-ci ? »*.

Arthas ne sait pas agréger ainsi. **C'est le gain à vérifier, et c'est le seul qui vaille
549 $.**

### Le gain secondaire : un seul outil au lieu de trois

Aujourd'hui : un agent pour la couverture, un agent pour le profil, une console attachée
pour les valeurs — trois procédures, trois formats de sortie. Un profileur commercial
réunirait le profil et les valeurs dans une session unique, avec la corrélation
temps / mémoire / verrous dans les mêmes vues.

Gain réel, mais **de confort** : la décision n° 1 l'a explicitement classé secondaire.

### Ce qu'il ne faut PAS en attendre

- **La couverture de lignes** — ni JProfiler ni YourKit ne la produisent. JaCoCo reste
  nécessaire dans tous les cas.
- **Un rapport lisible par un non-développeur** — leurs instantanés s'exportent, mais ils
  ne valent pas le HTML annoté de JaCoCo sur ce terrain. Le canal retenu (décision n° 3)
  ne changerait pas.
- **L'intégration IntelliJ** — déclassée par la décision n° 5, puisqu'on ne peut pas
  supposer que tout le monde a Ultimate.

### Le critère de décision, en une phrase

> Si le *method splitting by parameter values* apporte quelque chose qu'Arthas ne donne pas
> en trois commandes, la licence se justifie. Sinon, non.

C'est ce qu'il faut aller vérifier pendant les 10 et 15 jours d'essai — pas « est-ce que
l'outil est bon », mais **cette question-là**.

## JProfiler (ej-technologies)

| | |
|---|---|
| **Démarche** | Formulaire en ligne : [ej-technologies.com/jprofiler/trial](https://www.ej-technologies.com/jprofiler/trial) |
| **Informations demandées** | Nom, société, adresse e-mail. La clé arrive **par e-mail** — l'adresse doit être exacte |
| **Durée** | 10 jours *(à confirmer sur le formulaire au moment de la demande)* |
| **Données conservées par l'éditeur** | Nom, société, e-mail et IP anonymisée ; un e-mail de relance est envoyé pendant l'évaluation |
| **Téléchargement** | [ej-technologies.com/download/jprofiler](https://www.ej-technologies.com/download/jprofiler/files) |
| **Licence open source** | Gratuite pour les projets open source — à demander à l'éditeur |
| **⚠️ Hors ligne** | La question n'est pas le téléchargement (autorisé) mais **l'activation** : une clé saisie une fois et validée localement convient ; une vérification en ligne **à chaque lancement** serait éliminatoire. À faire confirmer par l'éditeur |

**Prix (relevés le 2026-08-20 sur la boutique de l'éditeur)** — licences perpétuelles :

| | Sans support | Avec 1 an de support et mises à jour |
|---|---|---|
| Licence simple | **549 $** | 768 $ |
| Licence flottante (1 utilisateur simultané) | **2 199 $** | 3 078 $ |

## YourKit Java Profiler

| | |
|---|---|
| **Démarche** | Téléchargement direct : [yourkit.com/download](https://www.yourkit.com/download/) — l'essai est intégré |
| **Durée** | **15 jours** |
| **Licence open source** | **Gratuite**, en contrepartie d'un lien vers YourKit sur les pages du projet. À demander au service commercial |
| **Académique / scientifique** | Abonnement à **99 $/an** par poste, **999 $/an** pour un établissement entier |
| **⚠️ Hors ligne** | Les licences **flottantes** interrogent par défaut un **serveur de licences dans le cloud pendant l'exécution** — c'est éliminatoire. Une option **auto-hébergée** existe : c'est elle qu'il faut demander. Les licences *seat*, validées localement, ne posent pas ce problème |

**Prix (relevés le 2026-08-20 sur [la page tarifaire](https://www.yourkit.com/java/profiler/purchase/))** :

| | Abonnement annuel | Perpétuel |
|---|---|---|
| 1 poste (Basic) | 449 $ | **549 $** |
| 1 poste (Advanced) | 579 $ | 713 $ |
| 5 postes (Basic) | 1 259 $ | 1 539 $ |
| Flottante 1 utilisateur (Basic) | 2 249 $ | 2 749 $ |
| Flottante 5 utilisateurs (Basic) | 2 699 $ | 3 299 $ |

## IntelliJ IDEA Ultimate (JetBrains)

| | |
|---|---|
| **Démarche** | Essai de 30 jours depuis la Toolbox ou le site JetBrains |
| **Licence gratuite** | Programmes existants pour l'open source, l'enseignement et les étudiants |
| **⚠️ Hors ligne** | L'activation JetBrains passe normalement par un compte en ligne ; un **serveur de licences auto-hébergé** existe pour les licences organisationnelles. À vérifier selon le cadre |
| **Utilité pour l'étude** | Limitée : le profiler intégré embarque async-profiler, déjà disponible gratuitement en ligne de commande |

## Ce qu'il faut mesurer une fois les clés obtenues

Pour que l'essai serve à décider et pas seulement à regarder, rejouer **exactement** le
protocole des outils gratuits — même `sample-app`, même exécution de 10 s, sous Java 21
puis Java 25 :

1. **La capture des valeurs de paramètres** — c'est le seul point qui justifie la dépense.
   Vérifier concrètement que le *method splitting by parameter values* de JProfiler sépare
   bien les appels de `RoutePlanner.travelTimeMinutes` selon le mode de transport passé.
2. **Le surcoût réel** — comparer le temps affiché par `Metrics` avec et sans l'outil.
   Les 10 s de référence donnent une base de comparaison immédiate.
3. **Ce qui sort de l'outil** — un instantané exportable et lisible par un non-développeur,
   ou bien une interface qu'il faut installer pour consulter ? C'est le **critère n° 2**.
4. **L'activation hors ligne** — la question qui peut tout disqualifier. À traiter en
   premier, avant même d'installer.

Consigner les résultats dans [le comparatif](comparatif.md) en faisant passer la ligne de
⛔ à ✅, et déposer les sorties dans `reports-demo/generated/<outil>/`.
