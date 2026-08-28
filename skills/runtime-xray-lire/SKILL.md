---
name: runtime-xray-lire
description: Répondre à une question à partir d'un rapport runtime-xray déjà produit — quelles classes n'ont jamais tourné, où passe le temps, quelle exécution a couvert quoi, pourquoi le rapport ne montre pas ce qu'on attendait. À utiliser dès qu'un répertoire contient faits.jsonl, index.html + vue/, ou diagnostic.json. Mots-clés : code mort, couverture, classes jamais exécutées, méthodes chaudes, campagne de recette, rapport d'exécution, faits.jsonl.
---

# Lire un rapport runtime-xray

Un rapport est un **dossier**, pas un fichier. Ce qui compte pour répondre à une question
n'est pas la page.

| Fichier | Ce qu'il porte | Pour qui |
|---|---|---|
| `faits.jsonl` | les conclusions, un objet JSON par ligne | **un programme, un modèle — commencer ici** |
| `diagnostic.json` | ce que l'outil a réellement fait : environnement, config, racines ouvertes, rapprochement classe par classe | quand le rapport déçoit |
| `index.html` + `vue/` | la page, chargée par morceaux | un humain devant un navigateur |
| `rapport.md`, `synthese.svg` | la synthèse à coller ailleurs | un ticket, un message |
| `runs/<nom>/` | les mesures brutes de chaque exécution | un retraitement |

**Ne pas lire `index.html` pour répondre à une question.** C'est une application, pas des
données : le code y est rangé pour être affiché, pas pour être interrogé, et les gros blocs
sont dans `vue/`.

## Le chemin le plus court

```sh
java -jar runtime-xray.jar --out <report-dir> --contexte "which classes never ran?"
```

Ça écrit sur la sortie standard un extrait **borné et prêt à lire** : les faits qui
répondent, leur vocabulaire, et en tête ce qui n'a *pas* été mesuré. Rien n'est envoyé nulle
part.

La question **choisit** les faits joints — par simples mots-clés **anglais**, pas par
compréhension : `never` `dead` `unused` pour les classes mortes, `time` `slow` `hot` pour le
temps, `cover` `coverage` pour la couverture, `source` `missing` `root` pour les sources,
`run` `when` pour les exécutions. Un mot-clé **ouvre un mot** : « screenshot » ne déclenche
pas `hot`. Les mots français marchent aussi. La question **voyage** ensuite dans le paquet. Ce qui a été retenu est annoncé sur la **sortie d'erreur** ;
`--help` donne la table des mots reconnus. Pour un résultat reproductible, nommer les
familles au lieu de les faire déduire :

```sh
java -jar runtime-xray.jar --out <le-dossier> --contexte "…" \
     --familles classe.jamais_executee,methode.chaude
```

Si le jar n'est pas à portée, lire `faits.jsonl` directement : **sa première ligne contient
son propre dictionnaire**, il n'y a pas de documentation à aller chercher.

```sh
head -1 <report-dir>/faits.jsonl | python3 -m json.tool
grep '"fait":"classe.jamais_executee"' <report-dir>/faits.jsonl
```

## Le vocabulaire

| Valeur de `fait` | Ce que la ligne dit |
|---|---|
| `campagne` | l'en-tête : outil, version, date, et le dictionnaire lui-même |
| `execution` | une exécution observée : identité, commande, machine |
| `indisponibilite` | une mesure qui **n'a pas été prise**, et pourquoi |
| `reserve` | une mesure prise, mais dont l'outil limite lui-même la portée |
| `couverture.execution` | instructions couvertes sur le total, pour une exécution |
| `classe` | une classe, sa meilleure couverture, et les exécutions qui l'ont couverte |
| `classe.jamais_executee` | une classe analysée qu'**aucune** exécution n'a atteinte |
| `methode.chaude` | une méthode parmi les plus coûteuses, en relevés de pile |
| `source.introuvable` | couverture connue, code source non — racine non configurée |
| `piste.source` | une racine à ajouter, avec ce qu'elle résoudrait |

Toute mesure porte son unité (`"mesure"`). Une mesure sans unité n'existe pas dans ce
fichier ; si on croit en voir une, c'est qu'on lit autre chose.

## Trois pièges de lecture — les tenir avant de répondre

**1. Un zéro ne veut pas dire « n'a pas tourné ».** Il peut vouloir dire « n'a pas été
mesuré ». **Lire les `indisponibilite` AVANT tout chiffre**, sans exception. Le cas le plus
courant : sous Windows, il n'y a aucune mesure de temps, et tout temps y est à zéro.

**2. Une source introuvable n'est jamais « le code n'existe pas ».** C'est une racine de
sources non configurée. La réponse est une ligne `SOURCE_DIRS=` à ajouter, pas un constat
sur le code.

**3. Un taux de couverture dit par où le code est passé.** Il ne dit rien de sa justesse,
ni de la qualité des tests. Ne jamais le présenter comme une note.

## Comment répondre

- **Citer les noms exactement** comme ils apparaissent — classes, méthodes, exécutions. Un
  nom approché renvoie le lecteur à une recherche qui ne trouve rien.
- **Dire ce sur quoi on ne peut pas conclure**, et pourquoi, plutôt que de supposer. Une
  réponse assurée tirée d'un zéro non mesuré coûte plus cher qu'une absence de réponse.
- **Ne jamais présenter un sous-ensemble comme un total.** Si l'extrait annonce des faits
  écartés, le dire : ni total, ni classement.
- **Une classe jamais exécutée n'est pas du code mort.** C'est du code qu'*aucune des
  exécutions observées* n'a atteint. Nommer les exécutions en question (`executionsAnalysee`)
  fait la différence entre un constat et une accusation.

## Quand le rapport ne montre pas ce qu'on attendait

Ouvrir `diagnostic.json`, et regarder dans cet ordre :

1. `sources` — quelles racines ont été réellement ouvertes, et combien de fichiers indexés.
2. `rapprochement` — classe par classe : ce que la couverture cherchait, ce que l'index a
   trouvé.
3. `executions` — la configuration réelle du lancement, qui n'est pas toujours celle qu'on
   croit avoir passée.

Puis **identifier laquelle des quatre restrictions est en cause** — elles ne restreignent
pas la même chose, et élargir la mauvaise ne change rien :

| Symptôme | La seule option qui le commande |
|---|---|
| pas de code affiché | `--sources` |
| pas de valeurs capturées | `--root` |
| pas de temps sur du code applicatif | `--filter` |
| pas de couverture sur une classe | `--cover` |

## Ce qu'on ne fait pas

- **Ne pas deviner une racine de sources** d'après une convention de projet. Sur la machine
  où l'application tourne loin de son code, la convention ne désigne rien — ou pire, désigne
  les sources d'un autre projet. `piste.source` propose des racines *vérifiées*, chacune avec
  le nombre de classes qu'elle résoudrait : les utiliser, elles.
- **Ne pas relancer une campagne pour répondre à une question de lecture.** Le cumul de
  couverture sur un sous-ensemble d'exécutions se calcule hors ligne, sur des données déjà
  là. Et `--report-only` réassemble la page sans rien réexécuter.
- **Ne pas faire sortir de valeurs capturées de la machine.** Elles restent dans leur bloc,
  sur le disque, et l'outil ne les met jamais dans les faits ni dans un contexte.
