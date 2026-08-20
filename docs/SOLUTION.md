# La solution retenue — une exécution, trois outils, une vue

> Sans licence · hors ligne · 0 € · vérifié de bout en bout sous Java 21.

**[→ Voir la vue intégrée](https://beennnn.github.io/runtime-xray/vue-integree/)**

![La vue intégrée](assets/shots/vue-integree.png)

*À gauche l'arbre d'appel, à droite le code de la méthode choisie : lignes vertes
exécutées, annotations violettes indiquant quel appel part de quelle ligne et en combien
de temps, et en bas les valeurs réellement passées.*

## Les trois outils et leur rôle

| | Outil | Ce qu'il apporte | Ce qu'il ne sait pas faire |
|---|---|---|---|
| <img src="assets/icons/jacoco.svg" width="22"> | **JaCoCo** | Les lignes exécutées, à la ligne près, et surtout **celles qui ne l'ont jamais été** | Rien sur le temps ni sur l'ordre des appels |
| <img src="assets/icons/async-profiler.svg" width="22"> | **async-profiler** | L'**arbre d'appel** agrégé sur toute l'exécution, avec la part de temps de chaque branche | Ne prouve jamais qu'un code n'a pas tourné (il échantillonne) |
| <img src="assets/icons/arthas.svg" width="22"> | **Arthas** | Les **valeurs des paramètres**, et l'arbre d'**un seul** appel avec ses numéros de ligne | Pas de rapport transmissible : c'est une console |

Les trois se complètent exactement : ce que l'un ignore, un autre le couvre.

## Le protocole : une seule exécution

[`tools/run-all.sh`](../tools/run-all.sh) lance **un seul processus** avec les deux agents
attachés au démarrage, puis y branche Arthas pendant que les appels ont lieu :

```
java -javaagent:jacoco-agent.jar=destfile=…          ← instrumente le bytecode
     -agentpath:libasyncProfiler.dylib=start,…       ← échantillonne les piles
     -jar sample-app.jar --iterations 48000000       ← ~30 s, le temps de s'attacher
                                                     ↳ arthas-boot --arthas-home … -f watch-params.as
```

Puis le rendu : rapport JaCoCo, rapport ciblé, et la vue intégrée.

```bash
./tools/run-all.sh
python3 tools/summary/build-dashboard.py
```

### Ce qui a été vérifié, et qui n'allait pas de soi

**Les trois agents cohabitent sans se corrompre.** Arthas *retransforme* les classes qu'il
observe, alors que JaCoCo les a déjà instrumentées — il était plausible que la couverture
soit perdue sur ces classes. Le script vérifie donc explicitement, à chaque exécution, que
la classe observée conserve sa couverture :

```
▶ Contrôle d'intégrité : la retransformation d'Arthas a-t-elle abîmé la couverture ?
   RoutePlanner : 93 instructions couvertes (OK)
```

## ⚠️ Le prix de l'exécution unique : Arthas fausse le profil

C'est le résultat le plus important de cette intégration, et il est mesuré.

Dans l'arbre d'appel produit par cette exécution commune, les deux premières branches sous
la fonction analysée ne sont pas du code métier :

| Branche | Part des échantillons |
|---|---|
| `java.arthas.SpyAPI.atBeforeInvoke` | **41,3 %** |
| `java.arthas.SpyAPI.atAfterInvoke` | **40,4 %** |
| `lab.sample.RoutePlanner.legMinutes` | 9,4 % |

**Plus de 80 % du temps mesuré est celui de l'instrumentation d'Arthas**, pas celui du
programme. C'est logique — Arthas intercepte chaque entrée et chaque sortie de la méthode
observée — mais ça rend le profil inexploitable pour répondre à « où passe le temps ? ».

### Le protocole recommandé : deux exécutions, pas une

| Exécution | Outils | Ce qu'on en tire |
|---|---|---|
| **1 — mesure** | JaCoCo + async-profiler | La couverture **et** un profil non faussé |
| **2 — inspection** | Arthas seul | Les valeurs des paramètres, l'arbre d'un appel |

Les deux rejouent le même scénario déterministe, donc les résultats restent comparables et
la vue intégrée les assemble sans difficulté. **L'exécution unique reste utile** pour une
démonstration ou quand le programme est coûteux à relancer — à condition de savoir que les
pourcentages du profil sont alors ceux d'un programme instrumenté, pas du programme.

> C'est exactement le compromis énoncé ailleurs dans ce dépôt : l'instrumentation est
> exacte sur ce qu'elle compte, et perturbe ce qu'elle mesure. Ici on en a la démonstration
> chiffrée sur notre propre montage.

## La vue intégrée

[`tools/summary/build-dashboard.py`](../tools/summary/build-dashboard.py) produit **un
fichier HTML autonome** (~275 Ko) à partir des sorties machine des trois outils. Il ne
mesure rien : il assemble.

| Source lue | Ce qu'elle alimente dans la vue |
|---|---|
| `jacoco.xml` | La couleur des lignes, et la ligne de début de chaque méthode |
| `profil.collapsed` | L'arbre d'appel et les pourcentages |
| `trace-calltree.txt` | Les annotations « quel appel part de cette ligne, en combien de temps » |
| `watch-params.txt` | Le panneau des valeurs réellement passées |
| les `.java` du projet | Le code affiché |

### Ce qu'elle permet

- **Deux navigations, au choix** : par **arbre d'appel** (on suit l'exécution) ou par
  **packages et classes** (on cherche un fichier précis). Les deux mènent à la même vue de
  code.
- **Tout sous les yeux** : en sélectionnant un nœud, on voit d'un coup les lignes
  exécutées, les appels qui en partent, leur coût, et les valeurs passées.
- **Masquer le code non exécuté** : une case à cocher retire du rapport les classes jamais
  atteintes et masque les lignes mortes, en indiquant combien ont été repliées. C'est la
  réponse à « exporter un résultat nettoyé, sans être pollué par le code inutilisé ».
- **Autonome** : un seul fichier, aucune ressource externe, aucun serveur — il s'ouvre
  hors ligne et s'envoie en pièce jointe.

### Ses limites, énoncées

- Les annotations de ligne ne couvrent que la méthode tracée par Arthas — tracer tout le
  code produirait un volume ingérable (129 Mo pour 10 s sur une seule méthode chaude).
- Les valeurs affichées sont celles de **quelques appels**, pas une agrégation par valeur.
  C'est précisément ce que les outils commerciaux savent faire en plus.
- L'arbre d'appel est **échantillonné** : une méthode rare peut n'y pas figurer. Pour
  savoir ce qui n'a jamais tourné, c'est la couverture qui fait foi, pas l'arbre.

## Ce que ça coûte

| | |
|---|---|
| Licences | **0 €** |
| Connexion pendant l'exécution | **aucune** |
| Connexion pour installer | oui, une fois — JaCoCo et Arthas depuis Maven Central ou un miroir interne, async-profiler par le gestionnaire de paquets. Le critère portant sur l'exécution, ce n'est pas une contrainte |
| Installation | trois outils, tous scriptés dans [`tools/`](../tools/) |
| Exécution | deux lancements du programme, ~30 s chacun |
