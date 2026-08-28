# Faire lire le rapport par un modèle de langage

> Un rapport `runtime-xray` répond à des questions qu'on met dix minutes à formuler et une
> heure à instruire à la main. Un modèle de langage les instruit en quelques secondes — à
> condition qu'on lui donne **le bon texte**, et non la page.

## Le besoin, énoncé une fois

Ce n'est pas un problème d'API.

Ça y ressemble : il faut choisir un fournisseur, une forme de requête, une bibliothèque. Mais
ce choix-là change tous les six mois et diffère d'un service à l'autre, alors que ce qui ne
change pas, c'est le besoin en dessous : donner à lire **un texte borné, exact, et qui se
comprend seul**.

D'où la règle tenue ici, jumelle de celle des [exports](exports.md) :

> **L'outil produit le contexte. Il ne parle à personne.**

Aucune classe de `runtime-xray` ne connaît le nom d'un fournisseur, n'ouvre une connexion ni
ne lit une clé. Ce qu'il produit se colle dans une fenêtre de discussion, se donne à un agent
qui lit l'espace de travail, ou se poste par une commande de trois lignes. Le jour où le
protocole à la mode change, seules ces trois lignes sont à refaire.

## Le fichier de faits

Un rapport `runtime-xray` s'accompagne d'un `faits.jsonl` : **un objet JSON par ligne**, où
chaque ligne se comprend seule.

```jsonl
{"fact":"class.never_executed","classe":"com.example.app.Ancien","runsAnalysed":["recette-1","recette-2"]}
{"fact":"method.hot","methode":"com.example.app.Repository.charger","samples":4821,"pct":31.2,"measure":"async-profiler.samples","execution":"recette-1"}
```

Il n'y a rien à installer pour le lire : `grep`, `jq`, une boucle de trois lignes, ou un
modèle de langage. Trois décisions le rendent utilisable, et il perd tout son intérêt dès
qu'il en manque une.

**Une ligne porte tout son sens.** Dix lignes prises au milieu du fichier se comprennent
sans les précédentes. Pas de structure imbriquée à reconstituer, pas de référence à un
identifiant défini plus haut, et jamais une mesure sans son unité — un chiffre sans unité
oblige le lecteur à deviner, et il devinera.

**Le vocabulaire voyage avec la donnée.** La première ligne est un fait `campagne` qui porte
un dictionnaire des familles de faits et de ce que chacune veut dire. Une documentation posée
à côté se perd — dans un zip, dans une pièce jointe, dans un dossier recopié. Celle-ci se
recopie avec le fichier.

**Ce qui n'a PAS été mesuré est écrit.** Sous Windows, async-profiler ne publie aucun
binaire : il n'y a pas de mesure de temps. Sans une ligne pour le dire, « 0 relevé » se lit
exactement comme « ce code n'a jamais tourné » — et un lecteur tranchera, dans le mauvais
sens, avec assurance. Les faits `unavailable` et `caveat` existent pour ça. Ils disent
ce qui manque, pourquoi, ce qu'on ne peut donc **pas** en conclure, et le remède.

Le format se donne dans le manifeste de la page (`faitsFormat`), et le rapport HTML n'a
besoin de rien de tout ceci : `faits.jsonl` **s'ajoute** sans rien retirer.

## Le paquet de contexte

Donner `faits.jsonl` en entier à un modèle marche jusqu'à ce que le fichier dépasse la
fenêtre — c'est-à-dire dès la première campagne sérieuse. Le tronquer au premier N kilo-octets
marche encore moins bien : les réserves sont en tête, les mesures en queue, et couper la queue
donne un paquet qui n'a plus rien à dire.

`--contexte` fait ce travail :

```bash
java -jar runtime-xray.jar --out runtime-xray-out --contexte "which classes never ran?"
```

Sur la sortie standard, un texte Markdown prêt à coller, qui porte dans cet ordre :

1. **de quoi il s'agit** — une observation d'application Java pendant son exécution ;
2. **trois pièges de lecture** — dont « un zéro peut vouloir dire *pas mesuré* » ;
3. **la campagne** et **le vocabulaire des faits** ;
4. **ce qui n'a PAS été mesuré**, avant tout chiffre ;
5. **les faits retenus** pour cette question, en JSONL ;
6. s'il a fallu écarter des faits, **combien et lesquels**.

Trois décisions le tiennent, et chacune est gardée par un test.

**L'ordre est l'information.** Les indisponibilités passent avant les mesures parce qu'un
lecteur qui voit les chiffres d'abord les a déjà interprétés quand il arrive aux réserves.
C'est vrai d'un humain ; ça l'est plus encore d'un modèle, qui produira une réponse assurée
sur un zéro qui ne mesurait rien.

**Une indisponibilité n'est jamais élaguée.** Le budget (40 000 caractères par défaut,
environ dix mille jetons — de quoi tenir dans la fenêtre d'un modèle modeste, comme le sont
souvent ceux qu'on héberge soi-même) coupe des mesures, jamais des réserves. Une réponse
fausse et assurée coûte plus cher qu'une absence de réponse.

**Rien n'est coupé en silence.** Quand le budget force à écarter, le paquet dit combien, de
quelles familles, et que la sélection est INCOMPLÈTE — donc qu'on n'en tire ni total ni
classement. Un extrait qu'on croit complet fait conclure sur un échantillon.

### La question a deux rôles, et c'est ce qui la rend ambiguë

Elle **choisit** les faits — grossièrement, par mots-clés — et elle **voyage**, recopiée
telle quelle en tête du paquet, parce que le vrai destinataire n'est pas le programme.

Le choix est délibérément grossier : la sélection ne doit jamais être la partie
intelligente — une sélection fine écarterait le fait qui contredit l'hypothèse, exactement
celui qu'il fallait garder. Mais un texte libre laisse croire à une compréhension qui
n'existe pas, alors deux choses le compensent :

- **Ce qui a été compris est annoncé sur la sortie d'erreur** — « familles retenues d'après
  la question : … », ou « aucun mot-clé reconnu — vue d'ensemble : … ». La sortie standard
  reste le paquet seul, donc redirigeable.
- **`--help` donne la table des mots reconnus.** Un texte libre non documenté n'est pas
  découvrable ; celui-ci l'est, et un test garde la table contre le pourrissement.

| Si la question contient… | Familles jointes |
|---|---|
| `never` `dead` `unused` `uncovered` `not covered` | `class.never_executed` |
| `cover` `coverage` `percent` | `coverage.run` + `class` |
| `time` `slow` `hot` `cost` `perf` `fast` `profil` | `method.hot` |
| `source` `missing` `root` | `source.missing` + `source.hint` |
| `run` `campaign` `when` `machine` `command` | `run` |

**Un mot-clé ouvre un mot**, il n'est pas cherché n'importe où dans la phrase : « screenshot »
ne déclenche donc pas `hot`, ni « generate » `rate`. Les flexions restent attrapées — `missing`
et `manqu` couvrent « manquant », « manquantes ». **Les mots français sont reconnus aussi**,
sans être documentés : l'outil parle anglais, et une seconde table rendrait la première
illisible pour ceux à qui elle s'adresse.

```
$ runtime-xray --out rapport --contexte "welche Klassen liefen nie" > paquet.md
   aucun mot-clé reconnu dans la question — vue d'ensemble : execution, couverture…
```

### Pour un script : nommer les familles

Un script n'a pas besoin qu'on interprète une phrase, il a besoin d'un résultat
reproductible. `--familles` court-circuite les mots-clés :

```sh
runtime-xray --out rapport --contexte "classes mortes de la recette" \
             --families class.never_executed,coverage.run
```

La question continue de voyager — c'est son autre rôle — mais ne choisit plus rien. Une
famille inconnue **s'arrête**, avec la liste de celles qui existent : c'est l'inverse du
texte libre, et délibérément. Une phrase qu'on ne reconnaît pas vient d'un humain qui
cherche, donc on lui donne la vue d'ensemble ; une famille qui n'existe pas vient d'un
script qui a un défaut, et le lui taire produirait un paquet silencieusement différent de
ce qu'il croit lire.

**Aucune valeur capturée ne part.** Les valeurs de paramètres relevées par Arthas restent
dans leur bloc, sur le disque. Elles sont volumineuses, et elles portent parfois ce qu'on ne
veut pas voir voyager vers un service hébergé ailleurs. Deux tests le gardent, un sur le
fichier de faits, un sur le paquet de contexte.

## Trois niveaux d'effort

### Niveau 0 — copier-coller, ou un agent qui lit le dossier

Rien à installer, rien à configurer, aucune clé.

```bash
java -jar runtime-xray.jar --out runtime-xray-out --contexte "where does the time go?" | pbcopy
```

Et si l'on travaille déjà avec un assistant qui lit l'espace de travail — dans un éditeur,
dans un terminal — il n'y a même pas ça à faire : lui indiquer `runtime-xray-out/faits.jsonl`
suffit, le fichier explique son propre vocabulaire.

Deux compétences prêtes à recopier dans `.claude/skills/` portent ce qu'il faut savoir de
part et d'autre — conduire une campagne, et lire un rapport sans en tirer de fausse
conclusion : [`skills/`](../../skills/README.md).

**C'est le niveau à essayer d'abord**, et il couvre la plupart des besoins. Les deux suivants
ne servent que si l'on veut poser la question *sans humain dans la boucle*.

#### Le cas d'un assistant intégré à l'éditeur

Beaucoup d'assistants d'édition de code se branchent sur **une adresse et un modèle**, dans
la forme de requête décrite plus bas — c'est le montage le plus répandu, et il n'impose rien
sur l'éditeur du modèle : une passerelle auto-hébergée s'y déclare comme un service
commercial.

Pour eux, il n'y a **rien à intégrer** : ils lisent déjà l'espace de travail. Il suffit de
poser le rapport dedans et de nommer le fichier.

```
Read runtime-xray-out/faits.jsonl and tell me which classes never ran.
```

Le fichier porte son propre vocabulaire en première ligne, et la [compétence de
lecture](../../skills/README.md) porte les pièges — un zéro qui peut vouloir dire « pas
mesuré », une source introuvable qui n'est jamais « le code n'existe pas ». Recopier les
deux compétences dans `.claude/skills/` du projet suffit pour que l'assistant les applique
sans qu'on ait à les rappeler à chaque question.

Le paquet de `--contexte` reste utile quand le rapport est gros : il choisit, borne, et met
les absences en tête — ce qu'un assistant qui lit un fichier de plusieurs mégaoctets ne fera
pas de lui-même.

### Niveau 1 — une commande

`bin/demander.sh` prend une question, produit le contexte par l'outil, le poste, et écrit la
réponse. Aucune dépendance : `curl` et `jq`.

```bash
./bin/demander.sh --out runtime-xray-out "which classes never ran?"
./bin/demander.sh --api anthropic --modele … "where does the time go?"
./bin/demander.sh --contexte-seul --out runtime-xray-out "…"   # rien n'est envoyé
```

Le script est **délibérément mince**, et c'est tout son intérêt : le travail utile — choisir
les faits, joindre la légende, mettre les absences en tête, borner le volume — est fait par
l'outil, qui est éprouvé par des tests. Ce qui reste ici, c'est de poster un texte.

**La clé se lit dans l'environnement**, jamais sur la ligne de commande : celle-ci se
retrouve dans l'historique du terminal et dans la liste des processus, où tout le monde la
lit.

```bash
export XRAY_CLE=…          # ou OPENAI_API_KEY / ANTHROPIC_API_KEY / GEMINI_API_KEY
```

#### Pourquoi trois formes de requête et pas une

Il n'existe pas de standard. Il existe une forme majoritaire, ce qui n'est pas la même chose,
et une forme majoritaire qu'on prend pour un standard est un pari qu'on a oublié d'avoir
fait. Les trois enveloppes sont donc écrites côte à côte dans le script — on voit du premier
coup d'œil que seul l'emballage change :

| `--api` | Forme | La consigne système | Le texte de la réponse |
|---|---|---|---|
| `openai` | `POST …/chat/completions` | un message de rôle `system` | `.choices[0].message.content` |
| `anthropic` | `POST …/v1/messages` | un champ `system` à part | `.content[0].text` |
| `gemini` | `POST …/models/X:generateContent` | `system_instruction` | `.candidates[0].content.parts[0].text` |

**`openai` désigne la forme de la requête, pas le fournisseur.** C'est le point le plus
souvent mal compris : la plupart des passerelles auto-hébergées et des services commerciaux
acceptent cette forme, quel que soit le modèle derrière. Une passerelle interne se désigne
donc ainsi, sans que rien du montage ne suppose quoi que ce soit sur l'éditeur du modèle :

```bash
./bin/demander.sh --url https://passerelle.interne/v1/chat/completions --modele … "…"
```

Le jour où une quatrième forme s'impose, il y a une trentaine de lignes à écrire dans un
`case`, et rien d'autre à toucher — ni dans l'outil, ni dans le format.

### Niveau 2 — dans une chaîne d'intégration

Le contexte étant un texte sur la sortie standard, il se pose où l'on veut : dans un ticket,
dans un commentaire de proposition de fusion, dans un message d'équipe.

```bash
java -jar runtime-xray.jar --out "$OUT" --contexte "which classes never ran?" \
  > contexte.md
```

Deux précautions, et elles sont du même ordre que pour n'importe quel appel sortant :

- **Ce qui sort de la machine** est un texte de faits — noms de classes et de méthodes,
  chiffres de couverture et d'échantillons. Pas de code source, pas de valeurs capturées. Sur
  un code dont les noms de classes sont eux-mêmes sensibles, la question à trancher reste
  entière, et `--contexte-seul` permet de la regarder avant d'envoyer quoi que ce soit.
- **Un appel sortant est un appel sortant.** Sur le parc visé par cet outil — des machines
  sans réseau — le niveau 0 est le seul qui fonctionne, et il fonctionne très bien.

## Ce qui n'est pas fait, et pourquoi

**Pas de client HTTP dans l'outil.** Il faudrait y mettre un nom de fournisseur, une clé, une
politique de nouvelle tentative, un quota. Chacun de ces quatre points est une décision
d'exploitation, propre au site, qui n'a rien à faire dans un jar sans dépendance dont la
raison d'être est de tourner sur une machine fermée.

**Pas de serveur.** Le rapport est un dossier de fichiers ; les fichiers sont déjà une
interface, lisible par tout ce qui sait ouvrir un fichier, et qui survit à la version du
programme qui les a écrits. Un serveur ajouterait un port à ouvrir, un cycle de vie à
surveiller, et une raison de plus pour l'outil de ne pas démarrer.

**Pas de sélection intelligente des faits.** Voir plus haut : ce serait précisément l'endroit
où l'on perdrait le fait qui contredit l'hypothèse.
