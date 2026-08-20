# Fiches outil

Une page par outil : ce que c'est, sa philosophie, ce qu'il sait faire, à quoi ressemble
son interface, comment on navigue dedans, sa licence, et ce qu'on peut en espérer — seul
ou combiné.

La vue d'ensemble est dans [COMPARISON.md](../COMPARISON.md), la décision dans
[RESULTS.md](../RESULTS.md).

## SANS LICENCE — gratuit, auto-hébergeable

### Testés sur l'application-cible

| Outil | Ce qu'il apporte |
|---|---|
| [JaCoCo](jacoco.md) | ✅ Les lignes exécutées, avec code source annoté navigable hors IDE |
| [async-profiler](async-profiler.md) | ✅ L'arbre d'appel, en HTML autonome interactif |
| [JFR & Mission Control](jfr-jmc.md) | ✅ Le socle inclus dans le JDK — et l'écart mesuré entre Java 21 et 25 |

### Pistes pour les valeurs de paramètres — le trou du socle gratuit

| Outil | Ce qu'il apporte |
|---|---|
| [Arthas](arthas.md) | 🚧 Capture interactive des valeurs, console web — le candidat n° 1 |
| [JMC Agent](jmc-agent.md) | 📄 Capture déclarative, adossée à JFR déjà présent |
| [BTrace & Byteman](btrace-byteman.md) | 📄 Capture par script ou par règle, les plus exigeants |
| [IntelliJ IDEA](intellij.md) | 📄 Point d'arrêt non suspensif : la réponse gratuite immédiate, mais artisanale |

### Autres outils recensés

| Outil | Pourquoi il figure ici |
|---|---|
| [VisualVM](visualvm.md) | 📄 Le plus court chemin vers un premier coup d'œil |
| [OpenClover](openclover.md) | 📄 Couverture **par test**, si la question se pose |
| [Glowroot](glowroot.md) | 📄 Interface web soignée, auto-hébergeable donc hors ligne |
| [SonarQube](sonarqube.md) | 📄 Le meilleur afficheur de la couverture JaCoCo |
| [Kieker](kieker.md) | 📄 Le seul qui vise le « but final » : reconstruire l'architecture |
| [OpenTelemetry](opentelemetry.md) | 📄 Le standard, mais c'est une infrastructure |

## AVEC LICENCE — commercial

Prix relevés le 2026-08-20. Démarches d'obtention des clés d'essai et stockage hors dépôt :
**[EVALUATION-KEYS.md](../EVALUATION-KEYS.md)**.

| Outil | Ce qu'il apporte | Prix |
|---|---|---|
| [JProfiler](jprofiler.md) | *Method splitting by parameter values* — le candidat payant n° 1 | 549 $ perpétuel |
| [YourKit](yourkit.md) | Probes extensibles ; gratuit en open source, 99 $/an en académique | 549 $ perpétuel |
| [IntelliJ IDEA Ultimate](intellij.md) | Profiler intégré — qui embarque async-profiler, gratuit par ailleurs | abonnement |
| [APM SaaS](apm-saas.md) | ⛔ Éliminés par la contrainte hors ligne — rejet motivé | à l'usage |
