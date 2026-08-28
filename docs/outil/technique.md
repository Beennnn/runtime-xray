# Détails techniques

> Page destinée à qui va **rejouer** ou **étendre** l'évaluation. Le décideur n'en a pas
> besoin : les résultats sont dans [les résultats](../resultat/resultats.md).

## Le programme analysé

[`sample-app/`](../../sample-app) — 27 classes, 9 paquets. Le **code métier** n'a aucune
dépendance : ce qu'un outil affiche vient de ce code, pas du bruit d'un framework.

Trois dépendances existent néanmoins, chacune pour produire un cas que le code local ne peut
pas fabriquer :

| Dépendance | Ce qu'elle démontre |
|---|---|
| `commons-lang3` | Du code à analyser qui ne vient pas d'un répertoire de classes mais d'un **jar**, comme une dépendance interne livrée compilée. Une seule de ses classes est appelée (`Range`, dans `Breaks`) |
| `slf4j-api` + `slf4j-simple` | Une bibliothèque **hors JDK**, visible dans l'arbre d'appel puisque rien ne la replie d'office, et donc masquable par configuration — l'appel est laissé dans la boucle chaude exprès |

Les jar de dépendances sont copiés dans `target/libs/` plutôt que fondus dans un jar
unique : l'étude a besoin d'un **chemin stable vers le jar d'une dépendance** pour le donner
à analyser, et un jar-uber effacerait justement la distinction qu'on veut montrer.

La fonction sous analyse est
[`RoutePlanner.travelTimeMinutes(Trip)`](../../sample-app/src/main/java/lab/sample/RoutePlanner.java) :
« combien de temps pour aller de A à B ? ». Elle est conçue pour **départager les outils**,
pas pour être réaliste.

### Le graphe d'appel dépend du contexte

C'est le point central : selon le trajet, ce ne sont pas les mêmes fonctions qui tournent.

| Contexte du trajet | Ce qui s'exécute |
|---|---|
| Voiture **et** heure de pointe | `traffic.Traffic` → `RushHourDelay` — sinon tout ce sous-arbre est absent |
| Train | `transfer.Connections` (récursif) → `Timetable`, seule opération **bloquante** |
| Vélo ou marche | `weather.WeatherPenalty` puis `LuggagePenalty` |
| Tous sauf le train | `terrain.Terrain` → `Elevation`, 24 sous-segments — le cœur de calcul |
| Durée > 2 h | `comfort.Breaks`, décidé sur une **valeur calculée**, pas sur un argument reçu |

Deux appels de la même méthode parcourent donc des arbres différents. Un outil qui ne
donne qu'un cumul global ne permet pas de le voir ; un outil qui trace par invocation, oui.

### Les trous de couverture sont volontaires

| Élément | Pourquoi |
|---|---|
| Le paquet `export` entier | `ItineraryExporter` et `CsvExporter` compilent, ont l'air utiles, et ne sont **jamais appelés**. C'est le cas le plus intéressant en reprise de code : un paquet complet en rouge fait poser la question « qui appelle ça ? » |
| `speed.PlaneSpeed` | Jamais instanciée — une **classe entière** doit ressortir en rouge |
| Branche `Weather.SNOW` | Jamais empruntée, **à l'intérieur** d'une méthode exécutée des millions de fois : un pourcentage par classe le masque, un rapport ligne à ligne le montre |
| Branche `Mode.PLANE` de `Speeds` | Volontairement instanciée dans le `switch` et non en champ `static final`, sinon la classe serait chargée et compterait comme partiellement couverte |

### Points de mesure

L'application imprime elle-même son temps par étape et la mémoire occupée
([`Metrics`](../../sample-app/src/main/java/lab/sample/Metrics.java)) :

```
  [JVM started                 ] stage        0 ms | total        0 ms | memory ~2 MB / 8192 MB max
  [trip set built              ] stage        8 ms | total        8 ms | memory ~1 MB / 8192 MB max
  [computation finished        ] stage     9927 ms | total     9935 ms | memory ~1 MB / 8192 MB max
```

C'est une **référence indépendante** : quand un outil annonce un temps, on peut le
confronter à une mesure qui ne vient pas de lui, et constater combien l'outil lui-même
ralentit l'exécution.

⚠️ Ces chiffres sont un bonus, **pas un critère de choix** — et ils ne disent rien de la
performance du code. **L'optimisation sera étudiée à part, plus tard** : quand le code aura
été compris grâce à ces outils, puis reconçu avec ses propres contraintes de performance.
Ici, la seule question posée est : *l'outil d'analyse ralentit-il assez peu pour rester
utilisable ?*

### Calibrage à ~10 secondes

`DEFAULT_ITERATIONS = 16_000_000`, calibré pour que le calcul dure **~10 s**. C'est le
minimum pour qu'un outil qui s'attache à un process vivant (VisualVM, JMC, JProfiler) ait
le temps d'être lancé, de sélectionner la JVM et d'enregistrer quelque chose. L'option
`--hold-seconds` maintient en plus la JVM en vie après le calcul.

### Affinages décidés à la mesure, pas au jugé

Trois modifications du programme viennent d'un profil observé, chacune commentée dans le
code :

1. **Fabrication du jeu de test sortie de la boucle mesurée** — elle captait ~70 % des
   échantillons ; l'arbre montrait `StringConcatHelper` au lieu du calcul d'itinéraire.
2. **Modèles de vitesse en instances partagées** — les allouer à chaque appel représentait
   ~25 % des échantillons, du bruit d'allocation pur.
3. **Boucle indexée au lieu de `for-each` sur une liste immuable** — l'itérateur alloué par
   étape représentait la moitié des échantillons.

Résultat : **71 % des échantillons ont une feuille dans `lab.sample`**, et les cinq
premières feuilles du profil sont des méthodes métier.

## Comment chaque outil s'intègre

Point structurant : **ces outils ne sont pas des dépendances Maven.**

| Mode | Outils | Ce que ça implique |
|---|---|---|
| **Agent JVM** (`-javaagent`, `-agentpath`) | JaCoCo, async-profiler, JProfiler, YourKit, Glowroot, OpenTelemetry, Kieker | Un flag au lancement ; rien à changer dans le code |
| **Option native du JDK** | JFR | Rien à installer du tout |
| **Attachement à un process vivant** | VisualVM, JMC, Arthas, JProfiler, YourKit | Nécessite que la JVM tourne encore — d'où le calibrage à 10 s |
| **Plugin de build** | JaCoCo (mode tests), OpenClover | Ne mesure que ce que les tests couvrent — pas la même question |

## Rejouer l'évaluation

```bash
mvn -q clean package
java -jar sample-app/target/sample-app.jar

./tools/jacoco/collect.sh          # couverture, rapport HTML annoté
./tools/async-profiler/collect.sh  # flame graph + arbre d'appel HTML
./tools/jfr/collect.sh             # enregistrement JFR
```

[`tools/java-env.sh`](../../tools/java-env.sh) force **Java 21** pour tous les collecteurs :
sans ça, on documenterait des capacités que la plateforme cible n'a pas.

## Pièges rencontrés, et leur correctif

| Piège | Correctif appliqué |
|---|---|
| `-Djacoco.outputDirectory` **n'est pas honoré** par le goal `report` : le rapport atterrit dans `target/site/jacoco` | Copie explicite plutôt que confiance à une propriété fantôme |
| Sans `-XX:+DebugNonSafepoints`, async-profiler attribue les frames au **mauvais numéro de ligne** | Flag ajouté dans le collecteur |
| Sans `include=lab/sample/*`, 60 % des échantillons sont les threads du compilateur JIT | Filtre ajouté — vrai, mais illisible pour qui cherche son propre code |
| Tracer une méthode du chemin chaud avec JFR : **129 Mo pour 10 s** d'exécution | `maxsize` + traçage ciblé sur la méthode rare |
| Le `.jfr` binaire **déclenche le scanner de secrets de GitHub** (faux positif « Grafana token ») | Binaire exclu du dépôt ; les extraits texte sont versionnés |
| Sur macOS, `perf_events` n'existe pas | `event=itimer` pour async-profiler |
