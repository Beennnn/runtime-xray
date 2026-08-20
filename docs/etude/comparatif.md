# Tableau comparatif

> Rempli à partir de deux sources distinctes, jamais mélangées : ce qui a été
> **exécuté sur ce poste** (et dont la sortie est versionnée dans `reports-demo/`),
> et ce qui provient de la **documentation publique** et reste à vérifier.
> Critères et protocole : [la méthode](methode.md).

## 0. Statut de vérification

| Statut | Sens |
|---|---|
| ✅ **Testé ici** | Exécuté sur `sample-app`, sortie versionnée |
| ⛔ **Bloqué par licence** | Exige une licence ou un essai commercial activé par un humain |
| 🚧 **Bloqué techniquement** | Gratuit, mais non exécutable dans cet environnement — raison indiquée |
| 📄 **Sur documentation** | Non exécuté ; fiche établie sur sources publiques, à confirmer |

**Sans licence**

| Outil | Statut | Preuve / raison |
|---|---|---|
| JaCoCo | ✅ Testé ici | [`reports-demo/generated/jacoco/html/`](../../reports-demo/generated/jacoco) |
| async-profiler | ✅ Testé ici | [`flamegraph.html`](../../reports-demo/generated/async-profiler/flamegraph.html) + [`tree.html`](../../reports-demo/generated/async-profiler/tree.html) |
| JFR (Flight Recorder) | ✅ Testé ici | [`recording.jfr`](../../reports-demo/generated/jfr) + `method-timing.txt` |
| JDK Mission Control | 📄 Sur documentation | GUI desktop : l'ouverture du `.jfr` produit ici n'a pas été faite |
| VisualVM | 📄 Sur documentation | GUI desktop, attachement manuel |
| IntelliJ IDEA | 📄 Sur documentation | Vérification = ouvrir l'IDE ; non automatisable ici |
| Arthas | ✅ Testé ici | [`reports-demo/generated/arthas/`](../../reports-demo/generated/arthas) — installé hors ligne depuis Maven Central, vérifié avec le trafic HTTP coupé |
| JMC Agent | 📄 Sur documentation | Non publié sur Maven Central sous une forme directement exécutable |
| BTrace / Byteman | 📄 Sur documentation | Non exécutés |
| OpenTelemetry Java agent | 📄 Sur documentation | Exige un collecteur + un backend de visualisation |
| Glowroot | 📄 Sur documentation | Exige un agent + son serveur embarqué |
| Kieker | 📄 Sur documentation | Non exécuté |
| OpenClover | 📄 Sur documentation | Non exécuté |

**Avec licence** — prix et démarches : [les clés d'évaluation](cles-evaluation.md)

| Outil | Statut | Preuve / raison |
|---|---|---|
| JProfiler | ⛔ Bloqué par licence | Essai 10 jours à activer manuellement |
| YourKit | ⛔ Bloqué par licence | Essai 15 jours à activer manuellement |
| XRebel (Perforce) | ⛔ Bloqué par licence | Licence commerciale |
| Dynatrace / Datadog / New Relic | ⛔ Bloqué par licence | SaaS, compte + agent |

## 1. Couverture fonctionnelle — les trois données du brief

Les trois colonnes reprennent mot pour mot la demande : lignes exécutées, arbre d'appel,
valeurs des paramètres.

| Outil | Lignes exécutées | Arbre d'appel | Valeurs des paramètres |
|---|---|---|---|
| **JaCoCo** ✅ | ✅ **oui**, ligne à ligne + branches | ❌ non | ❌ non |
| **async-profiler** ✅ | ❌ non (échantillonne, ne prouve rien sur le non-exécuté) | ✅ **oui**, complet et interactif | ❌ non |
| **JFR** ✅ | ⚠️ partiel — piles échantillonnées, pas une couverture | ✅ oui (piles + `jdk.MethodTrace` ciblé) | ❌ non — la signature `(List, int)` est affichée, **pas les valeurs** |
| **JMC** 📄 | ⚠️ idem JFR | ✅ oui, vue graphique du `.jfr` | ❌ non |
| **VisualVM** 📄 | ❌ non | ✅ oui (sampler / instrumentation) | ❌ non |
| **Arthas** ✅ | ❌ non | ✅ **oui, par appel, avec les numéros de ligne** | ✅ **oui** — les valeurs, pas les types |
| **BTrace** 📄 | ❌ non | ✅ oui | ✅ oui (scripts d'instrumentation) |
| **Byteman** 📄 | ❌ non | ⚠️ indirect | ✅ oui (règles d'injection) |
| **JMC Agent** 📄 | ❌ non | ✅ via JFR | ✅ oui — capture déclarative (XML) de paramètres et valeurs de retour dans des événements JFR |
| **OpenTelemetry** 📄 | ❌ non | ✅ oui (spans imbriqués) | ⚠️ seulement ce qu'on met en attribut de span |
| **Glowroot** 📄 | ❌ non | ✅ oui (traces par transaction) | ⚠️ limité, configurable |
| **Kieker** 📄 | ⚠️ traces d'exécution | ✅ oui — **conçu pour reconstruire l'architecture** à partir des traces | ✅ oui (probes paramétrables) |
| **OpenClover** 📄 | ✅ oui, + couverture **par test** | ❌ non | ❌ non |
| **JProfiler** ⛔ | ❌ non | ✅ oui, très riche | ✅ oui — *method splitting by parameter values* |
| **YourKit** ⛔ | ❌ non | ✅ oui | ✅ oui (*probes*, Open API) |

> **Le constat central se lit dans cette table** : *aucun outil ne couvre seul les trois
> colonnes.* La couverture de lignes et la capture de paramètres relèvent de familles
> techniques différentes — l'une instrumente tout le bytecode en permanence, l'autre
> greffe des sondes sur quelques méthodes ciblées. **Le résultat sera forcément une
> combinaison d'au moins deux outils**, et c'est cela qu'il faut arbitrer, pas « lequel
> gagne ».

## 2. Où se regarde le résultat — IDE, page web, plateforme

C'est l'axe décisif du brief : *voir par où le code est passé et naviguer dedans*, pour
un développeur **comme** pour un non-développeur.

| Outil | Page web autonome (hors IDE) | Dans l'IDE | Dans GitHub / GitLab | GUI desktop |
|---|---|---|---|---|
| **JaCoCo** ✅ | ✅ **oui — code source annoté, cliquable, ligne à ligne** ([capture](../assets/shots/jacoco-source-weather.png)) | ✅ IntelliJ, Eclipse, VS Code | ⚠️ **GitLab oui** (visualisation native dans la diff de MR, via un XML Cobertura converti) ; **GitHub non nativement** (exige Codecov / Coveralls / un commentaire de PR) | ❌ |
| **async-profiler** ✅ | ✅ **oui — flame graph + arbre d'appel HTML autonome**, zoom et recherche ([capture](../assets/shots/async-tree.png)) | ⚠️ via IntelliJ Ultimate (qui l'embarque) | ❌ | ❌ |
| **JFR** ✅ | ❌ — sortie binaire + texte CLI | ✅ IntelliJ Ultimate ouvre les `.jfr` | ❌ | ✅ via JMC |
| **JMC** 📄 | ❌ | ❌ | ❌ | ✅ application Eclipse RCP |
| **VisualVM** 📄 | ❌ | ⚠️ plugin IDE existant | ❌ | ✅ |
| **Arthas** 🚧 | ✅ console web (`http://<hôte>:8563`) | ❌ | ❌ | ❌ |
| **Glowroot** 📄 | ✅ **oui — interface web complète embarquée** | ❌ | ❌ | ❌ |
| **OpenTelemetry** 📄 | ✅ via Jaeger / Grafana Tempo | ❌ | ❌ | ❌ |
| **OpenClover** 📄 | ✅ rapport HTML avec source annotée | ⚠️ plugins historiques | ⚠️ comme JaCoCo | ❌ |
| **SonarQube** 📄 | ✅ **oui — source annotée + navigation, pensée pour non-développeurs** ; consomme le XML JaCoCo | ✅ SonarLint | ✅ décoration native des MR/PR | ❌ |
| **Codecov / Coveralls** 📄 | ✅ source annotée en ligne | ❌ | ✅ **oui — commentaire de PR + vue par ligne** | ❌ |
| **JProfiler** ⛔ | ⚠️ export HTML/CSV des snapshots | ✅ plugin IntelliJ natif | ❌ | ✅ |
| **YourKit** ⛔ | ⚠️ export multi-formats | ✅ plugin IDE | ❌ | ✅ |

### Ce que ça implique pour les deux publics

- **Développeur** → l'IDE gagne : IntelliJ affiche la couverture directement dans la marge
  de l'éditeur, et on saute d'un appel à l'autre avec les raccourcis habituels. Chemin le
  plus court : produire un `.exec` JaCoCo et le charger dans l'IDE.
- **Non-développeur** → la page web autonome gagne, et **JaCoCo est le seul outil testé
  ici qui la produise pour la couverture** : un dossier HTML, aucun serveur, aucun compte,
  cliquable de la vue d'ensemble jusqu'à la ligne coloriée. Publiable tel quel sur GitHub
  Pages, donc partageable par simple URL.
- **Revue de code (GitHub/GitLab)** → ce n'est **pas** un point fort de la couverture Java
  brute. GitLab sait le faire nativement mais exige une conversion au format Cobertura ;
  GitHub demande un service tiers. Aucun des deux ne montre un **arbre d'appel** — pour ça,
  la page web autonome reste le seul canal réaliste.

## 3. Facilité de mise en œuvre (critère n° 1)

| Outil | Mode d'intégration | Ce qu'il faut faire avant d'avoir un rapport | Verdict |
|---|---|---|---|
| **JFR** ✅ | Option JVM native | Un flag `-XX:StartFlightRecording` — **rien à installer**, c'est dans le JDK | ⭐ le plus simple |
| **JaCoCo** ✅ | Agent JVM (`-javaagent`) | Récupérer un jar, lancer avec l'agent, générer le rapport (3 commandes, scriptées ici) | ⭐ simple |
| **async-profiler** ✅ | Agent natif (`-agentpath`) | Rien à installer : le jar Maven embarque la bibliothèque native | ⭐ simple |
| **VisualVM** 📄 | Attachement au process | Lancer l'appli, ouvrir l'outil, cliquer sur le process | ⭐ simple, mais manuel |
| **JProfiler** ⛔ | Agent + GUI | Installer, licencier, attacher — annoncé « zéro configuration » | à vérifier en essai |
| **Arthas** 🚧 | Attachement au process | Un jar, puis des commandes interactives | simple si le réseau le permet |
| **OpenTelemetry** 📄 | Agent JVM | Agent **+ collecteur + backend** à déployer | ⚠️ lourd pour un diagnostic ponctuel |
| **Kieker** 📄 | Agent + configuration | Instrumentation à configurer, puis analyse hors ligne | ⚠️ le plus lourd — c'est un cadre de recherche |

> Rappel : c'est le critère n° 1. Un outil qui demande une demi-journée de configuration
> perd contre un flag JVM, même s'il en montre davantage.

## 4. Licence et coût (critère n° 5)

| Outil | Licence | Modèle | Coût indicatif |
|---|---|---|---|
| JaCoCo | EPL 2.0 | open source | 0 € |
| async-profiler | Apache 2.0 | open source | 0 € |
| JFR | GPLv2 + Classpath Exception (OpenJDK) | inclus dans le JDK | 0 € |
| JMC | UPL 1.0 *(à confirmer)* | open source | 0 € |
| VisualVM | GPLv2 + CE *(à confirmer)* | open source | 0 € |
| Arthas | Apache 2.0 | open source | 0 € |
| BTrace / Byteman | open source *(licences exactes à confirmer)* | open source | 0 € |
| Kieker | Apache 2.0 | open source / académique | 0 € |
| OpenClover | Apache 2.0 | open source | 0 € |
| Glowroot | Apache 2.0 | open source | 0 € |
| OpenTelemetry | Apache 2.0 | open source | 0 € + coût du backend |
| IntelliJ IDEA Community | Apache 2.0 | gratuit | 0 € — **mais le profiler intégré est réservé à Ultimate** |
| IntelliJ IDEA Ultimate | commerciale | abonnement | ordre de grandeur : quelques centaines €/an *(à vérifier sur la grille JetBrains)* |
| JProfiler | commerciale | licence perpétuelle + maintenance | ~500 $ / licence, ~200 $ académique *(chiffres du brief, à confirmer)* |
| YourKit | commerciale | licence | équivalent ; **gratuit pour l'open source**, tarif réduit académique *(à confirmer)* |
| XRebel | commerciale | abonnement | à chiffrer |
| Dynatrace / Datadog / New Relic | commerciale | SaaS à l'usage | le plus cher ; hors sujet pour un diagnostic ponctuel |

> ⚠️ **Aucun tarif de cette table n'a été relevé sur la page tarifaire du fournisseur au
> cours de cette session.** Ils viennent du brief ou de connaissances générales et doivent
> être confirmés avant toute décision d'achat.

## 5. Synthèse provisoire

Ce qui est **établi par l'exécution** (et non par lecture) :

1. **Pour « par où le code est passé », JaCoCo est déjà la réponse.** Il produit un site
   HTML où l'on descend de la vue d'ensemble jusqu'au source colorié ligne par ligne, sans
   IDE, sans serveur, sans compte. Sur `sample-app` : 91,1 % des instructions et 81,8 % des
   branches couvertes, avec `PlaneSpeed` à 0 % et la branche `SNOW` en rouge — exactement
   ce qu'un lecteur non technique doit pouvoir constater seul.
2. **Pour l'arbre d'appel, async-profiler donne le meilleur rapport effort/résultat gratuit** :
   un HTML autonome, dépliable et cherchable, obtenu avec un seul flag JVM.
3. **Pour les valeurs de paramètres, rien de gratuit n'a pu être démontré ici.** JFR affiche
   la signature, pas les valeurs — c'est vérifié, pas supposé. Les pistes gratuites
   (Arthas, JMC Agent, BTrace, Byteman) restent à tester ; c'est précisément le terrain où
   les commerciaux revendiquent leur valeur.

Ce qui **reste à trancher** — voir [la méthode](methode.md#6-ce-qui-reste-à-faire) :

- l'essai JProfiler / YourKit sur ce même `sample-app`, seul moyen de savoir si le payant
  se justifie **sur le seul point où le gratuit décroche** ;
- la piste gratuite de capture de paramètres la plus crédible (Arthas ou JMC Agent) ;
- la publication du rapport JaCoCo sur GitHub Pages, pour valider le canal « URL
  partagée à un non-développeur ».
