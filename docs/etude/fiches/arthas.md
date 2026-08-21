# Arthas

> **Statut : ✅ testé ici, hors ligne** — sorties dans
> [`reports-demo/generated/arthas/`](../../../reports-demo/generated/arthas)
>
> Son lanceur télécharge ses modules au premier démarrage — ce qui **n'est pas un
> obstacle**, puisque le critère porte sur l'exécution et non sur l'installation. Le paquet
> complet est publié sur Maven Central ; une fois posé, `--arthas-home` fait que plus rien
> ne sort sur le réseau. Vérifié avec le trafic HTTP coupé.

> ℹ️ **Précision sur le nom** : Arthas est un outil d'**Alibaba** (projet open source
> `alibaba/arthas`). Le serveur que son lanceur contacte par défaut est
> `arthas.aliyun.com` — le service cloud d'Alibaba. Aucun rapport avec un quelconque
> service d'IA.

## Le point hors ligne : vérifié, pas supposé

C'est la question qui décide de tout pour ce projet, alors elle a été **testée**.

**La dépendance réseau existe, mais elle est dans le lanceur, à l'installation.**
`arthas-boot` télécharge les modules d'Arthas au premier démarrage. Le critère du projet
portant sur l'**exécution** hors ligne, cette étape n'est pas rédhibitoire : elle se fait
une fois, à l'avance. Reste à s'assurer qu'ensuite **plus rien ne sort** — c'est ce qui a
été testé.

**Elle se contourne en deux gestes** : récupérer le paquet complet
`com.taobao.arthas:arthas-packaging:<version>:zip:bin` (~17 Mo, publié sur **Maven
Central** — donc servi par n'importe quel miroir Maven interne), le décompresser dans
`~/.arthas/lib/<version>/arthas`, et lancer avec `--arthas-home`.

**La preuve** : la capture ci-dessous a été rejouée avec **tout le trafic HTTP et HTTPS de
la JVM routé vers un port mort** (`-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=1`, idem en
HTTPS). Résultat :

```
[INFO] arthas home: /Users/.../.arthas/lib/4.3.4/arthas
[INFO] Attach process 70723 success.
    @String[TRIP-188], @Mode[CAR], @Weather[RAIN], ...
```

Aucune tentative de sortie, aucun échec : l'attachement et la capture fonctionnent
**sans réseau du tout**. En environnement réellement coupé, le seul geste supplémentaire
est de transporter le zip une fois.

## Ce que c'est

Une console de diagnostic qui **s'attache à une JVM déjà en cours** et permet de
l'interroger en direct, par commandes : quelles méthodes sont appelées, avec quels
arguments, quel résultat, quelle pile.

## Sa philosophie

**Poser des questions à une application vivante**, sans la redémarrer ni la modifier.
Là où un profiler enregistre puis présente un rapport, Arthas est interactif : on tape
`watch`, on voit passer des valeurs, on affine, on arrête. C'est un outil de *diagnostic*
au sens médical, pas un outil de mesure globale.

## Ce qu'il sait faire

| Capacité | |
|---|---|
| **Valeurs des paramètres** | ✅ — `watch` avec expressions OGNL sur `params`, `returnObj`, `throwExp` |
| Arbre d'appel | ✅ — `trace` (chemin + temps par étage), `stack` (qui m'a appelé ?) |
| Lignes exécutées | ❌ |
| Décompilation à chaud, rechargement de classe | ✅ |

C'est **le seul outil gratuit recensé qui réponde directement au troisième besoin du
l'étude** — voir les valeurs passées — sans écrire de script d'instrumentation.

## Son interface

Une **console** (terminal ou navigateur sur `http://<hôte>:8563`), pilotable par fichier de
commandes — donc scriptable et rejouable.

### `watch` — les valeurs des paramètres

```
watch lab.sample.RoutePlanner travelTimeMinutes \
  '{params[0].id(), params[0].mode(), params[0].weather(), params[0].timeOfDay(), returnObj}' -n 5 -x 2
```

Sortie réelle, sous Java 21 :

```
method=lab.sample.RoutePlanner.travelTimeMinutes location=AtExit
ts=2026-08-20 20:08:02.619; [cost=5.866458ms] result=@ArrayList[
    @String[TRIP-56],
    @Mode[CAR],
    @Weather[SUNNY],
    @TimeOfDay[RUSH_HOUR],
    @Boolean[false],
    @Double[52.00806981818182],
]
```

**Les valeurs, pas les types.** C'est très exactement ce que JFR ne donne pas : là où
l'enregistreur affiche `travelTimeMinutes(Trip)`, Arthas dit *quel* trajet — un trajet en
voiture, à l'heure de pointe, par beau temps — et *ce qu'il a répondu*.

### `trace` — l'arbre d'appel d'UN appel, avec les numéros de ligne

```
trace lab.sample.RoutePlanner travelTimeMinutes -n 2
```

```
`---[0.416459ms] lab.sample.RoutePlanner:travelTimeMinutes()
    +---[4,08% 0.017ms ] lab.sample.model.Trip:mode() #43
    +---[2,57% 0.010709ms ] lab.sample.speed.Speeds:forMode() #43
    +---[13,35% 0.055583ms ] lab.sample.RoutePlanner:legMinutes() #50
    +---[2,60% 0.010834ms ] lab.sample.traffic.Traffic:applies() #53
    +---[3,06% 0.01275ms ] lab.sample.weather.WeatherPenalty:applies() #57
    `---[2,06% 0.008583ms ] lab.sample.comfort.Breaks:totalMinutes() #67
```

Deux choses que ni JaCoCo ni async-profiler ne donnent :

1. **C'est l'arbre d'UN appel**, pas un cumul statistique. On voit le chemin réellement
   emprunté par ce trajet-là.
2. **Les numéros de ligne** (`#43`, `#53`, `#57`) — de quoi retourner directement dans le
   source, ce qui répond au besoin de navigation.

Et les branches conditionnelles apparaissent ou non selon le contexte : `Traffic:applies()`
n'est là que pour un trajet en voiture, `WeatherPenalty:applies()` que pour le vélo ou la
marche.

## Comment on navigue dedans

- **Console web** ✅ — accessible sans installer quoi que ce soit côté lecteur.
- **Pas de rapport à envoyer** : la sortie est un flux de texte. Pour un non-technicien,
  il faut la reprendre et la mettre en forme.
- Pas d'intégration IDE ni plateforme.

## Mise en œuvre

```bash
./tools/arthas/install-offline.sh   # une fois : récupère le paquet et le pose localement
./tools/arthas/collect.sh           # attache, capture, écrit dans reports-demo/
```

**L'installation hors ligne tient en deux gestes** : récupérer
`com.taobao.arthas:arthas-packaging:<version>:zip:bin` (~17 Mo, sur Maven Central, donc
disponible depuis un miroir Maven interne), le décompresser dans
`~/.arthas/lib/<version>/arthas`, et lancer avec `--arthas-home`. Le lanceur ne contacte
plus jamais `arthas.aliyun.com`.

En environnement réellement coupé : on transporte le zip une fois, et c'est tout.

> ⚠️ Trois pièges rencontrés, tous corrigés dans les scripts :
> - un fichier de commandes `.as` **n'accepte aucun commentaire** — une ligne `#` est
>   envoyée comme commande et renvoie `#: command not found` ;
> - la commande `stop` placée après un `trace` **ferme la session avant la collecte** ;
> - sans PID valide, `arthas-boot` attend une saisie **interactive indéfiniment** — d'où
>   le garde-fou temporel dans le collecteur.

## Licence et coût

**Apache 2.0**, open source, **0 €**. Projet Alibaba, largement utilisé.

## Ce qu'on peut en espérer

**Il comble le trou laissé par JaCoCo et async-profiler, et c'est vérifié.** Avec lui,
le socle gratuit couvre les trois besoins de l'étude :

| Besoin | Outil | |
|---|---|---|
| Lignes exécutées | JaCoCo | ✅ |
| Arbre d'appel (cumulé, visuel) | async-profiler | ✅ |
| Arbre d'appel (un appel, avec lignes) + **valeurs des paramètres** | **Arthas** | ✅ |

Ce que ça change : **la question de la licence commerciale ne porte plus sur une capacité
manquante, mais sur le confort**. JProfiler et YourKit réuniraient tout dans une interface
unique ; le trio gratuit demande trois outils et une console.

## Comparable à

- **[BTrace](btrace-byteman.md) · [Byteman](btrace-byteman.md)** — **Même famille** : injecter de l'observation dans une application vivante. La différence est ergonomique et décisive — Arthas répond à une commande, les deux autres demandent d'écrire un script ou une règle par question.
- **[JMC Agent](jmc-agent.md)** — Même objectif, philosophie opposée : **déclaratif** (un XML de sondes, une donnée structurée en sortie) contre **interactif** (une commande, du texte en sortie). Le JMC Agent produit une donnée réutilisable, Arthas une réponse immédiate.
- **[JProfiler](jprofiler.md) · [YourKit](yourkit.md)** — Ce qu'ils ajoutent est précis : **agréger** l'arbre d'appel par valeur d'argument sur des millions d'appels. Arthas montre quelques appels individuels — utile pour comprendre un cas, insuffisant pour raisonner sur un ensemble.
- **[IntelliJ IDEA](intellij.md)** — Le point d'arrêt non suspensif qui journalise une expression fait la même chose, gratuitement, dans l'IDE — mais seulement pendant une session de débogage.

## Facile / moins facile

**Ce qui est facile.** Poser une question à une application vivante : `watch`, et les valeurs défilent. `trace`, et l'arbre d'un appel s'affiche avec ses numéros de ligne. C'est le geste le plus direct de tout le panel pour le troisième besoin — et l'installation hors ligne s'est révélée triviale (un zip à décompresser).

**Ce qui l'est moins.** **Transmettre le résultat** : la sortie est un flux de texte de console, pas un rapport navigable — c'est sa vraie faiblesse face au critère n° 2. Il faut aussi **savoir quoi demander** : contrairement à un rapport de couverture qui montre tout, Arthas ne répond qu'aux questions qu'on lui pose, méthode par méthode. Et il exige une **JVM vivante**, donc une exécution qu'on peut tenir ouverte.
