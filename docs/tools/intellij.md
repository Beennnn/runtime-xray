# IntelliJ IDEA

> **Statut : 📄 sur documentation** — la vérification suppose d'ouvrir l'IDE ;
> non automatisable ici. IntelliJ IDEA **Community** est installé sur ce poste.

## Ce que c'est

L'IDE cible du projet. Il compte ici à deux titres : il **affiche** des données produites
par d'autres outils (couverture), et il **embarque** son propre profiler dans l'édition
Ultimate.

## Sa philosophie

**Ramener la mesure là où on écrit le code.** Un rapport dans un navigateur oblige à faire
l'aller-retour mentalement ; une marge colorée dans l'éditeur met l'information sur la
ligne concernée, à portée de tous les raccourcis de navigation habituels (aller à la
déclaration, chercher les usages, remonter la hiérarchie d'appels).

## Ce qu'il sait faire

| Capacité | Community | Ultimate |
|---|---|---|
| Couverture affichée dans l'éditeur | ✅ | ✅ |
| Import d'un `.exec` JaCoCo externe | ✅ *(à confirmer)* | ✅ |
| Profiler intégré (arbre d'appel, flame graph) | ❌ | ✅ — **embarque async-profiler** |
| Lecture d'enregistrements `.jfr` | ❌ | ✅ |
| Valeurs des paramètres | ⚠️ via le **débogueur** : point d'arrêt non suspensif qui journalise une expression évaluée | idem |

> La dernière ligne mérite attention : un point d'arrêt non bloquant qui imprime
> `trip.mode()` à chaque passage capture des valeurs de paramètres **sans aucun outil
> supplémentaire, gratuitement, dans l'IDE**. C'est artisanal et limité à une session de
> débogage — mais c'est la réponse la plus immédiate au troisième besoin pour un développeur.

## Comment on navigue dedans

C'est **le** point fort : la couverture s'affiche dans la marge, on saute d'un appel à
l'autre, on remonte la hiérarchie. Aucun autre canal n'offre cette fluidité **à un
développeur**. En contrepartie : rien de tout cela n'est transmissible à un non-technicien
— il faudrait qu'il installe l'IDE et ouvre le projet.

## Mise en œuvre

Charger un rapport de couverture existant : *Run › Show Coverage Data…*, puis désigner le
`.exec` produit par [JaCoCo](jacoco.md). **À vérifier sur poste** — non testé ici.

## Licence et coût

**Community** : Apache 2.0, gratuite — mais **sans le profiler intégré**.
**Ultimate** : abonnement commercial *(tarif à vérifier sur la grille JetBrains)*.

## Ce qu'on peut en espérer

Pour le **critère n° 4**, IntelliJ est la réponse côté développeur, et l'édition Community
suffit pour la couverture. L'abonnement Ultimate n'ajoute, pour ce besoin précis, que le
confort d'avoir async-profiler dans l'IDE plutôt qu'en ligne de commande — le moteur étant
le même et gratuit par ailleurs.
