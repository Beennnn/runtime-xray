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
- Mesuré sur `sample-app` : **92,3 % des instructions**, **85,7 % des branches**.
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

## Le trou, et il est net

**Les valeurs des paramètres ne sont couvertes par aucun des trois.** Ce n'est pas une
supposition : JFR affiche la signature `frequencyMinutes(Leg)` et la pile d'appel
complète, mais **jamais la valeur du `Leg`**. Vérifié, pas déduit.

C'est exactement le terrain sur lequel les outils commerciaux revendiquent leur valeur
(*method splitting by parameter values* chez JProfiler, *probes* chez YourKit).

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
