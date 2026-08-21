# Réduire l'empreinte sur un gros code

> Les trois informations recherchées n'ont ni la même valeur, ni le même coût. Sur une
> application d'entreprise, on ne les prend pas toutes du premier coup : on descend d'un
> niveau, on mesure, et on remonte si l'application le supporte.

## L'ordre de priorité, et pourquoi c'est celui-là

| | Information | Ce qu'elle coûte | Ce qu'on perd sans elle |
|---|---|---|---|
| 1 | **La couverture** — quelles lignes ont tourné | Instrumentation du bytecode au chargement, puis un compteur par branche | Tout. C'est la seule information **exhaustive** : elle voit les méthodes brèves, celles qu'un profileur rate par construction |
| 2 | **L'arbre d'appel** — qui appelle qui | Un réveil de la JVM à chaque intervalle d'échantillonnage | La forme du programme. On sait ce qui a tourné, pas ce qui a déclenché quoi |
| 3 | **Les valeurs** — avec quels paramètres | Une interception à chaque entrée et sortie de la méthode observée | L'explication des écarts. C'est utile, mais seulement une fois qu'on sait où regarder |

Cet ordre n'est pas une préférence : il suit ce que chaque mesure **remplace**. Sans
couverture, ni le profil ni les valeurs ne disent ce qui n'a *pas* tourné — et le code mort
est souvent ce qu'on cherchait. Sans valeurs, la couverture et l'arbre restent lisibles.

## Les trois niveaux

Un seul réglage les commande : `--niveau`.

```bash
# 1. le moins cher — la couverture seule
java -jar runtime-xray.jar --niveau couverture \
  --java "java -jar app.jar" --cover "com.exemple.*" --sources src/main/java

# 2. + l'arbre d'appel, échantillonné toutes les 10 ms
java -jar runtime-xray.jar --niveau arbre --interval 10 \
  --java "java -jar app.jar" --cover "com.exemple.*" --filter "com/exemple/*" \
  --sources src/main/java

# 3. + les valeurs d'une méthode (défaut)
java -jar runtime-xray.jar --niveau complet \
  --java "java -jar app.jar" --root "com.exemple.Moteur::calculer" \
  --cover "com.exemple.*" --sources src/main/java
```

| Niveau | JaCoCo | async-profiler | Arthas | Ce que la page montre |
|---|---|---|---|---|
| `couverture` | oui | **non** | **non** | Le code exécuté, ligne à ligne, et le code mort |
| `arbre` | oui | oui | **non** | Idem, plus l'arbre d'appel et la part de temps |
| `complet` *(défaut)* | oui | oui | oui | Idem, plus les valeurs reçues par la méthode racine |

Le niveau retenu est **écrit dans le contexte de l'exécution** et affiché dans la page. Sans
cela, un rapport moins fourni qu'un autre se lirait comme une mesure ratée plutôt que comme
un choix.

## Les leviers, du plus efficace au plus fin

### 1. Restreindre l'instrumentation — `--cover`

C'est le poste de coût principal, et le plus souvent oublié. Sans consigne, JaCoCo
instrumente **toute classe chargée par la JVM** : votre code, mais aussi le cadriciel, le
client HTTP, le pilote de base de données, le moteur de rendu. Sur une application qui
charge quinze mille classes, l'essentiel du surcoût vient de code que personne ne compte
lire.

```bash
--cover "com.exemple.*:com.exemple.commun.*"
```

Le format est celui de l'agent JaCoCo : des motifs de noms de classes séparés par `:`. Ce
qui n'y correspond pas n'est ni instrumenté, ni ralenti, ni compté.

**C'est aussi ce qui rend le taux de couverture lisible** : le dénominateur devient le code
du projet, au lieu de tout ce que la JVM a chargé.

### 2. Espacer les relevés — `--interval`

L'échantillonnage réveille la JVM à intervalle fixe, mille fois par seconde par défaut.
Passer à 10 ms divise par dix le nombre de relevés — et le surcoût qui va avec.

```bash
--interval 10     # 100 relevés par seconde au lieu de 1000
--interval 20     # 50 relevés par seconde
```

Ce qu'on perd : la finesse. Une méthode qui ne consomme que 0,1 % du temps peut ne
plus apparaître du tout. Pour comprendre la forme d'un programme, c'est sans conséquence ;
pour chasser une microseconde, ça l'est.

### 3. Restreindre le profil — `--filter`

Indépendant du précédent : `--filter "com/exemple/*"` dit à async-profiler de ne conserver
que les piles qui traversent ce paquet. Le coût du réveil reste, mais le fichier produit et
le temps d'assemblage de la page s'effondrent.

Sans consigne, le filtre se déduit du paquet de la méthode racine.

### 4. Renoncer aux valeurs — `--niveau arbre` ou `--no-values`

La capture des valeurs est le seul dispositif qui **intercepte** au lieu d'observer : il
s'insère à l'entrée et à la sortie de la méthode désignée. Sur une méthode appelée des
millions de fois, c'est le poste le plus visible — et [la réserve affichée dans le
rapport](lire-le-rapport.md) rappelle qu'il fausse la mesure du temps de cette méthode.

Deux conséquences pratiques :

- pour des **temps justes**, mesurer sans les valeurs, puis les capturer dans un second
  lancement ;
- pour un **premier contact** avec une application inconnue, `--niveau arbre` suffit
  largement : on ne sait pas encore quelle méthode observer.

### 5. Baisser le volume capturé — `WATCH_COUNT`, `TRACE_COUNT`

Dans le fichier de configuration : le nombre d'appels dont on garde les valeurs (10 par
défaut) et le nombre d'invocations dont on trace l'arbre. Les baisser à 3 réduit la durée
d'attachement de l'outil de capture.

### 6. Ne pas relancer l'application — `--print-options`

Une application longue à démarrer, un service géré par systemd, un conteneur : la relancer
sous l'outil coûte plus cher que la mesure elle-même. `--print-options` donne les options
JVM à ajouter à la ligne de commande existante ; l'assemblage de la page se fait ensuite
avec `--report-only`.

```bash
java -jar runtime-xray.jar --print-options --out mesures --classes app.jar
# … ajouter les options affichées au service, le laisser tourner, l'arrêter …
java -jar runtime-xray.jar --report-only --out mesures --sources src/main/java
```

## Une marche à suivre, sur une application qu'on ne connaît pas

1. **Couverture seule, sur le code du projet.** `--niveau couverture --cover "com.exemple.*"`.
   Rien d'autre. On obtient ce qui a tourné et ce qui n'a jamais tourné, pour un surcoût
   qu'une application de production supporte généralement sans réglage.
2. **Ajouter l'arbre, espacé.** `--niveau arbre --interval 10`. On voit la forme du
   programme et les endroits où le temps se consomme.
3. **Choisir une méthode et l'observer.** C'est seulement là que `--root` a un sens : on
   sait maintenant laquelle regarder, et pourquoi.

Si la mesure reste trop chère au niveau 1, le problème n'est plus le réglage : c'est le
périmètre. Restreindre `--cover` à un module, ou mesurer sur un scénario plus court, sont
les deux seules réponses honnêtes.

## Réserves

- **Les surcoûts ne sont pas chiffrés ici.** Ils dépendent du code observé, de la machine et
  de la JVM ; annoncer un pourcentage pris ailleurs serait exactement le genre d'affirmation
  que [la méthode](../etude/methode.md) interdit. Ce qui est établi, c'est **l'ordre** des
  postes de coût, et le fait que chaque levier agit sur celui qu'on nomme.
- **Ces conseils n'ont pas été rejoués sur une application de plusieurs milliers de
  classes** — c'est une limite déjà énoncée dans [le README](../../README.md#portée-et-limites-de-létude).
  Les leviers sont ceux que les outils sous-jacents documentent ; l'effet mesuré sur un code
  industriel reste à observer.

## À lire ensuite

- [Mode d'emploi](mode-emploi.md) — tous les paramètres
- [Reprendre le résultat dans un autre outil](exports.md) — perf, cpuprofile, LCOV
- [Lire le rapport](lire-le-rapport.md) — ce que la page montre et ce qu'elle replie
