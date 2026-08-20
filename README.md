<p align="center">
  <img src="docs/assets/banner.svg" alt="Runtime X-Ray — analyse dynamique Java : lignes exécutées, arbre d'appel, valeurs des paramètres" width="100%">
</p>

# Runtime X-Ray — outils d'analyse dynamique Java

**→ [Voir les résultats et les arbitrages à trancher](docs/RESULTS.md)**

## Objectif du projet

Mettre en place des outils d'**analyse dynamique** pour un code Java permettant, **à
l'exécution d'une fonction**, de récupérer :

- les **lignes de code exécutées** (couverture) ;
- les **arbres d'appel** (call graphs / call trees) ;
- les **valeurs passées en paramètres**.

**But final : analyser, restructurer et redéfinir le code à partir de ces données.**

Ce qui compte, concrètement : pouvoir **savoir facilement par où le code est passé**,
**voir l'arbre d'appel**, et **naviguer dans le code** — que l'on soit développeur
(dans l'IDE) ou non (dans une page web autonome).

## Contraintes

| Contrainte | Conséquence sur le choix |
|---|---|
| 🔌 **Environnement déconnecté d'Internet** | Éliminatoire pour tout SaaS (Datadog, New Relic, Codecov, SonarCloud) et pour tout outil qui télécharge ses composants au lancement. L'outil doit s'installer une fois, hors ligne, et fonctionner sans rappeler la maison. |
| ☕ **Langage : Java** | — |
| 🧭 **IDE cible : IntelliJ** | L'intégration IDE se juge sur IntelliJ en priorité ; à défaut, une page web navigable fait office d'équivalent. |
| 👥 **Deux publics** | Un développeur lit dans son IDE ; un non-technicien a besoin d'une page qu'on lui envoie et qu'il ouvre seul, sans installation ni compte. |
| 🎯 **Diagnostic ponctuel, pas supervision continue** | Un outil qui exige de déployer un collecteur et un backend est disproportionné. |

> La contrainte hors-ligne est la plus structurante : elle retire d'emblée une famille
> entière de solutions (les plateformes SaaS), et elle disqualifie aussi les outils dont
> le lanceur récupère ses modules sur Internet au démarrage — cas rencontré ici même avec
> Arthas, qui n'a pas pu être testé pour cette raison exacte.

## Critères d'évaluation, par ordre de priorité

1. **Facilité de mise en œuvre** — pouvoir lancer l'outil sans configuration lourde.
2. **Lisibilité des rapports** — rapports exploitables par des utilisateurs non techniques.
3. **Couverture fonctionnelle** — couverture de lignes, arbres d'appel, capture des
   valeurs de paramètres.
4. **Intégration IDE** — idéalement dans IntelliJ, ou une page web de code annoté
   permettant de naviguer hors IDE.
5. **Coût** — gratuit/open source vs commercial, et si le payant se justifie.

> **L'ordre compte** : il fait qu'« un outil qui se lance avec un flag » pèse davantage
> qu'« un outil au call tree plus riche mais qu'il faut configurer une demi-journée ».
>
> **Hors critères** : le temps de calcul et la RAM consommée sont mesurés par
> l'application-cible et rapportés **à titre de bonus**. Ils n'entrent pas dans le choix.

## Méthode

On ne compare que ce qu'on a **fait tourner**. Tous les outils analysent la **même
fonction**, dans le **même scénario déterministe**, et déposent leur sortie réelle dans
[`reports-demo/generated/`](reports-demo/).

1. **Une application-cible commune** — [`sample-app/`](sample-app/), 22 classes réparties
   en 8 packages, sans aucune dépendance externe : ce qu'un outil affiche vient de ce
   code, pas du bruit d'un framework. Exécution calibrée à **~10 secondes**, le minimum
   pour qu'un outil qui s'attache à un process vivant ait le temps de faire quelque chose.
2. **Un dossier par outil** — [`tools/<outil>/collect.sh`](tools/), la procédure exacte,
   rejouable.
3. **Les sorties versionnées** — pour pouvoir les rouvrir, les comparer, et vérifier une
   affirmation du tableau sans relancer quoi que ce soit.
4. **Un statut de vérification explicite** — ✅ testé ici / ⛔ bloqué par licence /
   🚧 bloqué techniquement / 📄 sur documentation. Une lecture de documentation n'est
   jamais présentée comme une mesure.

Point structurant : **ces outils ne sont pas des dépendances Maven.** Ils s'intègrent de
trois façons différentes — agent JVM (`-javaagent` / `-agentpath`), attachement à un
process vivant, ou option native du JDK — et cette différence est elle-même un critère.

<p align="center">
  <img src="docs/assets/approach.svg" alt="Les cinq étapes de la démarche et les trois livrables" width="100%">
</p>

## La fonction sous analyse

Tout tourne autour d'une question que tout le monde comprend : **« combien de temps pour
aller de A à B ? »** —
[`RoutePlanner.travelTimeMinutes(Trip)`](sample-app/src/main/java/lab/sample/RoutePlanner.java).

Le code est conçu pour **départager les outils** : selon le contexte du trajet, ce ne sont
pas les mêmes fonctions qui sont appelées.

| Contexte | Ce qui s'exécute — ou pas |
|---|---|
| Voiture à l'heure de pointe | le calcul de bouchons s'exécute ; sinon tout ce sous-arbre est absent |
| Train | les correspondances et la consultation d'horaires (seule opération **bloquante**) |
| Vélo / à pied | la météo et les bagages ralentissent ; le train les ignore |
| Tous sauf le train | le relief, calculé sur 24 sous-segments — le cœur de calcul |
| Trajet > 2 h | les pauses, décidées sur une **valeur calculée** et non sur un argument reçu |
| Avion, neige | **jamais exécutés** — un rapport de couverture doit les signaler en rouge |

Deux appels de la même méthode parcourent donc des arbres d'appel différents. C'est
exactement ce qu'on demande aux outils de savoir montrer.

## Démarrer

```bash
mvn -q clean package
java -jar sample-app/target/sample-app.jar
```

Puis, pour un outil donné :

```bash
./tools/jacoco/collect.sh
```

## Où regarder

| | |
|---|---|
| **Résultats et arbitrages** | **[docs/RESULTS.md](docs/RESULTS.md)** — les solutions retenues et les questions à trancher |
| Tableau comparatif complet | [docs/COMPARISON.md](docs/COMPARISON.md) |
| Fiche par outil | [docs/tools/](docs/tools/) |
| Protocole et critères | [docs/METHODOLOGY.md](docs/METHODOLOGY.md) |
| Sorties réellement produites | [reports-demo/](reports-demo/) |

## Licence

MIT — voir [LICENSE](LICENSE).
