# APM SaaS — Datadog, New Relic, Dynatrace

> **Statut : ⛔ éliminés par la contrainte hors ligne.** Cette fiche existe pour que le
> rejet soit motivé, pas implicite.

## Ce que c'est
Des plateformes de supervision applicative hébergées : un agent instrumente l'application
et envoie les données vers un service distant, où tout est stocké et présenté.

## Sa philosophie
**L'observabilité comme service.** Rien à installer côté serveur, tout est fourni :
stockage, corrélation, alertes, tableaux de bord, rétention. Le modèle repose entièrement
sur la connexion sortante permanente vers l'éditeur.

## Pourquoi c'est éliminé ici
La contrainte **« environnement déconnecté d'Internet »** est incompatible avec le modèle
lui-même : sans lien sortant, l'agent n'a nulle part où écrire. Ce n'est pas une question
de configuration, c'est l'architecture du produit.

S'y ajoutent deux points, secondaires mais réels : le **coût à l'usage** est le plus élevé
du panel, et le besoin décrit est un **diagnostic ponctuel** — pas une supervision continue.

## L'équivalent utilisable
Si l'attrait est l'interface web soignée, la contrepartie auto-hébergeable existe :
**[Glowroot](glowroot.md)** (agent avec serveur embarqué) ou
**[OpenTelemetry](opentelemetry.md) + Jaeger/Tempo** (chaîne complète à monter).

## Licence et coût
Commerciales, facturation à l'usage.

## Ce qu'on peut en espérer
Rien dans ce contexte. La fiche est là pour que la question ne soit pas reposée dans six mois.

## Comparable à

- **[Glowroot](glowroot.md)** — **Le remplaçant recommandé** : même promesse, agent et interface auto-hébergés, donc utilisable sans réseau pendant l'exécution.
- **[OpenTelemetry](opentelemetry.md)** — La voie ouverte vers le même résultat, au prix d'un collecteur et d'un backend à installer.

## Facile / moins facile

**Ce qui est facile.** Tout, si l'on est connecté : c'est le principe même du modèle.

**Ce qui l'est moins.** **Rien n'est possible hors ligne.** Ce n'est pas une difficulté à surmonter, c'est une incompatibilité d'architecture.
