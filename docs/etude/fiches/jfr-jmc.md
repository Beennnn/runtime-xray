# JFR (Java Flight Recorder) & JDK Mission Control

> **Statut : ✅ testé ici** (JFR en ligne de commande) · **📄 sur documentation** (l'interface JMC)
> — sorties dans [`reports-demo/generated/jfr/`](../../../reports-demo/generated/jfr)

## Ce que c'est

**JFR** est un enregistreur d'événements intégré à la JVM elle-même. **JMC** est
l'application de bureau qui ouvre et analyse ces enregistrements.

## Sa philosophie

**Être déjà là.** JFR n'est pas un outil qu'on ajoute : c'est un sous-système du JDK,
conçu pour rester activable en production avec un surcoût minime. Il n'enregistre pas
« un profil » mais un **flux d'événements** typés — GC, verrous, entrées-sorties, piles
d'exécution — qu'on interroge après coup.

Cette généralité est sa force et sa limite : il voit tout, mais rien n'est présenté sous
la forme « voici ton arbre d'appel » sans un outil de lecture.

## ⚠️ Le point décisif : Java 21 contre Java 25

Le JDK 25 apporte le **traçage de méthodes ciblé** (JEP 520). **Sous Java 21, la cible du
projet, ces réglages n'existent pas :**

```
[warning][jfr,start] The .jfc option/setting 'jdk.MethodTiming#filter' doesn't exist.
```

Même programme, même durée :

| Événement | Java 21 | Java 25 |
|---|---|---|
| `jdk.ExecutionSample` | 331 | 147 |
| `jdk.MethodTrace` | — | **594 877** |
| `jdk.MethodTiming` | — | **16 000 000 invocations comptées exactement** |

**Pourquoi 25 est plus précis** : Java 21 *échantillonne* (quelques centaines de photos de
la pile, dont on infère statistiquement où passe le temps) ; Java 25 *instrumente* les
méthodes nommées dans le filtre et compte **chaque** invocation. Changement de nature, pas
de réglage. En contrepartie l'instrumentation perturbe le JIT et fait exploser le volume —
détail et limites dans [les résultats](../../resultat/resultats.md#pourquoi-java-25-est-plus-précis--le-mécanisme).

Sous Java 21, JFR ne fournit que de l'échantillonnage — moins bien qu'async-profiler, pour
plus de complexité. **Sous Java 25, il devient un outil de premier plan sans rien
installer.** Voir [les gains d'un portage](../../resultat/resultats.md#gains-dun-portage-vers-java-25).

## Ce qu'il sait faire

| Capacité | Java 21 | Java 25 |
|---|---|---|
| Arbre d'appel | ⚠️ échantillonné | ✅ exact, par invocation |
| Compte d'appels | ❌ | ✅ exact |
| Lignes exécutées | ❌ | ❌ |
| Valeurs des paramètres | ❌ | ❌ — la signature est affichée, **pas les valeurs** |
| GC, verrous, E/S, threads | ✅ | ✅ |

## Son interface

**En ligne de commande** — l'outil `jfr` livré avec le JDK :

```
jdk.MethodTrace {
  method = lab.sample.transfer.Timetable.frequencyMinutes(Leg)
  stackTrace = [
    lab.sample.transfer.Connections.transferMinutes(Leg, Leg) line: 32
    lab.sample.transfer.Connections.waitMinutes(List, int) line: 23
    lab.sample.RoutePlanner.travelTimeMinutes(Trip) line: 63
    lab.sample.Main.main(String[]) line: 55
  ]
}
```

**Cet extrait est la démonstration la plus nette du trou du brief** : la pile complète est
là, le nom du paramètre et son type aussi (`Leg`) — mais **jamais sa valeur**.

**Dans JMC** — une application de bureau (Eclipse RCP) avec vues graphiques : flame view,
arbre d'appel, corrélation avec le GC et les verrous. Riche, mais c'est un outil
d'expert : pas un rapport qu'on envoie à un non-technicien.

## Comment on navigue dedans

- **Hors IDE** : ❌ — le `.jfr` est un binaire, illisible sans outil. Pas de page web.
- **Dans JMC** : ✅ interface complète, mais desktop et technique.
- **Dans IntelliJ Ultimate** : ✅ ouvre les `.jfr` nativement.
- **GitHub / GitLab** : ❌.

## Mise en œuvre

```bash
./tools/jfr/collect.sh
```

**Rien à installer** : c'est un flag JVM. Le script détecte la version du JDK et adapte
les options — il ne fait pas semblant d'avoir JEP 520 sous Java 21.

> ⚠️ Tracer une méthode du chemin chaud a produit **129 Mo pour 10 s** d'exécution, au-delà
> de la limite de fichier de GitHub. Le traçage fin se cible, il ne se saupoudre pas.
>
> ⚠️ Le `.jfr` binaire déclenche le scanner de secrets de GitHub (faux positif). Il est
> exclu du dépôt ; les extraits texte sont versionnés.

## Licence et coût

**GPLv2 + Classpath Exception** (OpenJDK), **0 €**, inclus dans le JDK. JMC est distribué
séparément sous **UPL 1.0** *(à confirmer)*. **Hors ligne par construction.**

## Ce qu'on peut en espérer

**Sous Java 21** : un socle de diagnostic généraliste (GC, verrous, threads) déjà présent,
mais pas la meilleure réponse aux besoins du brief.

**Sous Java 25** : l'arbre d'appel exact devient gratuit et sans installation — un argument
sérieux en faveur d'un portage. Les valeurs de paramètres, elles, restent hors de portée
dans les deux cas.

## Comparable à

- **[async-profiler](async-profiler.md)** — **Le concurrent direct.** Sous Java 21, il fait mieux pour moins d'effort. Sous Java 25, le rapport s'inverse : JFR compte exactement là où l'autre échantillonne.
- **[VisualVM](visualvm.md)** — Même famille, plus ancienne. VisualVM s'ouvre plus vite, JFR enregistre plus finement.
- **[JMC Agent](jmc-agent.md)** — Son extension naturelle : il ajoute au flux JFR des événements portant les **valeurs des paramètres**, que JFR seul ne capture pas.
- **[JProfiler](jprofiler.md) · [YourKit](yourkit.md)** — Ils font ce que JFR fait, plus les valeurs d'arguments, dans une interface unique — contre une licence.

## Facile / moins facile

**Ce qui est facile.** Lancer un enregistrement : rien à installer, c'est un flag. Impossible de faire plus simple.

**Ce qui l'est moins.** **En tirer quelque chose** : le `.jfr` est binaire, il faut JMC. Cibler le traçage sans exploser le volume (129 Mo pour 10 s sur une méthode chaude). Et sous **Java 21**, obtenir mieux qu'un échantillonnage : ce n'est pas difficile, c'est impossible.
