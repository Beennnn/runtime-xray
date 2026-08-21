# Annoter les exécutions — seul, sur son poste, ou à plusieurs

> Une mesure sans nom ne se retrouve pas. Ce document décrit ce qu'on peut ajouter à une
> exécution après l'avoir mesurée, où ça s'écrit, et les trois façons de faire vivre ces
> annotations — du navigateur seul au serveur partagé.

## Le problème

Trois exécutions dans un rapport, c'est déjà trois répertoires horodatés dont les noms ne
disent rien. Au bout d'une semaine, personne ne sait plus laquelle portait le jeu de
données réduit, laquelle a servi à la démonstration, ni pourquoi celle du milieu avait été
relancée.

Ce que la mesure ne peut pas produire — **ce qu'on cherchait, ce qu'on a compris, ce qu'il
reste à vérifier** — doit donc pouvoir s'écrire après coup, et rester attaché à
l'exécution. C'est tout l'objet de ce qui suit.

## Ce qu'on peut ajouter

| | Ce que c'est | À quoi ça sert |
|---|---|---|
| **Nom** | Le libellé affiché partout où l'exécution apparaît | Retrouver *celle-là* dans un sélecteur qui en montre cinq |
| **Description** | Du texte libre | Dire ce qu'on cherchait et ce qu'on a vu — le rapport ne le devinera jamais |
| **Étiquettes** | Des clés, chacune avec un texte **facultatif** | Rattacher à un ticket, un environnement, une version ; marquer « à rejouer » sans autre commentaire |
| **Élagage** | Des branches coupées, une racine ramenée | Ne montrer que la branche qui compte — voir plus bas |

Tout cela se saisit dans la page, sur le **bandeau d'identité** posé sous le nom de
l'exécution, ou s'écrit à la main dans un fichier. Les deux chemins mènent au même endroit.

## Trois identités, et pourquoi il en faut trois

| | Ce que c'est | Change ? |
|---|---|---|
| **Identifiant** | Généré au lancement, écrit dans `run-context.json` | **jamais** — c'est la clé stable, celle qui survit à tous les renommages |
| **Nom au lancement** | Le `--name` donné à la mesure | jamais — il dit ce qu'on **croyait** mesurer, et permet le retour arrière |
| **Nom dans l'outil** | Posé après coup | oui, autant de fois qu'on veut — il dit ce qu'on a **compris** |

Le nom affiché suit cet ordre : **le nom posé dans l'outil**, sinon **celui du lancement**,
sinon **l'identifiant abrégé**. Rien n'est inventé au passage : une exécution lancée sans
`--name` s'affiche sous son identifiant, jamais sous un libellé de commodité — « exécution
du 21/08 à 00:41 » se ferait passer pour une intention de l'opérateur alors que ce n'en est
pas une.

## Les trois modes

Une page ouverte comme fichier ne peut rien écrire sur le disque : c'est une règle du
navigateur, et c'est aussi ce qui rend la page transmissible telle quelle. D'où trois
façons de faire vivre les annotations, du plus simple au plus partagé. **Aucune n'exclut
les autres** : c'est le même format, et on passe de l'une à l'autre sans rien convertir.

| | Ce qu'on fait | Ce que ça donne | Ce que ça suppose |
|---|---|---|---|
| **1. Chacun dans son navigateur** | Ouvrir la page, annoter | Immédiat. Les annotations restent dans ce navigateur d'une visite à l'autre. **Exporter** rend un fichier, **importer** reprend celui d'un collègue | Rien |
| **2. Sur son poste** | `--serve`, puis annoter | La page écrit à côté des exécutions et le rapport est régénéré : l'annotation est acquise, y compris pour qui rouvrira le fichier sans serveur | Lancer l'outil |
| **3. Un serveur partagé** | `--serve --serve-host 0.0.0.0` sur une machine où l'on dépose les résultats | Tout le monde y accède par un navigateur et **annote en parallèle**, sans s'écraser | Une machine, et un accès filtré ou un secret partagé |

```bash
# 2. sur son poste — http://localhost:8787
java -jar runtime-xray.jar --report-only --out runtime-xray-out --serve

# 3. serveur partagé — on y dépose des répertoires d'exécution, tout le monde lit et annote
java -jar runtime-xray.jar --report-only --out /srv/runtime-xray --serve 8080 --serve-host 0.0.0.0

# 3 bis. le même, gardé par un secret partagé (tiré au sort et affiché une fois)
java -jar runtime-xray.jar --report-only --out /srv/runtime-xray --serve 8080 \
  --serve-host 0.0.0.0 --serve-token
```

La page reconnaît d'elle-même le mode où elle se trouve : servie par l'outil, elle propose
**enregistrer** dans sa barre du haut ; ouverte comme fichier ou depuis un hébergement
statique, elle s'en tient à **exporter** et **importer**, et le bandeau d'identité annonce
alors *gardé dans ce navigateur*.

### Annoter à plusieurs

C'est la seule difficulté réelle du mode 3, et elle est traitée là où elle se pose :

- **L'écriture porte sur une exécution**, jamais sur le fichier entier. Deux personnes qui
  annotent deux exécutions ne se voient donc jamais.
- Sur la **même** exécution, chacun envoie l'empreinte de ce qu'il avait sous les yeux. Si
  quelqu'un est passé entre-temps, le second reçoit **« modifiée entre-temps »** et la
  version enregistrée, et choisit : garder la sienne, ou *reprendre la version
  enregistrée*. Personne n'écrase personne en silence.
- La page **relit le serveur toutes les quinze secondes** : les noms posés par les autres
  arrivent tout seuls, sans recharger.
- Tant qu'une saisie n'est pas envoyée, le bandeau l'annonce — *non enregistré* — et le
  bouton **enregistrer** se marque d'un point.

### Déposer des résultats pendant qu'il tourne

C'est le geste même du mode 3 : on copie un répertoire d'exécution dans `runs/`, et tout le
monde doit le voir. Le serveur **sonde le répertoire toutes les dix secondes** et réassemble
la page dès qu'une exécution apparaît ou disparaît — il n'y a rien à redémarrer.

Les pages ouvertes ne se rechargent pas d'elles-mêmes : quelqu'un est peut-être en train de
lire une méthode ou d'écrire une description. Elles l'annoncent, en bas à droite — *« Le
rapport a changé sur le serveur — 4 exécutions à présent »* — et laissent recharger quand
c'est le moment.

Le sondage est délibéré, plutôt qu'une surveillance du système de fichiers : les résultats
arrivent souvent par un partage réseau, où les notifications de modification sont au mieux
irrégulières. Dix secondes suffisent — on ne dépose pas une exécution dix fois par minute.

### Fermer la porte : `--serve-token`

Les modes 1 et 2 n'ont rien à garder — la boucle locale ne laisse entrer que la machine
elle-même. Le mode 3 change la question : quiconque atteint le port lit les rapports et
annote. D'où un secret partagé, **facultatif** :

```bash
--serve-token "phrase choisie"   # celui-ci, et pas un autre
--serve-token                    # sans valeur : tiré au sort et affiché une fois
XRAY_SERVE_TOKEN=... --serve     # même effet, sans l'exposer dans « ps »
```

Ce que cela donne :

- un visiteur tombe sur une page d'entrée, saisit le secret, et **la session dure douze
  heures** — il ne le retape pas à chaque page ;
- la session est un cookie `HttpOnly`, `SameSite=Strict`, qui **ne contient pas le secret** ;
- un script ou un `curl` passe par `Authorization: Bearer <secret>`, sans formulaire ;
- après cinq essais ratés depuis la même adresse, on cesse de répondre pendant une
  trentaine de secondes ;
- si la session expire pendant qu'on annote, **rien n'est perdu** : la page le dit, ce qui
  n'était pas encore enregistré reste dans le navigateur, et il suffit de se reconnecter.

Sans l'option, rien ne change : le serveur reste ouvert, et le dit au démarrage.

```
⚠️ Écoute au-delà de la boucle locale, SANS authentification :
   quiconque atteint ce port peut lire les rapports et annoter.
   À placer derrière ce qui filtre déjà les accès, ou à garder
   par --serve-token.
```

### Ce que ce secret vaut, et ce qu'il ne vaut pas

Un secret partagé, ce ne sont pas des comptes. **L'outil ne sait pas qui annote** — il ne
l'a jamais su, et les fichiers d'annotation ne portent aucun auteur. Le prétendre serait
mentir sur ce qu'ils contiennent.

Trois réserves, à lire avant de déployer :

- **En HTTP simple, le secret circule en clair.** Pour qu'il protège vraiment, il faut du
  TLS devant, terminé par un mandataire. Sur un réseau interne de confiance il arrête un
  passant ; il n'arrête pas quelqu'un qui écoute le réseau.
- **Un secret sur la ligne de commande se lit dans `ps`** par les autres comptes de la
  machine. `XRAY_SERVE_TOKEN` existe pour cela.
- **Il doit tenir en ASCII imprimable.** Un accent ne traverse ni l'en-tête
  `Authorization` ni certaines variables d'environnement : l'outil le refuse au démarrage,
  avec sa raison, plutôt que de laisser un 401 inexplicable une fois déployé.

Autrement dit il **complète** un filtrage réseau, il ne le remplace pas. C'est un outil de
diagnostic, pas un service : la seule écriture qu'il accepte est l'annotation d'une
exécution, dans un fichier dont il choisit lui-même le nom, et le service de fichiers
refuse tout chemin qui sortirait du répertoire servi.

## Où le fichier est écrit

En vision fichier, **une exécution est un répertoire**. Son annotation peut vivre à trois
endroits, et l'ordre de priorité est celui-ci :

| Emplacement | Ce que ça implique |
|---|---|
| `runs/<exécution>/config.json` | **Prioritaire.** L'annotation est *dans* l'exécution : elle la suit partout — copie, archive, envoi à un collègue |
| `runs/<exécution>-config.json` | À côté du répertoire, même nom suffixé. L'exécution reste intacte : utile si elle est en lecture seule, signée, ou produite par quelqu'un d'autre |
| `noms.json` | Un seul fichier à la racine, indexé par identifiant. C'est le **format d'échange** : celui qu'exporte la page |

Deux règles, et elles se justifient de la même façon :

- **le plus proche de l'exécution l'emporte** — celui qui a rangé l'annotation avec la
  mesure a exprimé une intention plus précise que celui qui a rempli le fichier commun ;
- **la source retenue est prise entière** — mélanger un nom venu d'un fichier et une
  description venue d'un autre donnerait une annotation que personne n'a écrite, et que
  personne ne saurait corriger.

Le serveur, lui, écrit **là où l'annotation vit déjà**, et dans le répertoire de
l'exécution si elle n'existait pas encore.

### Les deux formes

Un fichier d'exécution ne porte pas d'identifiant — il est déjà dans le bon répertoire :

```json
{
  "nom": "Recette du 21/08 — nuit",
  "description": "24 M d'itérations. Vérifier la branche météo.",
  "etiquettes": { "ticket": "RX-142", "à-rejouer": "" },
  "elagage": {
    "racine": "lab/sample/Main.main;lab/sample/RoutePlanner.travelTimeMinutes",
    "coupes": ["lab/sample/Main.main;lab/sample/Scenarios.at"]
  }
}
```

Le fichier commun, lui, est indexé par identifiant, et accepte le nom seul — c'est la forme
qui circulait avant les descriptions et les étiquettes, et elle reste lue telle quelle :

```json
{
  "8BF7DA2E-1C5A-4435-A3E3-4FE50CD41A2F": "Recette — jeu de données réduit",

  "637985B7-4F91-46E6-BD08-8980D92653E8": {
    "nom": "Recette du 21/08 — nuit",
    "description": "24 M d'itérations. Vérifier la branche météo.",
    "etiquettes": { "ticket": "RX-142" }
  }
}
```

Supprimer une entrée rétablit le nom d'origine. À la fin de chaque mesure, l'outil rappelle
l'identifiant de l'exécution et les deux formes possibles, prêts à être collés.

## Élaguer l'arbre

Un arbre d'appel complet montre **tout** ce qui a tourné : le démarrage, les rouages, les
branches qui n'intéressent personne aujourd'hui. Ce qu'on transmet à quelqu'un, c'est
souvent **une** branche. Deux gestes, sous le bouton *élaguer* au bout de chaque ligne de
l'onglet **Exécutions** :

| Geste | Ce qu'il fait |
|---|---|
| **Couper cette branche** | Retire ce nœud et tout ce qui en part — **sur ce chemin seulement**. La même méthode appelée depuis ailleurs reste visible |
| **Repartir d'ici** | Masque les niveaux au-dessus : l'arbre commence à ce nœud |

Les chemins s'écrivent comme dans le fichier de piles repliées — les frames de la racine au
nœud, séparées par `;`. Un chemin devenu introuvable, parce que la mesure a changé, est
ignoré plutôt qu'appliqué de travers.

**Ce que l'élagage ne fait pas : corriger la mesure.** Les pourcentages restent rapportés
au total mesuré, les fichiers d'origine et [les exports](exports.md) ne bougent pas, et un
bandeau rappelle en permanence ce qui est coupé, avec de quoi tout rétablir. Le graphe de
temps de la vue d'ensemble suit le même cadrage — deux endroits de la page ne doivent pas
raconter deux histoires.

## Depuis la page

Le **bandeau d'identité**, sous le nom de l'exécution en vue d'ensemble, tient sur une
ligne : l'identifiant abrégé, le nom — modifiable sur place, c'est le titre de la vue —, la
description, les étiquettes, et l'état de la saisie. Ce qui ne se modifie pas ne prend pas
de place : le nom du lancement et l'identifiant complet vivent dans l'infobulle du champ
qu'ils expliquent.

Rien d'éditable n'est posé dans le corps de la page : **le rapport se lit, il ne se remplit
pas**. Les gestes, eux, rejoignent les autres boutons dans la barre du haut, sous
**annotations** :

| Bouton | Ce qu'il fait |
|---|---|
| **enregistrer** | Écrit l'annotation de cette exécution à côté de ses résultats. N'apparaît que si la page est servie par l'outil, et se marque d'un point tant qu'il reste quelque chose à envoyer |
| **exporter** | Télécharge le fichier d'annotations de tout le rapport — à déposer à côté des exécutions, ou à envoyer |
| **importer** | Reprend un fichier existant : un `noms.json` entier, ou le `config.json` d'une seule exécution |
| **JSON** | Affiche ce qui serait écrit, par-dessus la page, pour le relire ou le copier |

La même barre porte, sous **exports**, les quatre formats destinés à d'autres outils. Ceux
qui n'ont pas été produits y restent **nommés mais éteints** : une capacité qui ne vit que
dans une option de ligne de commande n'existe pas pour qui lit la page. Un clic dessus donne
la commande qui les produit — voir [Reprendre le résultat dans un autre outil](exports.md).

## Réserves

- **Le navigateur oublie.** Mode 1, les annotations vivent dans le stockage local du
  navigateur : elles disparaissent avec lui, ne suivent pas d'une machine à l'autre, et ne
  sont pas visibles par les autres. C'est pour cela que l'export existe.
- **Les annotations ne sont pas dans les exports.** [Ce qui part vers un autre
  outil](exports.md) est la mesure, pas ce qu'on en a dit — un profil `perf` n'a pas
  d'endroit où loger une description.
- **Le secret partagé n'identifie personne.** Il dit qui peut entrer, pas qui a écrit
  quoi : deux personnes derrière le même secret sont indiscernables dans les fichiers
  d'annotation. C'est suffisant pour un outil de diagnostic ; ce ne l'est pas pour un
  registre de décisions.
- **Le serveur régénère la page après chaque écriture.** Sur un rapport très gros, cela
  prend quelques secondes ; c'est fait en arrière-plan, et plusieurs écritures rapprochées
  ne déclenchent qu'un assemblage de plus, à la fin.
- **Ni verrou ni historique.** Deux personnes sur la même exécution sont départagées au
  moment d'écrire, mais rien ne conserve la version remplacée : on la voit au moment du
  refus, pas après.

## À lire ensuite

- [Mode d'emploi](mode-emploi.md) — tous les paramètres, dont `--serve` et `--serve-host`
- [Lire le rapport](lire-le-rapport.md) — ce que la page montre, ce qu'elle replie
- [Reprendre le résultat dans un autre outil](exports.md) — perf, cpuprofile, LCOV
