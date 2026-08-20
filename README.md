# Runtime X-Ray

Étude comparative d'outils d'analyse dynamique pour Java, et l'outil qui en découle.

**→ [Consulter un rapport produit sur une exécution réelle](https://beennnn.github.io/runtime-xray/multi/)**

C'est le livrable ; tout ce qui suit explique comment on y est arrivé, et
[comment le lire](docs/outil/lire-le-rapport.md).

## Objet

Disposer, à l'exécution d'une fonction Java, de trois informations :

1. les lignes exécutées ;
2. l'arbre des appels ;
3. en option de seconde priorité, les valeurs passées en paramètres.

Finalité : disposer d'une base factuelle pour analyser, restructurer et redéfinir un code
existant — plutôt que de s'en remettre à la lecture seule.

## Contraintes

| Contrainte | Conséquence sur la sélection |
|---|---|
| Exécution hors ligne | Écarte les services hébergés. Le téléchargement préalable des composants reste possible |
| Java 21 et Java 25 | Les deux plateformes sont évaluées ; l'écart mesuré est reporté dans les [résultats](docs/resultat/resultats.md#gains-dun-portage-vers-java-25) |
| IntelliJ comme environnement cible | Sans en dépendre : l'édition payante n'est pas supposée disponible |
| Deux publics | Un développeur dans son environnement, et un lecteur non technicien devant un document transmis |
| Diagnostic ponctuel | Aucun serveur à déployer ni à maintenir |

Le temps de calcul et la mémoire sont relevés à titre indicatif, mais ne font pas partie
des critères de sélection. L'optimisation des performances est un sujet distinct, qui
suppose que le code soit d'abord compris.

## Démarche

![La démarche](docs/assets/approach.svg)

Dix-huit outils ont été recensés, puis évalués sur cinq critères ordonnés : facilité de
mise en œuvre, lisibilité du rapport pour un lecteur non technicien, couverture
fonctionnelle, intégration à l'IDE, coût. Chaque affirmation du comparatif porte un statut
indiquant si elle a été vérifiée par exécution ou reprise de la documentation de l'éditeur.

Le protocole et les critères sont décrits dans [la méthode](docs/etude/methode.md).

## Résultat

Aucun des outils examinés ne répond seul aux trois questions. Une combinaison de trois
outils libres y parvient, dans les limites énoncées ci-dessous :

| Outil | Licence | Ce qu'il apporte |
|---|---|---|
| JaCoCo | EPL 2.0 | Les lignes exécutées, de façon exhaustive |
| async-profiler | Apache 2.0 | Les mesures de temps et l'arbre agrégé des appels |
| Arthas | Apache 2.0 | Les valeurs des paramètres, et le chemin suivi par un appel donné |

Leurs sorties sont assemblées en une page unique par un orchestrateur écrit pour cette
étude. Le détail du montage et ses réserves sont dans [la solution
retenue](docs/resultat/solution.md).

![La vue produite : l'arbre d'appel à gauche, le code annoté au centre, les appels comparés en bas](docs/assets/shots/vue-arbre-appel.png)

La page réunit deux entrées vers le même code : le code rangé par paquet, et les exécutions
présentées comme un arbre d'appel — c'est cette seconde entrée qui est ouverte ci-dessus.

Les lignes exécutées apparaissent en vert, celles jamais atteintes en rouge. À droite de
chaque ligne, les appels qui en partent, avec le nombre de passages et l'étendue des durées
observées : `×10` sur une ligne dans une boucle se déplie en un nœud par itération. En bas,
les appels observés sont mis en regard, une colonne par champ, les colonnes qui varient
signalées — c'est là que se lit ce qui a fait diverger deux exécutions de la même méthode.

Un exemple produit par cette combinaison est
[consultable en ligne](https://beennnn.github.io/runtime-xray/multi/).

## Portée et limites de l'étude

Ces résultats valent dans le cadre décrit, et pas au-delà. En particulier :

- Les outils commerciaux n'ont pas été exécutés. JProfiler, YourKit et les autres sont
  évalués sur leur documentation et leurs captures publiques. Les démarches pour obtenir
  des clés d'évaluation sont décrites dans [les clés
  d'évaluation](docs/etude/cles-evaluation.md), mais elles n'ont pas été entreprises. Ce
  qu'on en attendrait est énoncé, pas constaté.
- Un seul programme de démonstration a servi de support, sur une seule machine
  (macOS, Apple Silicon). Le comportement sur un code industriel — plusieurs milliers de
  classes, plusieurs fils d'exécution, un serveur d'application — n'a pas été observé.
- La capture des valeurs perturbe la mesure du temps : elle est menée dans la même
  exécution, ce qui surestime le coût de la méthode observée. La réserve est affichée dans
  le rapport et chiffrée ; elle est acceptée parce que le temps n'est pas un critère.
- Les valeurs ne sont capturées que sur une méthode, désignée au lancement. Observer
  toutes les méthodes produirait un volume ingérable.
- L'écart entre Java 21 et Java 25 a été mesuré sur ce seul programme et n'a pas montré
  de gain pour la combinaison retenue. Cela ne préjuge pas d'autres cas.

## L'outil

L'orchestrateur est un jar sans dépendance hors du JDK. Il exécute la commande fournie
telle quelle et injecte les observateurs par `JAVA_TOOL_OPTIONS`, que toute JVM lit à son
démarrage ; ni le code analysé ni le build ne sont modifiés.

```bash
java -jar runtime-xray.jar \
  --java "java -jar target/mon-appli.jar" \
  --root "com.exemple.Calculateur::calculer" \
  --sources src/main/java
```

Le rapport est écrit dans `runtime-xray-out/index.html`.

Le bytecode à analyser n'est pas demandé : il est lu sur les arguments réels de la JVM
observée, ce qui vaut aussi pour un lanceur qui masque la commande. Quand ces arguments ne
portent pas le classpath applicatif — c'est le cas de `mvn exec:java`, où l'application
tourne dans la JVM de Maven — la disposition du projet prend le relais, si elle existe
vraiment. `--classes` reste disponible pour analyser autre chose : une dépendance interne
livrée compilée, un jar « gras », ou un module précis.

Les composants d'analyse sont récupérés une fois depuis un dépôt Maven — celui de l'éditeur
ou un miroir interne — puis mis en cache dans `~/.runtime-xray`. Les exécutions suivantes
n'accèdent plus au réseau.

Chaque rapport enregistre son propre contexte : commande lancée, méthode racine, filtres,
heure de début et de fin, durée, machine, système, version de Java.

[Mode d'emploi complet](docs/outil/mode-emploi.md) — paramètres, fichier de configuration,
choix de la méthode racine, publication du résultat.
[Lire le rapport](docs/outil/lire-le-rapport.md) — ce que la page montre, ce qu'elle replie,
et pourquoi.

## Documents

Trois dossiers, selon la question traitée.

**`docs/etude/` — la recherche**

| Page | Contenu |
|---|---|
| [Méthode](docs/etude/methode.md) | Critères, ordre de priorité, protocole de vérification |
| [Comparatif](docs/etude/comparatif.md) | Les 18 outils, avec le statut de vérification de chaque affirmation |
| [Fiches par outil](docs/etude/fiches) | Une page par outil : capacités, licence, limites |
| [Outils écartés](docs/etude/outils-ecartes.md) | Les rejets, motivés et datés |
| [Clés d'évaluation](docs/etude/cles-evaluation.md) | Démarches pour essayer les outils commerciaux |

**`docs/resultat/` — les conclusions**

| Page | Contenu |
|---|---|
| [Solution retenue](docs/resultat/solution.md) | Le montage, le protocole, et ses réserves |
| [Résultats et arbitrages](docs/resultat/resultats.md) | Ce qui est décidé, ce qui reste ouvert |
| [Formats et découplage](docs/resultat/formats.md) | Changer d'affichage sans changer la collecte |
| [Publication du rapport](docs/resultat/publication.md) | Où déposer une page HTML pour qu'elle soit lisible |

**`docs/outil/` — l'usage**

| Page | Contenu |
|---|---|
| [Mode d'emploi](docs/outil/mode-emploi.md) | Paramètres, configuration, méthode racine |
| [Lire le rapport](docs/outil/lire-le-rapport.md) | Ce que la page montre, ce qu'elle replie, et ce qu'on peut lui demander |
| [Détails techniques](docs/outil/technique.md) | Programme de démonstration et difficultés rencontrées |
| [Diffusion](docs/outil/distribution.md) | Dépôt interne, Maven Central, et ce qu'a apporté la réécriture |

## Organisation du dépôt

| Répertoire | Contenu |
|---|---|
| `orchestrator/` | L'outil : un module Maven sans dépendance d'exécution |
| `sample-app/` | Le programme de démonstration : un calcul de temps de trajet dont le graphe d'appel dépend du contexte, avec du code volontairement jamais exécuté |
| `tools/` | Le protocole manuel de l'étude : l'invocation native de chaque outil, conservée pour que les affirmations du comparatif restent vérifiables |
| `docs/` | L'étude |
| `site/` | La page d'accueil publiée |

Pour reproduire la démonstration :

```bash
mvn -q clean package
java -jar orchestrator/target/runtime-xray.jar \
  --java "java -jar sample-app/target/sample-app.jar" \
  --root "lab.sample.RoutePlanner::travelTimeMinutes" \
  --sources sample-app/src/main/java
```

## Licence

MIT — voir [LICENSE](LICENSE).
