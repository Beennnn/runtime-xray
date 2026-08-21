# Lire le rapport

> Ce document décrit la page produite par l'outil : ce qu'elle montre, ce qu'elle tait, et
> ce qu'on peut lui demander. Pour la produire, voir [le mode d'emploi](mode-emploi.md).
>
> [Un exemple en ligne](https://beennnn.github.io/runtime-xray/multi/)

![La vue : l'arbre d'appel à gauche, le code annoté au centre, les appels comparés en bas](../assets/shots/vue-arbre-appel.png)

La page est autonome : un fichier HTML sans dépendance, qui s'ouvre depuis un disque, une
pièce jointe ou une forge. Elle n'appelle rien sur le réseau.

## Les deux entrées, à gauche

Deux onglets mènent au même code, par deux chemins différents.

| Onglet | Ce qu'il montre | Quand il sert |
|---|---|---|
| Code | Les paquets, sous-paquets et classes, par ordre alphabétique, filtrés sur ce qui a tourné | On cherche une classe qu'on connaît déjà |
| Exécutions | Les exécutions comme racines d'un arbre d'appel, du plus coûteux au moins coûteux | On cherche par où l'exécution est passée |

Dans la vue Code, le bouton `exécuté` bascule entre « seulement ce qui a tourné » et
« tout le code », le code jamais atteint apparaissant alors en rouge. C'est ce second mode
qui fait ressortir un paquet entier que personne n'appelle — le cas que la lecture du code
ne révèle pas.

Dans la vue Exécutions, les cases à cocher en haut choisissent les exécutions
affichées ; `%` / `ms` bascule entre part du temps et durée estimée ; `pkg` masque les noms
de paquet pour dégager les noms de méthode.

Les flèches naviguent : haut et bas déplacent le curseur, droite ouvre puis descend,
gauche ferme puis remonte, `Entrée` sélectionne. Le curseur est volontairement distinct de
la sélection — un liseré, pas un aplat — pour qu'on puisse parcourir l'arbre sans changer
ce qu'affiche le panneau de droite.

## Ce qui n'est PAS affiché, et pourquoi

C'est la partie qu'il faut connaître pour ne pas se méprendre sur un pourcentage.

Quatre familles de frames sont repliées sur leur appelant dans l'arbre d'appel :

| Repliée | Raison |
|---|---|
| Le JDK et la bibliothèque standard | Personne n'ouvre `java.util.ArrayList` pour comprendre son application |
| Les rouages de la machine virtuelle | Compilateurs internes, appels natifs, trampolines : du code qu'aucun lecteur ne peut ouvrir ni corriger |
| Les outils d'observation eux-mêmes | L'agent de couverture et l'inspecteur de valeurs mesurent, ils ne font pas partie du sujet |
| Les paquets masqués par configuration ou par clic | Voir plus bas |

Le critère commun est simple : **a-t-on son code source ?** L'arbre sert à ouvrir des
méthodes et à les lire ; une frame qu'on ne peut pas ouvrir n'y a pas sa place.

⚠️ **Replier n'est pas supprimer.** Le temps d'une frame repliée est attribué à la méthode
applicative qui l'a appelée. Rien n'est perdu, et c'est même l'information utile : savoir
que `Terrain.slowdownFactor` coûte 8 % est exploitable ; savoir que `Math.pow` coûte 3 % ne
l'est pas, parce qu'on ne réécrira pas `Math.pow`.

Corollaire à garder en tête : le pourcentage d'une méthode inclut ce qu'elle a fait
faire au JDK. C'est voulu.

Les classes repliées ne disparaissent pas de l'analyse pour autant : l'onglet Code les
liste avec leur couverture. Là c'est un inventaire, pas une lecture.

## Le code annoté, au centre

Les lignes portent leur état de couverture : vert exécutée, jaune partiellement
(une branche sur deux), rouge jamais atteinte.

Le bouton *Resserré sur la méthode*, actif par défaut, n'affiche que la méthode
sélectionnée, sans ses lignes mortes. C'est ce qui garantit qu'on regarde une méthode à la
fois plutôt que d'être noyé dans un fichier de mille lignes. Le désactiver montre le fichier
entier.

À droite de chaque ligne, ce que la trace a vu partir de cette ligne :

```
appelle Trip.legs() ×10 0.002–0.003ms · Trip.mode() ×10 0.002–0.016ms
```

- le nom de l'appelé, cliquable : il ouvre la méthode appelée ;
- `×10` : le nombre de passages observés sur cette ligne ;
- l'étendue des durées, minimum et maximum.

### `×N` : un nœud par passage de boucle

Le `×N` est cliquable. Il déplie une ligne par passage, dans l'ordre de l'exécution.

C'est important sur une ligne dans une boucle. L'agrégat « 10 fois, entre 0,002 et
0,016 ms » dit qu'il y a de la dispersion sans dire d'où elle vient. Le détail la montre :
c'est presque toujours le premier tour qui coûte — cache froid, chargement paresseux,
compilation à la volée — et les suivants sont plats. Cette différence change le diagnostic.

Le nombre de passages détaillés est borné : au-delà, la liste cesse d'informer et commence à
peser.

## Les valeurs, en bas

Les valeurs ne sont capturées que sur une seule méthode, la méthode racine désignée au
lancement (`--root`). Observer toutes les méthodes produirait un volume ingérable et
fausserait davantage la mesure. Une méthode sans valeurs n'est donc pas une méthode qui n'a
pas tourné — la page le dit explicitement quand on en ouvre une.

Le panneau du bas met les appels en regard : une colonne par champ, une ligne par appel,
et les colonnes qui varient sont surlignées.

| | id | mode | weather | withLuggage | ⟵ retour |
|---|---|---|---|---|---|
| appel 1 | TRIP-175 | WALK | RAIN | true | 1523.97 |
| appel 2 | TRIP-176 | CAR | SUNNY | false | 52.01 |

C'est la seule question qui compte vraiment devant des valeurs capturées : *qu'est-ce qui
change d'un appel à l'autre, et est-ce ça qui a fait diverger l'exécution ?*

Dans l'arbre, chaque appel se déplie aussi en sous-nœuds `clé = valeur`. Le bouton
`par champ` / `par paramètre` choisit la granularité : une ligne par champ de la valeur
(`id = TRIP-175`, `mode = WALK`…), ou une ligne par paramètre avec la valeur brute.

## Filtrer ce qu'on ne veut pas voir

Le JDK est toujours replié. Mais la même chose vaut pour des bibliothèques qui n'en font pas
partie et que l'on considère comme de l'infrastructure : un journal, un client HTTP, un
cadriciel d'injection. Où passe cette frontière n'est pas une question technique — elle
dépend de ce que l'équipe possède et de ce qu'elle subit.

Deux façons de le dire, et elles se complètent.

### En lisant le rapport

Dans l'onglet Code, chaque classe et chaque paquet portent un bouton *filtrer* au survol.
Le chemin affiché au-dessus du source rend chaque niveau de paquet cliquable de la même
manière — c'est le geste le plus direct : on est en train de lire du code qui ne nous
concerne pas, et son chemin est déjà écrit au-dessus.

La liste s'accumule dans un bandeau, en haut à gauche, et elle y reste : le navigateur la
retient d'un chargement à l'autre, il n'y a rien à enregistrer.

### Avant la mesure, par la configuration

```bash
--hide "org.slf4j, ch.qos.logback"
```

Le bouton *« Ne plus le mesurer non plus »* du bandeau copie la ligne `HIDDEN_PACKAGES="…"`
correspondante. C'est un second geste, facultatif : il ne sert pas à sauvegarder — la
liste est déjà retenue — mais à ce que les exécutions suivantes ne mesurent même plus ce
code : collecte plus courte, rapport plus léger.

Une page statique ne peut pas écrire dans votre fichier de configuration. C'est aussi ce qui
lui permet d'être publiée telle quelle.

## Deux arbres, deux questions — on se sert des deux

Le mot « arbre d'appel » recouvre deux objets différents dans cette page. Ils ne répondent
pas à la même question, et la confusion coûte cher.

| | L'arbre agrégé | Le chemin d'un appel |
|---|---|---|
| Où | Onglet Exécutions, à gauche | Annotations « appelle … » dans le code, à droite |
| Produit par | async-profiler | Arthas |
| Répond à | « Où passe le temps, tous appels confondus ? » | « Qu'a fait *cet* appel-là, dans l'ordre ? » |
| Comment | Par sondage : la JVM est photographiée mille fois par seconde, et les photos sont additionnées. Rien n'est ralenti, mais un appel bref peut échapper à toutes les photos | Par instrumentation : chaque entrée et sortie est notée. Rien n'échappe, mais seules quelques invocations sont suivies, et elles sont ralenties |
| Ne peut pas | Distinguer deux appels de la même méthode : c'est une somme | Dire ce que fait l'application *en général* : c'est un exemple |

D'où la répartition : on cherche où regarder dans l'arbre agrégé, puis on ouvre la
méthode et on lit ce qui s'y est passé dans le chemin d'un appel.

## Plusieurs exécutions dans la même page

Chaque lancement ajoute une exécution. Toutes apparaissent comme racines de l'arbre, et les
cases à cocher choisissent celles qu'on affiche. Sur une méthode ouverte, la page indique
les autres exécutions où elle a aussi tourné, et le nombre de contextes d'appel distincts —
un clic bascule.

Trois identités par exécution, et chacune a sa raison : un UUID attribué au lancement
(stable, il ne change jamais), le nom d'origine donné par `--name`, et un nom d'affichage modifiable après coup dans `noms.json` sans rien relancer.

## Les sorties brutes

En bas de la vue d'ensemble, une section liste tout ce que les outils ont écrit, groupé
par outil : les rapports HTML de JaCoCo, le profil natif d'async-profiler et son inverse,
les piles repliées, les valeurs et la trace d'Arthas, le journal de l'exécution, le contexte.

Ce n'est pas de la complétude pour la forme. Cette page est une synthèse : elle replie
des frames, agrège des appels, masque des paquets. Chacun de ces choix peut se révéler faux
sur un cas qu'on n'avait pas prévu, et la page elle-même peut cesser de s'ouvrir. Les
sorties d'origine restent donc atteignables en un clic — c'est ce qui sépare un rapport
vérifiable d'un rapport à croire sur parole.

## `rapport.md` — la même chose, lisible dans une forge

À côté de `index.html`, l'outil écrit un `rapport.md`. Ce n'est pas un second rapport :
c'est le même, réduit à ce qui se lit sans interaction — couverture par paquet, code jamais
exécuté nommé, méthodes les plus coûteuses, appels comparés.

Il existe pour une raison précise : une forge affiche un fichier `.html` comme du code source, pas comme une page. Le Markdown, lui, est rendu nativement par GitHub comme par
GitLab, dépôt privé compris, sans Pages, sans service tiers et sans réglage. C'est le seul
format qui se lit là où le code est relu.

Son taux de couverture porte une précision qui compte : il est calculé sur tout le bytecode donné à analyser. Si l'analyse porte sur une dépendance entière, la plus grande
partie n'a par construction aucune raison de tourner, et ce taux dit alors surtout la taille
de cette dépendance.

## Ce que la page ne dit pas

- Elle ne compte pas les appels, sauf sur les lignes tracées. L'arbre agrégé est un
  sondage : il donne des parts de temps, pas des nombres d'invocations.
- Le coût de la méthode racine est surestimé, parce que l'inspection des valeurs
  travaille dans la même exécution. La page l'affiche et le chiffre. `--no-values` sur une
  seconde passe donne des temps non perturbés.
- Elle ne mesure ni la mémoire ni les allocations. D'autres outils le font ; ce n'était
  pas l'objet de [l'étude](../resultat/resultats.md).
- Elle décrit une exécution, pas un programme. Ce qui n'a pas tourné ce jour-là apparaît
  comme jamais exécuté — ce qui est exact, et ne veut pas dire inutile.
