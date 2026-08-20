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

### 3. Les valeurs des paramètres → **Arthas**. Le trou est comblé.

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

## Et JFR dans tout ça ? — il n'est PAS un troisième pilier

Il figurait plus haut dans une version précédente de cette page comme un point à part
entière. **C'était trompeur, et la question mérite d'être tranchée nettement.**

**Sous Java 21, JFR n'apporte rien qu'async-profiler ne fasse déjà — et il le fait moins
bien.** Les deux échantillonnent les piles d'appel ; c'est la même nature de donnée. Mais
async-profiler produit un HTML autonome qu'on ouvre et qu'on envoie, là où JFR produit un
binaire qui exige JDK Mission Control, une application de bureau. Sur nos critères n° 1
(mise en œuvre) et n° 2 (lisibilité), **il est dominé**.

Il garde pourtant trois intérêts réels, dont aucun n'est « une capacité de plus
aujourd'hui » :

1. **Il est déjà là.** C'est le seul outil disponible quand on ne peut **rien installer**
   sur la machine cible — un environnement verrouillé, une machine de production sur
   laquelle on n'a pas la main. C'est un **filet de sécurité**, pas un pilier.
2. **Il change de nature en Java 25.** Avec le traçage de méthodes (JEP 520), il ne
   échantillonne plus : il **compte exactement** — 16 000 000 d'invocations relevées, pas
   estimées. Ni async-profiler ni Arthas ne savent faire ça. C'est le seul argument
   technique sérieux en faveur d'un portage, et il est détaillé
   [plus bas](#gains-dun-portage-vers-java-25).
3. **C'est le seul format standard capable de porter des valeurs de paramètres.** Un
   événement JFR a des champs typés ; c'est ce qu'exploite le
   [JMC Agent](tools/jmc-agent.md). Arthas, lui, produit du texte de console. Si le besoin
   de **réutiliser** les valeurs capturées apparaît un jour — les rejouer, les comparer,
   les alimenter dans un autre outil — c'est par JFR que ça passera, pas par Arthas.
   Voir [FORMATS.md](FORMATS.md).

**En résumé** : aujourd'hui, sous Java 21, on n'en a pas besoin. Demain, sous Java 25 ou
si les valeurs doivent devenir des données structurées, il redevient central.

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

## La solution retenue : JaCoCo + async-profiler + Arthas

**→ [Page dédiée : le montage complet, le protocole et la vue intégrée](SOLUTION.md)**

Les trois outils se complètent exactement — ce que l'un ignore, un autre le couvre — et
l'ensemble a été **vérifié de bout en bout** sur la même application, sous Java 21.

| Priorité | Besoin | Outil | Ce qu'on obtient |
|---|---|---|---|
| **1** | Lignes exécutées | **JaCoCo** | Site HTML, code colorié ligne à ligne, classes mortes en rouge |
| **1** | Arbre d'appel | **async-profiler** | HTML autonome dépliable, part de temps par branche |
| **2 — option** | Valeurs des paramètres | **Arthas** | Les valeurs réelles, et l'arbre d'un appel avec ses numéros de ligne |

**Coût : 0 €. Connexion : aucune. Licence : aucune.**

Et les trois sorties s'assemblent dans **une seule page** —
[la vue intégrée](https://beennnn.github.io/runtime-xray/vue-integree/) : on navigue dans
l'arbre d'appel ou dans les classes, on choisit un point, et on voit d'un coup les lignes
exécutées, les appels qui en partent avec leur coût, et les valeurs passées.

![La vue intégrée](assets/shots/vue-integree.png)

**Le tout en une seule exécution** : les trois outils tournent ensemble, et la couverture
n'est pas corrompue — c'est vérifié automatiquement à chaque lancement. Seule réserve :
Arthas capte l'essentiel des échantillons du profil, donc les **pourcentages de temps** sont
surestimés sur la méthode qu'il observe. La vue replie ses frames pour restituer la forme de
l'arbre et affiche la réserve en bandeau. Le temps n'étant pas un critère du projet, c'est
un échange acceptable — et `TWO_RUNS=1` sépare les deux passes le jour où l'on voudra des
temps justes. Détail dans [SOLUTION.md](SOLUTION.md).

### Variante minimale, si Arthas pose problème

**JaCoCo + async-profiler seuls** couvrent les **deux objectifs prioritaires**. Arthas ne
sert que la seconde priorité : son installation manuelle hors ligne, ou le fait que sa
sortie soit une console plutôt qu'un rapport, ne remettent pas en cause l'essentiel.

### Le complément payant — repoussé, pas écarté

**JProfiler ou YourKit**, en plus du socle, apporteraient une chose précise qu'Arthas ne
sait pas faire : **agréger l'arbre d'appel par valeur de paramètre** sur l'ensemble des
appels. La décision n° 1 l'a classé secondaire. Ce qu'il faudrait vérifier en essai est
détaillé dans [EVALUATION-KEYS.md](EVALUATION-KEYS.md#ce-quon-attend-des-outils-commerciaux--et-pourquoi).

### Pour plus tard

- **[Kieker](tools/kieker.md)** — le seul outil qui vise le *but final* : reconstruire une
  architecture à partir de l'exécution. À ouvrir une fois le diagnostic outillé, pas avant.
- **Le portage vers Java 25** — il rendrait le comptage exact des appels disponible sans
  aucun outil tiers. Voir [les gains mesurés](#gains-dun-portage-vers-java-25).

### Écartées d'office par la contrainte hors ligne

Datadog, New Relic, Dynatrace, Codecov, Coveralls, SonarCloud — toutes SaaS. Le détail des
rejets, avec leurs motifs, est dans [ECARTES.md](ECARTES.md).

## Gains d'un portage vers Java 25 : **aucun gain visible sur le rapport**

### Le verdict d'abord

> **Sur la solution retenue, Java 25 n'améliore rien de ce que l'analyse donne à voir.**
> Ne pas porter le code pour l'outillage d'analyse. S'il y a un portage, ce sera pour
> d'autres raisons.

Vérifié point par point, sur le même programme :

| Ce que le rapport montre | Java 21 | Java 25 | Écart visible ? |
|---|---|---|---|
| Lignes exécutées (JaCoCo) | 91,1 % / 81,8 % | **identique, valeur par valeur** | ❌ aucun |
| Arbre d'appel (async-profiler) | fonctionne | fonctionne pareil | ❌ aucun |
| Valeurs des paramètres (Arthas) | fonctionne | fonctionne pareil | ❌ aucun |

**Les trois outils retenus ignorent la version du JDK.** Le gain de Java 25 ne concerne
qu'un quatrième outil — JFR — dont [on a établi plus haut](#et-jfr-dans-tout-ça--il-nest-pas-un-troisième-pilier)
qu'on n'en a pas besoin.

### Un argument en sens inverse

[Kieker](tools/kieker.md), le seul outil qui viserait le *but final* du brief, annonce le
support **jusqu'à Java 23**. Porter vers 25 **retirerait** cette option tant qu'il n'aura
pas suivi. Du point de vue de l'outillage, le portage coûterait plutôt qu'il ne rapporterait.

### Le seul cas où Java 25 changerait quelque chose

Un scénario, et un seul : **celui où l'on ne peut rien installer** sur la machine cible. Il
ne reste alors que ce que le JDK embarque, et la différence devient spectaculaire. Même
programme, même durée, même commande :

| Événement JFR | Java 21 | Java 25 |
|---|---|---|
| `jdk.ExecutionSample` (échantillonnage statistique) | 331 | 147 |
| `jdk.MethodTrace` (pile d'appel **à chaque invocation**) | — | **594 877** |
| `jdk.MethodTiming` (compte exact) | — | **16 000 000 invocations comptées** |

Sous Java 21, la JVM refuse même le réglage :

```
[warning][jfr,start] The .jfc option/setting 'jdk.MethodTiming#filter' doesn't exist.
```

### Pourquoi Java 25 est plus précis — le mécanisme

Ce n'est pas un réglage plus fin : **c'est un changement de nature de la mesure.**

**Java 21 échantillonne.** JFR photographie périodiquement la pile des threads. Sur dix
secondes, quelques centaines de clichés, à partir desquels on *infère* où le temps est
passé. Une méthode appelée 16 millions de fois mais très brève peut n'apparaître dans
presque aucun cliché. On ne sait jamais combien de fois une chose a été appelée — seulement
dans quelle proportion des photos elle figurait.

**Java 25 instrumente** (JEP 520). Le traceur réécrit le bytecode des méthodes **nommées
dans le filtre** pour compter et chronométrer **chaque** invocation. Ce n'est plus un
sondage, c'est un comptage exhaustif. Le filtre est ce qui rend l'opération soutenable :
on ne paie le surcoût que sur les méthodes désignées.

### Ce que cette précision coûte

1. **L'instrumentation perturbe ce qu'elle mesure** — une méthode tracée n'est plus
   *inlinée* de la même façon par le compilateur JIT. Le comptage reste exact, les durées
   des méthodes très brèves sont à prendre avec précaution.
2. **Le volume explose** — tracer une méthode du chemin chaud a produit **129 Mo pour
   10 secondes**. Jouable en ciblé, ingérable en saupoudrage.

Java 25 ne remplace donc pas l'échantillonnage : il **ajoute** un second mode. On
échantillonne pour savoir où chercher, on instrumente pour mesurer ce qu'on a trouvé.

Les sorties des deux versions sont versionnées côte à côte :
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
