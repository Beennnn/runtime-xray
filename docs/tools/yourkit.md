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
Commerciale, ordre de grandeur équivalent à JProfiler *(à vérifier)*. **Licences gratuites
pour les projets open source** et tarifs réduits pour l'académique et la recherche
*(à confirmer)* — à signaler si le contexte s'y prête.

## Ce qu'on peut en espérer
Même rôle que JProfiler dans la décision. À tester dans le même mouvement, sur le même
`sample-app`, pour comparer sur pièces plutôt que sur plaquette.
