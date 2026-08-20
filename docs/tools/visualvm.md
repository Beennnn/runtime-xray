# VisualVM

> **Statut : 📄 sur documentation** — non exécuté ici (application de bureau, attachement manuel).

## Ce que c'est

L'outil de supervision et de profilage historique de l'écosystème Java, longtemps livré
avec le JDK, aujourd'hui distribué séparément. On le lance, il liste les JVM en cours, on
clique sur l'une d'elles.

## Sa philosophie

**Le point d'entrée universel.** Ne rien exiger : pas d'agent à configurer, pas de flag au
lancement, pas de licence. On ouvre, on voit ses JVM, on regarde. C'est le plus court
chemin entre « une application tourne » et « je vois quelque chose » — d'où sa popularité
persistante.

Sa limite est le revers de cette simplicité : il montre l'essentiel et s'arrête là.

## Ce qu'il sait faire

| Capacité | |
|---|---|
| Arbre d'appel | ✅ échantillonnage ou instrumentation |
| Mémoire, threads, GC | ✅ |
| Lignes exécutées | ❌ |
| Valeurs des paramètres | ❌ |

## Son interface

Application de bureau : onglets Monitor / Threads / Sampler / Profiler, arbre d'appel
triable. Confortable pour un développeur, **pas un livrable** qu'on transmet.

## Comment on navigue dedans

Uniquement dans son interface. Pas de page web, pas de sortie exploitable hors outil, pas
d'intégration plateforme. Un plugin IDE existe *(à confirmer sur les versions récentes)*.

## Mise en œuvre

⭐ **Très simple, mais manuelle** : lancer l'application (d'où l'intérêt du calibrage à
10 s et de `--hold-seconds`), lancer VisualVM, cliquer. Rien à scripter — donc rien à
rejouer automatiquement non plus.

## Licence et coût

Open source, **0 €** *(GPLv2 + Classpath Exception, à confirmer)*. Hors ligne.

## Ce qu'on peut en espérer

Un **premier coup d'œil** en trente secondes, sans rien préparer. Pour ce projet, il est
dominé par async-profiler sur l'arbre d'appel (qui produit en plus un fichier partageable)
et ne répond ni à la couverture ni aux paramètres. Utile comme outil d'appoint, pas comme
solution.
