<p align="center">
  <img src="docs/assets/banner.svg" alt="Runtime X-Ray — obtenir des rapports par analyse dynamique de code Java" width="100%">
</p>

# Runtime X-Ray — obtenir des rapports par analyse dynamique de code Java

**Trouver la solution qui permet de produire, à l'exécution d'un programme Java, des
rapports exploitables montrant par où le code est passé.**

Il ne s'agit pas de départager des produits pour le plaisir de les classer, mais de
répondre à un besoin précis : disposer de rapports qui rendent visible le comportement
réel du code — pour l'analyser, puis le restructurer.

**→ [Résultats et arbitrages à trancher](docs/RESULTS.md)** · [Tableau comparatif](docs/COMPARISON.md) · [Fiches par outil](docs/tools/) · [Détails techniques](docs/TECHNICAL.md)

## Ce qu'on veut obtenir

| Donnée recherchée | À quoi ça sert |
|---|---|
| **Les lignes de code exécutées** | Savoir ce qui a réellement tourné — et surtout ce qui n'a **jamais** tourné |
| **L'arbre d'appel** | Comprendre qui appelle quoi, et dans quel ordre |
| **Les valeurs passées en paramètres** | Savoir **pourquoi** telle branche a été prise plutôt qu'une autre |

Et, transversalement : pouvoir **naviguer dans le code** depuis le rapport — pour un
développeur dans son IDE, pour un non-technicien dans une page web qu'on lui envoie.

## Contraintes

| Contrainte | Conséquence sur le choix |
|---|---|
| 🔌 **Environnement déconnecté d'Internet** | Élimine tout SaaS (Datadog, New Relic, Codecov, SonarCloud) et tout outil qui télécharge ses composants au lancement |
| ☕ **Java 21 aujourd'hui, Java 25 à évaluer** | Les deux plateformes sont évaluées. L'écart est mesuré et il est important : le traçage de méthodes de JFR n'existe qu'à partir de Java 25 — [pourquoi, et ce que ça change](docs/RESULTS.md#gains-dun-portage-vers-java-25) |
| 🧭 **IntelliJ comme IDE cible** | L'intégration IDE se juge sur IntelliJ ; à défaut, une page web navigable fait office d'équivalent |
| 👥 **Deux publics** | Un développeur lit dans son IDE ; un non-technicien ouvre une page seul, sans installation ni compte |
| 🎯 **Diagnostic ponctuel** | Déployer un collecteur et un serveur pour répondre à une question est disproportionné |

## Critères d'évaluation, par ordre de priorité

1. **Facilité de mise en œuvre** — lancer l'outil sans configuration lourde.
2. **Lisibilité des rapports** — exploitables par des utilisateurs non techniques.
3. **Couverture fonctionnelle** — lignes, arbre d'appel, valeurs des paramètres.
4. **Intégration IDE** — IntelliJ, ou une page web de code annoté navigable hors IDE.
5. **Coût** — gratuit vs commercial, et si le payant se justifie.

> **Hors critères** : le temps de calcul et la RAM sont mesurés à titre de bonus, mais
> n'entrent pas dans la décision.

## Les outils comparés

Dix-huit outils recensés, regroupés par ce qu'ils savent faire. Le détail — features,
philosophie, licence, interfaces — est dans les [fiches individuelles](docs/tools/).

| Outil | Lignes exécutées | Arbre d'appel | Valeurs des paramètres | Rapport lisible hors IDE | Coût | Statut |
|---|:--:|:--:|:--:|---|---|---|
| **[JaCoCo](docs/tools/jacoco.md)** | ✅ | ❌ | ❌ | ✅ site HTML, code annoté | 0 € | ✅ testé |
| **[async-profiler](docs/tools/async-profiler.md)** | ❌ | ✅ | ❌ | ✅ HTML autonome interactif | 0 € | ✅ testé |
| **[JFR / Mission Control](docs/tools/jfr-jmc.md)** | ⚠️ | ✅ | ❌ | ❌ GUI desktop | 0 € (dans le JDK) | ✅ testé |
| **[VisualVM](docs/tools/visualvm.md)** | ❌ | ✅ | ❌ | ❌ GUI desktop | 0 € | 📄 |
| **[Arthas](docs/tools/arthas.md)** | ❌ | ✅ | ✅ | ✅ console web | 0 € | 🚧 |
| **[JMC Agent](docs/tools/jmc-agent.md)** | ❌ | ✅ | ✅ | ❌ via JMC | 0 € | 📄 |
| **[BTrace / Byteman](docs/tools/btrace-byteman.md)** | ❌ | ✅ | ✅ | ❌ sortie texte | 0 € | 📄 |
| **[OpenClover](docs/tools/openclover.md)** | ✅ | ❌ | ❌ | ✅ HTML, couverture par test | 0 € | 📄 |
| **[Glowroot](docs/tools/glowroot.md)** | ❌ | ✅ | ⚠️ | ✅ interface web embarquée | 0 € | 📄 |
| **[Kieker](docs/tools/kieker.md)** | ⚠️ | ✅ | ✅ | ⚠️ diagrammes générés | 0 € | 📄 |
| **[OpenTelemetry](docs/tools/opentelemetry.md)** | ❌ | ✅ | ⚠️ | ✅ via Jaeger / Tempo | 0 € + backend | 📄 |
| **[SonarQube](docs/tools/sonarqube.md)** | ✅ | ❌ | ❌ | ✅ web, pensé non-développeur | 0 € (Community) | 📄 |
| **[IntelliJ IDEA](docs/tools/intellij.md)** | ✅ | ✅ (Ultimate) | ⚠️ | ❌ dans l'IDE | 0 € / abonnement | 📄 |
| **[JProfiler](docs/tools/jprofiler.md)** | ❌ | ✅ | ✅ | ⚠️ export | ~500 $ | ⛔ licence |
| **[YourKit](docs/tools/yourkit.md)** | ❌ | ✅ | ✅ | ⚠️ export | ~500 $ | ⛔ licence |
| **[APM SaaS](docs/tools/apm-saas.md)** | ❌ | ✅ | ⚠️ | ✅ web | à l'usage | ⛔ hors ligne |

Légende — ✅ testé ici · 🚧 bloqué techniquement · ⛔ bloqué par licence ou contrainte · 📄 sur documentation.

> **Le constat qui structure la décision** : aucune ligne n'a trois ✅ dans les trois
> premières colonnes. **La solution sera une combinaison d'au moins deux outils.**

## À quoi ressemblent les rapports

### Voir par où le code est passé, et naviguer dedans

JaCoCo produit un site HTML où l'on descend de la vue d'ensemble jusqu'au **code source
colorié ligne par ligne**. Aucun serveur, aucun compte : un dossier qu'on ouvre ou qu'on
envoie.

![Code source annoté par JaCoCo](docs/assets/shots/jacoco-source-weather.png)

*Vert : exécuté. Rouge : jamais atteint. Losange jaune : condition partiellement couverte.
Un lecteur non technique voit immédiatement ce qui n'a pas tourné.*

La navigation se fait par le fil d'Ariane en haut — projet › package › classe › source —
et depuis la vue d'ensemble, triable par colonne :

![Vue d'ensemble JaCoCo](docs/assets/shots/jacoco-index.png)

### Voir l'arbre d'appel

async-profiler produit un **fichier HTML unique**, dépliable et cherchable, avec le
pourcentage de temps passé dans chaque branche :

![Arbre d'appel async-profiler](docs/assets/shots/async-tree.png)

Et la même donnée en flame graph, où la largeur d'une barre est le temps passé :

![Flame graph async-profiler](docs/assets/shots/async-flamegraph.png)

Toutes ces sorties sont versionnées dans [`reports-demo/generated/`](reports-demo/) :
elles s'ouvrent directement, sans rien réexécuter.

## Méthode

On ne compare que ce qu'on a **fait tourner**. Tous les outils analysent le même
programme, dans le même scénario déterministe, **sous Java 21 puis sous Java 25**, et
déposent leur sortie réelle dans le dépôt. Vérifié au passage : la couverture est
**identique** sur les deux versions ; c'est JFR, et lui seul, qui change. Chaque affirmation du tableau porte un statut : *testé ici* ou
*sur documentation* — une lecture de documentation n'est jamais présentée comme une mesure.

Le détail — programme analysé, scripts de collecte, mode d'intégration de chaque outil —
est dans **[docs/TECHNICAL.md](docs/TECHNICAL.md)**.

## Licence

MIT — voir [LICENSE](LICENSE).
