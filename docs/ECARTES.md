# Outils écartés, et pourquoi

> Cette page existe pour que les rejets soient **motivés et datés**, et qu'on ne repose pas
> les mêmes questions dans six mois. Aucun de ces outils n'est mauvais : ils sont
> incompatibles avec *ces* contraintes.
>
> ⚠️ **Précision sur le critère hors ligne** : il porte sur l'**exécution**, pas sur
> l'installation. Télécharger un outil au préalable — depuis Internet ou un miroir interne —
> ne pose aucun problème. Est éliminatoire le besoin de réseau **pendant que le programme
> tourne**. C'est cette lecture qui est appliquée ci-dessous.

## Écartés par la contrainte hors ligne

### Datadog, New Relic, Dynatrace — ⛔ incompatibilité d'architecture

Leur modèle repose sur une connexion sortante **permanente, pendant l'exécution** :
l'agent instrumente, mais **n'affiche rien** — il envoie, en continu, vers l'éditeur. Ce
n'est donc pas un problème d'installation qu'on règlerait en téléchargant à l'avance :
sans lien sortant au moment où le programme tourne, l'agent n'a nulle part où écrire.
Ce n'est pas un réglage, c'est le produit.

**L'équivalent utilisable** : [Glowroot](tools/glowroot.md) (agent + serveur web embarqué,
auto-hébergeable) ou [OpenTelemetry](tools/opentelemetry.md) + Jaeger/Tempo.

### Codecov, Coveralls, SonarCloud — ⛔ même raison

Ce sont les afficheurs de couverture que GitHub utilise faute de support natif. Tous SaaS.
**L'équivalent utilisable** : le rapport HTML de JaCoCo (aucun service), ou
[SonarQube Community](tools/sonarqube.md) auto-hébergé si un portail permanent est voulu.

### La visualisation de couverture dans une pull request GitHub — ⛔ pas de solution native

GitHub ne sait pas afficher les lignes couvertes dans une diff sans service tiers, donc
sans connexion. **GitLab auto-hébergé, lui, le fait nativement** à partir d'un rapport
Cobertura — si la forge interne est un GitLab, ce canal redevient disponible.

## Écartés par les décisions prises

### SonarQube, Glowroot, OpenTelemetry — hors périmètre

Techniquement compatibles hors ligne (tous auto-hébergeables), mais ils supposent
**un serveur à installer et maintenir**. Les décisions n° 3 (un fichier qu'on envoie) et
n° 4 (pas d'historisation) rendent cette infrastructure inutile.

**À rouvrir si** le besoin passe du diagnostic ponctuel au suivi dans le temps.

### Le profiler d'IntelliJ IDEA Ultimate — hors chemin critique

On ne peut pas supposer que tout le monde dispose d'une licence Ultimate (décision n° 5).
Et le constat est confortable : son profiler **embarque async-profiler**, qu'on obtient
gratuitement en ligne de commande, avec une sortie *plus* partageable que celle de l'IDE.

### JProfiler, YourKit — repoussés, pas rejetés

La décision n° 1 les classe secondaires, et Arthas a comblé le manque qui les justifiait.
Ils gardent un intérêt précis : l'**agrégation de l'arbre d'appel par valeur de paramètre**.
Voir [EVALUATION-KEYS.md](EVALUATION-KEYS.md#ce-quon-attend-des-outils-commerciaux--et-pourquoi).

## Écartés parce qu'un autre outil fait mieux, pour le même prix

| Outil | Pourquoi il n'est pas retenu |
|---|---|
| [VisualVM](tools/visualvm.md) | Dominé par async-profiler sur l'arbre d'appel, et rien n'en sort : ni fichier à transmettre, ni script à rejouer |
| [OpenClover](tools/openclover.md) | Répond à « quel test couvre quoi ? », pas à « qu'a fait cette exécution ? ». Instrumentation à la compilation, donc plus intrusif que JaCoCo |
| [BTrace, Byteman](tools/btrace-byteman.md) | Font ce qu'Arthas fait, mais en demandant d'écrire un script ou une règle par question |
| [JMC Agent](tools/jmc-agent.md) | Bonne idée — capture déclarative adossée à JFR — mais pas de binaire prêt à l'emploi trouvé, là où Arthas fonctionne en deux commandes |

## Reporté, pas écarté

### [Kieker](tools/kieker.md) — le bon outil pour la question suivante

Le seul qui vise le **but final** du brief : reconstruire une architecture à partir de
l'exécution. Mais c'est un projet en soi, et l'ouvrir avant d'avoir la première mesure
inverserait l'ordre raisonnable.

⚠️ À revérifier si le portage vers Java 25 est retenu : la version 2.0.3 annonce le
support **jusqu'à Java 23**.
