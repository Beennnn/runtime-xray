# Clés d'évaluation — comment les obtenir, où les ranger

> Cette page décrit les démarches à faire pour intégrer les outils sous licence à l'étude.
> **Ce sont des actions humaines** : elles demandent une adresse professionnelle, parfois
> une validation par l'éditeur. Aucune ne peut être automatisée depuis ce dépôt.

## Règle absolue : aucune clé dans le dépôt

Le dépôt est **public**. Une clé de licence commise reste dans l'historique git même après
suppression du fichier, et les scanners automatiques la trouvent en quelques minutes.
Trois protections, cumulées :

1. **Les clés vivent hors du dépôt**, dans
   `~/gdrive/claude/projects/pro/artifacts/runtime-xray-licences/` — hors de `~/dev`, donc
   hors de toute arborescence git, et répliqué automatiquement (Drive → NAS).
2. **Les scripts les lisent par variable d'environnement**, jamais depuis un fichier du dépôt :

   ```bash
   # ~/gdrive/claude/projects/pro/artifacts/runtime-xray-licences/licences.sh
   export JPROFILER_LICENSE_KEY="..."
   export JPROFILER_LICENSE_NAME="..."
   export YOURKIT_LICENSE_KEY="..."
   ```

   ```bash
   source ~/gdrive/claude/projects/pro/artifacts/runtime-xray-licences/licences.sh
   ./tools/jprofiler/collect.sh
   ```

3. **Le `.gitignore` bloque les formes usuelles** (`*.license`, `licence*.sh`, `.env.local`)
   — filet de sécurité, pas ligne de défense principale : la vraie protection est que le
   fichier ne soit pas dans l'arborescence.

> Alternative si l'on préfère ne rien laisser en clair sur le disque : le trousseau macOS.
> `security add-generic-password -s runtime-xray -a jprofiler -w '<clé>'` pour déposer,
> `security find-generic-password -s runtime-xray -a jprofiler -w` pour relire dans un script.

## JProfiler (ej-technologies)

| | |
|---|---|
| **Démarche** | Formulaire en ligne : [ej-technologies.com/jprofiler/trial](https://www.ej-technologies.com/jprofiler/trial) |
| **Informations demandées** | Nom, société, adresse e-mail. La clé arrive **par e-mail** — l'adresse doit être exacte |
| **Durée** | 10 jours *(à confirmer sur le formulaire au moment de la demande)* |
| **Données conservées par l'éditeur** | Nom, société, e-mail et IP anonymisée ; un e-mail de relance est envoyé pendant l'évaluation |
| **Téléchargement** | [ej-technologies.com/download/jprofiler](https://www.ej-technologies.com/download/jprofiler/files) |
| **Licence open source** | Gratuite pour les projets open source — à demander à l'éditeur |
| **⚠️ Hors ligne** | À vérifier au moment de la demande : la saisie de la clé est locale, mais **il faut confirmer qu'aucune activation en ligne n'est exigée** avant de compter sur l'outil en environnement déconnecté |

**Prix (relevés le 2026-08-20 sur la boutique de l'éditeur)** — licences perpétuelles :

| | Sans support | Avec 1 an de support et mises à jour |
|---|---|---|
| Licence simple | **549 $** | 768 $ |
| Licence flottante (1 utilisateur simultané) | **2 199 $** | 3 078 $ |

## YourKit Java Profiler

| | |
|---|---|
| **Démarche** | Téléchargement direct : [yourkit.com/download](https://www.yourkit.com/download/) — l'essai est intégré |
| **Durée** | **15 jours** |
| **Licence open source** | **Gratuite**, en contrepartie d'un lien vers YourKit sur les pages du projet. À demander au service commercial |
| **Académique / scientifique** | Abonnement à **99 $/an** par poste, **999 $/an** pour un établissement entier |
| **⚠️ Hors ligne** | Les licences **flottantes** s'appuient par défaut sur un **serveur de licences dans le cloud** — incompatible avec la contrainte. Une option **auto-hébergée** existe : c'est elle qu'il faut demander. Les licences *seat* ne posent pas ce problème |

**Prix (relevés le 2026-08-20 sur [la page tarifaire](https://www.yourkit.com/java/profiler/purchase/))** :

| | Abonnement annuel | Perpétuel |
|---|---|---|
| 1 poste (Basic) | 449 $ | **549 $** |
| 1 poste (Advanced) | 579 $ | 713 $ |
| 5 postes (Basic) | 1 259 $ | 1 539 $ |
| Flottante 1 utilisateur (Basic) | 2 249 $ | 2 749 $ |
| Flottante 5 utilisateurs (Basic) | 2 699 $ | 3 299 $ |

## IntelliJ IDEA Ultimate (JetBrains)

| | |
|---|---|
| **Démarche** | Essai de 30 jours depuis la Toolbox ou le site JetBrains |
| **Licence gratuite** | Programmes existants pour l'open source, l'enseignement et les étudiants |
| **⚠️ Hors ligne** | L'activation JetBrains passe normalement par un compte en ligne ; un **serveur de licences auto-hébergé** existe pour les licences organisationnelles. À vérifier selon le cadre |
| **Utilité pour l'étude** | Limitée : le profiler intégré embarque async-profiler, déjà disponible gratuitement en ligne de commande |

## Ce qu'il faut mesurer une fois les clés obtenues

Pour que l'essai serve à décider et pas seulement à regarder, rejouer **exactement** le
protocole des outils gratuits — même `sample-app`, même exécution de 10 s, sous Java 21
puis Java 25 :

1. **La capture des valeurs de paramètres** — c'est le seul point qui justifie la dépense.
   Vérifier concrètement que le *method splitting by parameter values* de JProfiler sépare
   bien les appels de `RoutePlanner.travelTimeMinutes` selon le mode de transport passé.
2. **Le surcoût réel** — comparer le temps affiché par `Metrics` avec et sans l'outil.
   Les 10 s de référence donnent une base de comparaison immédiate.
3. **Ce qui sort de l'outil** — un instantané exportable et lisible par un non-développeur,
   ou bien une interface qu'il faut installer pour consulter ? C'est le **critère n° 2**.
4. **L'activation hors ligne** — la question qui peut tout disqualifier. À traiter en
   premier, avant même d'installer.

Consigner les résultats dans [COMPARISON.md](COMPARISON.md) en faisant passer la ligne de
⛔ à ✅, et déposer les sorties dans `reports-demo/generated/<outil>/`.
