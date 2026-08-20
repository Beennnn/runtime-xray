# YourKit Java Profiler

> **Statut : ⛔ bloqué par licence** — essai de 15 jours à activer manuellement. Non testé ici.

## Ce que c'est
L'autre profileur Java commercial de référence, concurrent direct de JProfiler.

## Sa philosophie
**Rendre l'outil extensible.** Sa particularité sont les *probes* : des sondes que
l'utilisateur définit lui-même, via une API ouverte, pour capturer des données propres à
son application. Là où JProfiler propose un ensemble de vues fini et très abouti, YourKit
offre un mécanisme d'extension.

## Ce qu'il sait faire
Arbre d'appel ✅ · **valeurs des paramètres** ✅ via les probes · mémoire, verrous ✅ ·
lignes exécutées ❌ · export multi-formats ✅ (un point fort : ses instantanés se
transmettent plus facilement que la moyenne).

## Son interface
Application de bureau + plugin IDE.

## Comment on navigue dedans
Interface propre ✅ · IDE ✅ · exports variés ⚠️ · pas d'intégration plateforme.

## Mise en œuvre
Agent + interface. Les probes demandent un développement — ce qui déplace le curseur du
critère n° 1 selon qu'on se contente des sondes fournies ou qu'on en écrive.

## Licence et coût
Commerciale. Prix relevés le 2026-08-20 sur [la page tarifaire](https://www.yourkit.com/java/profiler/purchase/) :

| | Abonnement annuel | Perpétuel |
|---|---|---|
| 1 poste (Basic) | 449 $ | **549 $** |
| 1 poste (Advanced) | 579 $ | 713 $ |
| 5 postes (Basic) | 1 259 $ | 1 539 $ |
| Flottante 1 utilisateur (Basic) | 2 249 $ | 2 749 $ |

**Gratuit pour les projets open source** (en contrepartie d'un lien vers YourKit).
**Académique : 99 $/an** par poste, **999 $/an** pour un établissement entier — nettement
moins cher que JProfiler sur ce créneau.

⚠️ **Hors ligne** : les licences **flottantes** utilisent par défaut un **serveur de
licences dans le cloud**, incompatible avec la contrainte. Une option **auto-hébergée**
existe — c'est elle qu'il faut demander. Les licences *seat* n'ont pas ce problème.

Essai de 15 jours, téléchargement direct — voir
[EVALUATION-KEYS.md](../EVALUATION-KEYS.md#yourkit-java-profiler).

## Ce qu'on peut en espérer
Même rôle que JProfiler dans la décision. À tester dans le même mouvement, sur le même
`sample-app`, pour comparer sur pièces plutôt que sur plaquette.

## Facile / moins facile

**Ce qui est facile.** Démarrer : téléchargement direct, l'essai de 15 jours est intégré, pas de formulaire.

**Ce qui l'est moins.** **Les probes** — les sondes personnalisées, qui sont l'argument de l'outil pour capturer des valeurs, demandent du développement. Et en licence flottante, obtenir le serveur auto-hébergé plutôt que le cloud par défaut.
