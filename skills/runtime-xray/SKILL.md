---
name: runtime-xray
description: Observer une application Java pendant qu'elle tourne — couverture réelle, où passe le temps, valeurs passées à une méthode — avec runtime-xray. À utiliser pour lancer une campagne, choisir la méthode racine et les filtres, observer un processus qu'on ne lance pas soi-même (service, conteneur, serveur d'application), ou quand une mesure précédente n'a pas montré ce qu'on attendait. Mots-clés : couverture, code mort, quel code tourne vraiment, profilage, JaCoCo, async-profiler, Arthas, recette, campagne de tests.
---

# Conduire une campagne runtime-xray

`runtime-xray` lance une application Java, l'observe pendant qu'elle tourne, et assemble un
rapport : le code réellement exécuté, où le temps est passé, et les valeurs passées à une
méthode choisie. Aucune modification du code analysé, aucun changement dans le build.

C'est un jar **sans aucune dépendance** : ni Python, ni shell, ni Maven à installer.

## Avant tout : trouver le jar

Ne pas supposer qu'il est téléchargeable. La machine visée n'a par hypothèse **pas de
réseau** — c'est la raison d'être de l'outil. Chercher dans cet ordre :

```sh
ls runtime-xray*.jar ./*/runtime-xray*.jar 2>/dev/null
ls ~/.runtime-xray/ 2>/dev/null
```

S'il n'y en a pas, demander où il est plutôt que d'essayer de le télécharger.

## La commande minimale

```sh
java -jar runtime-xray.jar --java "java -jar mon-appli.jar" --out runtime-xray-out
```

Ça suffit à obtenir un rapport. Tout le reste ne sert qu'à répondre à une question plus
précise, ou à payer moins cher.

`--java` est exécuté **tel quel** : l'outil ne relance pas l'application à sa façon, il
lance la vôtre. Une commande complexe — options JVM, propriétés système, arguments — se
recopie sans y toucher.

## La seule décision qui demande réflexion : `--root`

```sh
--root "com.example.Traitement::executer"
```

C'est **le point d'entrée métier** : la méthode dont on veut savoir avec quoi elle a été
appelée et ce qu'elle a déclenché. Un traitement, un calcul, une commande. **Pas un `main`,
pas un accesseur.**

On n'en désigne qu'une, et c'est délibéré : capturer les valeurs de toutes les méthodes
donnerait des centaines de mégaoctets sur un chemin fréquenté.

Sans `--root`, tout marche encore — on perd seulement les valeurs et l'arbre d'appel détaillé.

## Les quatre restrictions ne restreignent PAS la même chose

**C'est la confusion la plus fréquente**, et elle coûte une campagne entière : on élargit un
filtre qui n'y était pour rien, on relance, on obtient le même rapport.

| Option | Ce qu'elle commande, et **rien d'autre** |
|---|---|
| `--sources` | l'affichage du code source dans la page |
| `--root` | la capture des valeurs, et le filtre de temps par défaut |
| `--filter` | la mesure du temps, seule |
| `--cover` | l'instrumentation JaCoCo, donc la couverture |

Avant d'élargir quoi que ce soit, identifier **quel** symptôme on observe, puis prendre la
ligne correspondante. Le rapport lui-même porte ce tableau, écrit à partir du symptôme.

## Ce qu'on accepte de payer

Sur un gros code, ne pas tout prendre du premier coup.

```sh
--niveau couverture     # JaCoCo seul — le moins cher, souvent le plus utile
--niveau arbre          # + échantillonnage des piles
--niveau complet        # + valeurs (défaut)

--cover "com.example.*" # ← le poste de coût principal
--interval 10           # échantillonnage à 10 ms au lieu de 1 ms
```

**`--cover` est le premier levier.** Sans lui, *toute classe chargée* est instrumentée,
dépendances comprises.

## Observer un processus qu'on ne lance pas soi-même

Service systemd, conteneur, serveur d'application, script d'intégration : l'outil ne lance
rien, il rend le texte à coller.

```sh
java -jar runtime-xray.jar --print-options --out runtime-xray-out
```

Puis on ajoute la ligne obtenue à la commande Java existante, on laisse tourner, et on
assemble ensuite :

```sh
java -jar runtime-xray.jar --report-only --out runtime-xray-out
```

C'est aussi la réponse quand la commande est **noyée dans des scripts imbriqués** : on
n'essaie pas de faire remonter l'intelligence du lancement à travers les couches, on injecte
les agents une fois, tout en bas.

## Voir ce qui se passe pendant l'exécution

Une analyse dure ce que dure l'application observée. Trois façons de ne pas attendre en
aveugle, de la plus universelle à la plus lisible.

**1. Le fichier — il marche partout, il n'y a rien à demander.**

```sh
tail -f runtime-xray-out/progression.jsonl
```

Une ligne JSON par seconde, écrite de toute façon : secondes écoulées, cœurs occupés,
palier d'activité, taille de la sortie produite. La dernière ligne porte
`"evenement":"fin"` — c'est ce qui dit qu'on peut arrêter de regarder. Le chemin ne dépend
pas du nom de l'exécution.

**2. La bande dans le terminal** — elle s'affiche seule devant un vrai terminal, et se tait
dans un tuyau. Un shell imbriqué met souvent un tuyau au milieu ; on la réclame alors :

```sh
RUNTIME_XRAY_PROGRESSION=1 java -jar runtime-xray.jar …
```

**3. La page, quand on a un navigateur.**

```sh
java -jar runtime-xray.jar --suivi …          # http://127.0.0.1:8788
```

Elle montre ce qu'une suite de lignes JSON montre mal : la **forme** de l'exécution — la
bande d'activité, la courbe des cœurs occupés, la sortie qui grossit ou qui ne grossit
plus, et la fin du journal de l'application. Boucle locale seulement.

**Aucune des trois ne mesure quoi que ce soit.** Le temps processeur vient de ce que le
système compte de toute façon, la taille de sortie d'un fichier déjà écrit. Le rapport est
identique selon qu'on suit l'exécution ou non — un affichage qui déplacerait la mesure
ferait mentir le rapport qu'il accompagne.

## Plusieurs exécutions

Les exécutions **s'accumulent** dans le même `--out`. Les nommer, sinon le rapport devient
illisible :

```sh
--out campagne --name "recette nominale"
--out campagne --name "recette dégradée"
```

La page permet ensuite de cumuler la couverture des exécutions cochées, et de voir quelle
exécution a couvert quelle ligne.

## Sur une machine sans réseau

Les trois composants d'analyse (JaCoCo, async-profiler, Arthas) sont cherchés **d'abord sur
la machine**, le réseau vient en dernier. Si le lancement échoue faute de composants, le
message liste **tous les chemins essayés** : c'est lui qui suffit à s'en sortir, il faut le
lire en entier plutôt que de deviner.

```sh
--composants /chemin/vers/les/composants   # prime sur tout téléchargement
--repo https://miroir.interne/maven        # dépôt interne, en dernier recours
```

## Après la mesure : vérifier avant de conclure

Un rapport décevant est **silencieux** : un panneau de code vide se lit exactement comme un
panneau qu'on n'a pas su remplir.

- `diagnostic.json` est écrit à chaque assemblage, sans qu'on le demande. Il porte
  l'environnement, la configuration réelle du lancement, les racines de sources
  effectivement ouvertes, et le rapprochement classe par classe. **C'est le fichier à
  regarder en premier** quand le résultat surprend.
- La section « Analyse des sources » de la vue d'ensemble le rend lisible, et propose des
  racines de sources chiffrées — « cette racine résoudrait 27 des 27 classes sans source ».
- **Ne jamais deviner une racine de sources d'après une convention.** Du code faux affiché
  en face d'une couverture coûte plus cher qu'un panneau vide, parce qu'on le croit.

## Limite de plateforme à annoncer, pas à découvrir

**Sous Windows, il n'y a pas de mesure de temps** : async-profiler ne publie que des
binaires Linux et macOS. La couverture et les valeurs fonctionnent. L'outil le dit au
lancement. Un temps à zéro sous Windows ne signifie donc pas « jamais appelé ».

## Ensuite

Pour répondre à une question à partir du rapport obtenu, voir la compétence
**runtime-xray-lire**.
