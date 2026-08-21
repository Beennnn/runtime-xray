# OpenTelemetry (agent Java)

> **Statut : 📄 sur documentation** — non exécuté ici (exige un collecteur et un backend).

## Ce que c'est
Le standard d'instrumentation ouvert. Son agent Java instrumente automatiquement les
bibliothèques connues et émet des **traces** : des arbres de *spans* imbriqués.

## Sa philosophie
**Découpler la production des données de leur exploitation.** L'agent ne sait rien afficher :
il émet vers un collecteur, qui alimente le backend de son choix (Jaeger, Grafana Tempo,
Zipkin). C'est une force pour un système d'information durable, et un **handicap pour un
diagnostic ponctuel** : il faut avoir monté toute la chaîne avant de voir la première trace.

## Ce qu'il sait faire
Arbre d'appel entre composants ✅ · valeurs ⚠️ uniquement ce qu'on place en attribut de span ·
lignes exécutées ❌.

⚠️ Nuance importante : l'instrumentation automatique opère **aux frontières** (HTTP, JDBC,
messagerie), pas méthode par méthode. Pour voir l'intérieur d'un calcul, il faut instrumenter
à la main.

## Comment on navigue dedans
Via le backend : Jaeger ou Tempo, en web ✅. Rien dans l'IDE, rien dans GitHub/GitLab.

## Mise en œuvre
⚠️ Un agent **+ un collecteur + un backend**. Faisable hors ligne (tout est auto-hébergeable),
mais c'est une infrastructure, pas un outil.

## Licence et coût
**Apache 2.0**, **0 €** — plus le coût d'exploitation du backend.

## Ce qu'on peut en espérer
Hors sujet pour le besoin décrit. Deviendrait pertinent si la question se déplaçait vers la
supervision continue d'un système distribué.

## Comparable à

- **[Glowroot](glowroot.md)** — La version « clé en main » de la même idée : agent plus interface, sans infrastructure à monter.
- **[APM SaaS](apm-saas.md)** — Ils consomment justement de l'OTel. La différence est où atterrissent les données.
- **[Kieker](kieker.md)** — Même matière première — des traces — pour une finalité différente : superviser contre reconstruire.

## Facile / moins facile

**Ce qui est facile.** Instrumenter les frontières (HTTP, base de données) : l'agent le fait tout seul.

**Ce qui l'est moins.** **Voir l'intérieur d'un calcul** — il faut instrumenter à la main. Et monter la chaîne complète collecteur + backend avant de voir la première trace.
