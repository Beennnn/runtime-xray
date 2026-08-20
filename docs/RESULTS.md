# Résultats — solutions retenues et arbitrages à trancher

> Page de synthèse. Le détail outil par outil est dans [COMPARISON.md](COMPARISON.md)
> et dans les [fiches individuelles](tools/). Le protocole est dans [METHODOLOGY.md](METHODOLOGY.md).

## En une phrase

**Aucun outil ne couvre seul les trois besoins.** La couverture de lignes et la capture
des valeurs de paramètres relèvent de deux familles techniques différentes — l'une
instrumente tout le bytecode en permanence, l'autre greffe des sondes sur quelques
méthodes ciblées. La réponse sera donc **une combinaison**, et c'est le deuxième outil
de cette combinaison qui est à arbitrer.

## Ce qui est acquis, et vérifié par l'exécution

Trois outils ont réellement tourné sur l'application-cible. Tous trois sont **gratuits**
et **fonctionnent hors ligne** une fois installés.

### 1. « Par où le code est passé » → **JaCoCo**. Question réglée.

C'est le point le plus important du brief, et c'est le mieux résolu.

- Sortie : un **site HTML** où l'on descend de la vue d'ensemble jusqu'au **code source
  colorié ligne par ligne** — vert exécuté, rouge jamais atteint, jaune branche partielle.
- Mesuré sur `sample-app` : **91,1 % des instructions**, **81,8 % des branches**.
- Aucun serveur, aucun compte, aucune connexion : un dossier de fichiers qu'on ouvre ou
  qu'on envoie tel quel.

![Code source annoté par JaCoCo](assets/shots/jacoco-source-weather.png)

*La branche `SNOW` en rouge n'a jamais été exécutée ; le losange jaune signale une
condition partiellement couverte. Un lecteur non technique voit immédiatement ce qui n'a
pas tourné, sans rien savoir de Java.*

→ [Fiche JaCoCo](tools/jacoco.md)

### 2. « L'arbre d'appel » → **async-profiler**. Meilleur rapport effort/résultat.

- Sortie : un **HTML autonome unique** — flame graph et vue arbre — dépliable, cherchable,
  avec les pourcentages de temps par branche.
- Mise en œuvre : une installation, puis **un seul flag JVM**.
- Après affinage du code d'exemple, **71 % des échantillons tombent sur des méthodes
  métier** ; les cinq premières feuilles du profil sont toutes des classes de l'application.

![Arbre d'appel async-profiler](assets/shots/async-tree.png)

→ [Fiche async-profiler](tools/async-profiler.md)

### 3. Le socle déjà présent → **JFR**, inclus dans le JDK.

Zéro installation, zéro téléchargement — donc **compatible hors ligne par construction**.
Depuis le JDK 25 (JEP 520), il trace une méthode ciblée avec sa pile d'appel :

```
jdk.MethodTiming {
  method = lab.sample.RoutePlanner.travelTimeMinutes(Trip)
  invocations = 16000000
  minimum = 0,000084 ms   average = 0,000836 ms   maximum = 29,1 ms
}
```

→ [Fiche JFR & Mission Control](tools/jfr-jmc.md)

### 4. Les valeurs des paramètres → **Arthas**. Le trou est comblé.

C'était le point manquant, et il ne l'est plus. Arthas s'attache à la JVM en cours et
répond aux deux questions restantes, **gratuitement et hors ligne** :

```
watch lab.sample.RoutePlanner travelTimeMinutes '{params[0].id(), params[0].mode(), ...}'

ts=2026-08-20 20:08:02.619; [cost=5.866458ms] result=@ArrayList[
    @String[TRIP-56], @Mode[CAR], @Weather[SUNNY], @TimeOfDay[RUSH_HOUR],
    @Boolean[false], @Double[52.00806981818182],
]
```

Là où JFR affiche `travelTimeMinutes(Trip)`, Arthas dit **quel** trajet et **ce qu'il a
répondu**. Et sa commande `trace` donne l'arbre d'appel d'**un seul appel**, avec les
**numéros de ligne** :

```
`---[0.416459ms] lab.sample.RoutePlanner:travelTimeMinutes()
    +---[13,35% 0.055583ms ] lab.sample.RoutePlanner:legMinutes() #50
    +---[2,60%  0.010834ms ] lab.sample.traffic.Traffic:applies() #53
    +---[3,06%  0.01275ms  ] lab.sample.weather.WeatherPenalty:applies() #57
```

**L'installation hors ligne était le seul obstacle, et il est levé** : le paquet complet
est sur Maven Central, on le décompresse dans `~/.arthas/`, et `--arthas-home` fait que
le lanceur ne contacte plus jamais l'éditeur.

→ [Fiche Arthas](tools/arthas.md)

## Le socle gratuit couvre les deux priorités — et l'option

| Priorité | Besoin | Outil | Statut |
|---|---|---|---|
| **1** | Lignes exécutées | JaCoCo | ✅ vérifié |
| **1** | Arbre d'appel cumulé, visuel | async-profiler | ✅ vérifié |
| **2 — option** | Arbre d'appel d'un appel + valeurs des paramètres | Arthas | ✅ vérifié |

**Les deux objectifs prioritaires sont atteints avec deux outils seulement.** Arthas vient
en supplément, sur la seconde priorité — ce qui veut dire que même s'il posait problème
(installation, lisibilité de sa sortie), **l'essentiel resterait acquis**.

**Coût : 0 €. Connexion nécessaire : aucune.**

Ce que ça change pour la décision : la licence commerciale ne comblerait aucune capacité
manquante, et ce qu'elle apporterait ne concerne que la **seconde priorité** — réunir
l'arbre d'appel et les valeurs dans une interface unique, et agréger par valeur de
paramètre. **Payer pour l'option, alors que l'objectif est déjà atteint.**

## Analyse de synthèse — ce qui est facile, ce qui l'est moins

### Facile, tout de suite, gratuitement

**Savoir par où le code est passé** est un problème résolu. Un flag JVM, un dossier HTML,
et n'importe qui ouvre le rapport et voit le code colorié ligne à ligne. Aucune
configuration, aucun serveur, aucun compte, aucune connexion. C'est le besoin n° 1 du
brief et il ne demande aucune décision.

**Voir l'arbre d'appel** est presque aussi simple : une installation, un flag, un fichier
HTML unique qu'on déplie et qu'on envoie par mail.

**Publier tout ça pour un non-développeur** s'est révélé plus facile que prévu : les
rapports sont des fichiers statiques, donc [directement publiables en
ligne](https://beennnn.github.io/runtime-xray/). Un lien, rien à installer côté lecteur.

**Restreindre le rapport au code réellement exécuté** fonctionne, avec le mécanisme natif
de JaCoCo et une dizaine de lignes de script — les classes jamais atteintes disparaissent
du rapport.

### Moins facile, mais faisable

**Afficher la couverture dans la forge.** GitLab sait le faire nativement, au prix d'une
conversion au format Cobertura. GitHub ne sait pas, sans service tiers — donc sans
connexion, il ne sait pas du tout. Le canal réaliste hors ligne reste le rapport HTML
publié.

**Faire tenir le tout dans l'IDE.** IntelliJ affiche la couverture dans la marge, et c'est
imbattable pour un développeur — mais rien n'en sort pour quelqu'un d'autre.

**Cibler sans se noyer.** Dès qu'on trace finement, le volume explose : 129 Mo pour dix
secondes sur une méthode chaude. Le traçage se cible méthode par méthode ; c'est une
compétence, pas un bouton.

### Difficile — et c'est là que se joue la décision

**Les valeurs passées en paramètres.** Aucun outil gratuit ne les donne *facilement*.
Les quatre pistes existantes demandent chacune quelque chose : Arthas une installation
hors ligne manuelle et une lecture en console, JMC Agent un XML de sondes et un binaire
difficile à trouver, BTrace et Byteman un script par question, IntelliJ un point d'arrêt
posé à la main dans une session de débogage. **Ce sont toutes des réponses d'expert, aucune
n'est un rapport.** C'est précisément ce que vendent JProfiler et YourKit.

**Réunir les trois besoins dans un seul rapport navigable.** Personne ne le fait. Le
rapport de couverture ignore l'arbre d'appel, le flame graph ignore les lignes mortes, et
aucun des deux ne montre une valeur. Un rapport unifié n'existe pas sur étagère — il
faudrait le construire.

**Masquer les portions non exécutées à l'intérieur d'un fichier.** Retirer une classe
entière, oui. Réduire un fichier à ses seules lignes exécutées : aucun outil recensé ne le
propose.

### Ce que l'argent achète, exactement

Une seule chose, mais elle est nette : **les valeurs de paramètres dans le même outil que
l'arbre d'appel, sans écrire de script**. Ni la couverture (JaCoCo la donne mieux, et
gratuitement), ni le flame graph (même moteur, gratuit), ni la lisibilité pour un
non-développeur — sur ce dernier point, le HTML de JaCoCo est même supérieur à un
instantané de profileur.

549 $ pour combler un trou précis, à comparer à quelques jours d'appropriation d'Arthas ou
du JMC Agent. **C'est tout l'arbitrage n° 1**, et il ne se tranche honnêtement qu'après
l'essai gratuit.

## Les solutions jugées pertinentes

### Option A — Tout gratuit, hors ligne (socle acquis + une piste à valider)

| Besoin | Outil | Statut |
|---|---|---|
| Lignes exécutées | JaCoCo | ✅ vérifié |
| Arbre d'appel | async-profiler | ✅ vérifié |
| Valeurs des paramètres | **JMC Agent** ou **BTrace** ou **Byteman** | 📄 à tester |

**Coût : 0 €.** Le risque porte entièrement sur la troisième ligne : ces trois outils
capturent bien des valeurs, mais au prix d'une configuration (XML de sondes pour JMC
Agent, script pour BTrace, règles pour Byteman) — ce qui pèse contre le **critère n° 1**.

### Option B — Gratuit pour les deux premiers besoins, payant pour le troisième

| Besoin | Outil | Statut |
|---|---|---|
| Lignes exécutées | JaCoCo | ✅ vérifié |
| Arbre d'appel + **valeurs des paramètres** | **JProfiler** ou **YourKit** | ⛔ essai à activer |

L'argument : on n'achète la licence que pour le point précis où le gratuit décroche, et
on obtient au passage l'intégration IntelliJ native (**critère n° 4**).
Ordre de grandeur : ~500 $/licence, ~200 $ en académique — **chiffres du brief, non
vérifiés sur les pages tarifaires**.

### Option C — Reconstruction d'architecture (le « but final » du brief)

**Kieker** est le seul outil identifié qui soit *conçu* pour reconstruire une architecture
à partir de traces d'exécution — ce qui est littéralement l'objectif final annoncé. En
contrepartie, c'est le plus lourd à mettre en œuvre : c'est un cadre de recherche, pas un
outil qu'on lance en trois clics.

→ À considérer **en second temps**, une fois le diagnostic de base outillé.

### Écartées d'office par la contrainte hors ligne

Datadog, New Relic, Dynatrace, Codecov, Coveralls, SonarCloud — toutes SaaS. Et **Arthas**,
pourtant excellent candidat gratuit pour les valeurs de paramètres, n'a **pas pu être
testé ici** : son lanceur télécharge ses modules depuis `arthas.aliyun.com`. Il reste
utilisable hors ligne à condition d'installer le paquet complet à la main au préalable —
c'est une contrainte d'installation, pas un rejet définitif.

## Gains d'un portage vers Java 25

La contrainte est **Java 21**. Elle a un coût mesurable, et il est plus lourd qu'attendu.

Le JDK 25 apporte le **traçage de méthodes dans JFR** (JEP 520). Sous Java 21, ces
réglages n'existent pas — la JVM le dit explicitement :

```
[warning][jfr,start] The .jfc option/setting 'jdk.MethodTiming#filter' doesn't exist.
```

Même programme, même durée, même commande — voici ce que chaque version enregistre :

| Événement JFR | Java 21 | Java 25 |
|---|---|---|
| `jdk.ExecutionSample` (échantillonnage statistique) | 331 | 147 |
| `jdk.MethodTrace` (pile d'appel **à chaque invocation**) | — | **594 877** |
| `jdk.MethodTiming` (compte exact + min/moy/max) | — | **1 événement, 16 000 000 invocations comptées** |

**La différence n'est pas quantitative, elle est de nature.** Java 21 donne un
*échantillonnage* : 331 photos prises au hasard pendant 10 secondes, dont on infère
statistiquement où le temps est passé. Java 25 donne une *mesure* : le nombre exact
d'appels, et la pile complète de chacun.

Concrètement, sur `RoutePlanner.travelTimeMinutes` :

```
jdk.MethodTiming {
  method = lab.sample.RoutePlanner.travelTimeMinutes(Trip)
  invocations = 16000000
  minimum = 0,000042 ms   average = 0,000903 ms   maximum = 19,0 ms
}
```

Sous Java 21, cette ligne n'existe pas — il faut un outil tiers pour l'obtenir.

### Pourquoi Java 25 est plus précis — le mécanisme

Ce n'est pas un réglage plus fin : **c'est un changement de nature de la mesure.**

**Java 21 — échantillonnage.** JFR photographie périodiquement la pile des threads
(événement `jdk.ExecutionSample`). Sur 10 secondes, ça donne quelques centaines de
clichés, à partir desquels on *infère* où le temps est passé. C'est une méthode
statistique : elle est fidèle sur les grandes masses, et aveugle sur le reste. Une méthode
appelée 16 millions de fois mais très brève peut n'apparaître dans presque aucun cliché ;
une méthode appelée trois fois n'apparaîtra probablement jamais. **On ne sait jamais
combien de fois quelque chose a été appelé — seulement quelle proportion des photos le
montrait.**

**Java 25 — instrumentation ciblée (JEP 520).** Le traceur de méthodes de JFR réécrit le
bytecode des méthodes **nommées dans le filtre**, pour compter et chronométrer **chaque
invocation**. Ce n'est plus un sondage, c'est un comptage exhaustif : d'où les
16 000 000 d'invocations rapportées exactement, et les 594 877 piles d'appel enregistrées.

Le **filtre est ce qui rend l'instrumentation soutenable** : on ne paie le surcoût que sur
les méthodes qu'on a désignées. C'est tout le sens du JEP — l'instrumentation exhaustive
existait déjà chez les outils commerciaux, la nouveauté est de l'avoir dans le JDK, ciblée.

### Ce que cette précision coûte — à ne pas passer sous silence

L'exactitude ne s'obtient pas gratuitement, et sur deux points elle se retourne :

1. **L'instrumentation perturbe ce qu'elle mesure.** Une méthode tracée ne peut plus être
   *inlinée* par le compilateur JIT de la même façon, et un événement est écrit à chaque
   appel. Le **comptage** reste exact ; les **durées** de méthodes très brèves, elles, sont
   à prendre avec précaution — on mesure aussi le coût de la mesure. L'échantillonnage de
   Java 21, lui, ne déforme presque rien : il est moins précis mais moins intrusif.
2. **Le volume explose.** Tracer une méthode du chemin chaud a produit **129 Mo pour 10
   secondes** d'exécution. C'est jouable sur une méthode ciblée, ingérable en saupoudrage.

Autrement dit : Java 25 ne remplace pas l'échantillonnage, il **ajoute** un second mode.
On échantillonne pour savoir où chercher, on instrumente pour mesurer précisément ce qu'on
a trouvé.

### Ce que le portage ferait gagner

1. **Un besoin du brief passe de "outil tiers" à "inclus dans le JDK"** — l'arbre d'appel
   exact, sans rien installer. C'est le critère n° 1 (facilité de mise en œuvre) qui
   bascule, et il est prioritaire.
2. **Compatible hors ligne par construction**, puisque c'est le JDK lui-même.
3. **Le nombre d'appels devient exact**, ce qui compte pour restructurer du code : savoir
   qu'une méthode est appelée 16 millions de fois n'a pas la même valeur que « elle
   apparaît dans 4 % des échantillons ».

Ce qui reste inchangé : **les valeurs des paramètres**. Java 25 ne les donne pas
davantage. Le portage réduit le besoin d'outillage tiers, il ne le supprime pas.

### Ce que ça ne dit pas

Rien ici ne mesure le **coût** du portage 21 → 25 sur le code réel — c'est une question
de compatibilité applicative, hors périmètre de ce dépôt. Le tableau ci-dessus dit ce
qu'on gagnerait ; il ne dit pas ce qu'on paierait.

Les sorties des deux versions sont versionnées côte à côte pour vérification :
[`reports-demo/generated/jfr/`](../reports-demo/generated/jfr/) (Java 21) et
[`reports-demo/generated/jfr-jdk25/`](../reports-demo/generated/jfr-jdk25/) (Java 25).

## Décisions prises

Arbitrages tranchés par Benoît le 2026-08-20. Ils réduisent le périmètre et fixent le canal
de diffusion.

| # | Question | Décision |
|---|---|---|
| 1 | Acheter une licence pour les valeurs de paramètres ? | **Non, secondaire pour l'instant.** D'autant qu'Arthas les fournit gratuitement — voir ci-dessus |
| 2 | Capture ciblée ou générale ? | **À développer** — voir juste en dessous |
| 3 | Comment le non-technicien reçoit-il le rapport ? | **Un dossier envoyé dans un fichier**, analysable **sans IntelliJ** |
| 4 | Historiser les résultats ? | **Non, pas pour l'instant** |
| 5 | IntelliJ Ultimate ? | **Ne pas en dépendre** — tout le monde ne l'a pas. Ce qui n'existe qu'en Ultimate ne doit pas être sur le chemin critique |
| 6 | Viser le « but final » (restructuration) ? | **À développer** — voir la section Kieker |

### Ce que ces décisions éliminent

- **SonarQube, Glowroot, OpenTelemetry** sortent du périmètre : ils supposent un serveur à
  installer et à maintenir, ce que les décisions 3 et 4 rendent inutile.
- **Le profiler d'IntelliJ Ultimate** sort du chemin critique (décision 5).
- **JProfiler et YourKit** sont repoussés, sans être écartés (décision 1).

### Question 2, développée : capture ciblée ou générale ?

La question porte sur **l'étendue** de la capture des valeurs : instrumenter tout le code,
ou seulement les méthodes qu'on désigne.

**Le mode général** — enregistrer tous les appels de toutes les méthodes avec leurs
arguments — est séduisant : on n'a rien à décider d'avance, on capture puis on cherche.
**Il est impraticable, et c'est mesuré** : tracer **une seule** méthode du chemin chaud a
produit **129 Mo pour 10 secondes** d'exécution. Étendu à quelques centaines de méthodes,
on parle de gigaoctets par minute, et d'un ralentissement qui change le comportement même
qu'on observe.

**Le mode ciblé** — désigner les méthodes qui nous intéressent — est ce que proposent tous
les outils concernés : `watch <classe> <méthode>` chez Arthas, un XML de sondes chez JMC
Agent, un `filter` chez JFR en Java 25. Sa contrepartie : **il faut savoir quoi regarder
avant de regarder.**

**Recommandation : ciblé, et une méthode de travail en deux temps.**

1. **Repérer** avec les outils exhaustifs et bon marché — la couverture JaCoCo dit par où
   on est passé, le flame graph dit où le temps part. Aucune décision à prendre à ce stade.
2. **Comprendre** en ciblant : une fois la méthode intéressante identifiée, `watch` dessus
   pour voir les valeurs, `trace` pour voir son arbre.

Ce n'est pas un pis-aller : c'est ainsi qu'on travaille avec un débogueur — on ne pose pas
un point d'arrêt sur toutes les lignes. **Et surtout, ça ne départage aucun outil** :
aucun ne propose le mode général de façon soutenable. Cette question définit la *méthode*,
pas le *choix*.

### Question 5, développée : que perd-on sans IntelliJ Ultimate ?

| Capacité | Community | Ultimate |
|---|---|---|
| Couverture affichée dans la marge de l'éditeur | ✅ | ✅ |
| Import d'un `.exec` JaCoCo produit à l'extérieur | ✅ *(à confirmer sur poste)* | ✅ |
| Profiler intégré : flame graph, arbre d'appel | ❌ | ✅ (embarque async-profiler) |
| Ouverture d'un enregistrement `.jfr` | ❌ | ✅ |

**Conséquence, cohérente avec la décision 5** : la **couverture** peut se regarder dans
l'IDE par tout le monde, Community comprise. L'**arbre d'appel**, non — le canal commun
reste le HTML autonome d'async-profiler, qui a l'avantage d'être lisible par quelqu'un qui
n'a aucun IDE.

Autrement dit, l'absence d'Ultimate ne coûte rien : le moteur de son profiler
(async-profiler) est gratuit en ligne de commande, et sa sortie est *plus* partageable que
celle de l'IDE.

## Les questions à arbitrer

| # | Question | Ce qui bascule selon la réponse |
|---|---|---|
| 1 | **Achète-t-on une licence pour la capture des valeurs de paramètres ?** | Option A (0 €, configuration à faire) contre Option B (~500 $, intégration IntelliJ incluse). C'est **la** décision. |
| 2 | **La capture de paramètres doit-elle être ciblée ou générale ?** | Décisif techniquement : tracer une méthode du chemin chaud a produit **129 Mo d'enregistrement pour 10 s d'exécution**. Une liste de méthodes ciblées est réaliste, « tout capturer » ne l'est pas. |
| 3 | **Comment le non-technicien reçoit-il le rapport ?** | Un dossier HTML envoyé par fichier (JaCoCo seul suffit), ou un serveur interne (SonarQube on-premise, GitLab auto-hébergé) — ce qui ajoute une brique à installer et à maintenir. |
| 4 | **Le résultat doit-il être historisé ?** | Diagnostic ponctuel → les scripts de ce dépôt suffisent. Suivi dans le temps → il faut une plateforme, donc l'auto-hébergement (contrainte hors ligne). |
| 5 | **IntelliJ Ultimate se justifie-t-il ?** | Son profiler intégré embarque async-profiler — qu'on obtient gratuitement en ligne de commande. L'abonnement ne se justifie que pour le confort de rester dans l'IDE. |
| 6 | **Faut-il viser le « but final » (restructuration) dès maintenant ?** | Si oui, Kieker entre dans le périmètre et change l'échelle du projet. Si non, on outille le diagnostic et on décide ensuite. |

## Ce qui reste à faire pour trancher

1. **Activer l'essai JProfiler (10 j) et YourKit (15 j)** et les lancer sur ce même
   `sample-app` — c'est le seul moyen de répondre à la question n° 1 sur pièces. *(Action
   humaine : téléchargement et licence.)*
2. **Tester une piste gratuite de capture de paramètres** — JMC Agent en premier, puisqu'il
   s'appuie sur JFR déjà présent et reste hors ligne.
3. **Installer Arthas hors ligne** à partir de son paquet complet, pour lever le blocage
   constaté ici.
4. **Publier le rapport JaCoCo sur une URL** pour valider le canal « je l'envoie à
   quelqu'un qui n'est pas développeur ».
