# Kieker

> **Statut : 📄 sur documentation** — non exécuté ici.
> Version 2.0.3 (avril 2026). Licence Apache 2.0. **0 €.**

## Ce que c'est

Un cadre de **surveillance et de reconstruction d'architecture**, issu de la recherche
(université de Kiel). Il n'appartient pas à la même famille que les autres outils du panel :
là où un profiler répond « où passe le temps ? » et un outil de couverture « par où
est-on passé ? », Kieker répond **« à quoi ressemble ce logiciel, vu de son exécution ? »**.

C'est le seul outil recensé dont l'objectif déclaré coïncide avec le **but final du
brief** : *analyser, restructurer et redéfinir le code à partir de ces données*.

## Sa philosophie

**La trace est un matériau, pas un rapport.** Les autres outils présentent ce qu'ils ont
mesuré et s'arrêtent là. Kieker sépare explicitement deux moments :

1. **la collecte** — des sondes écrivent un flux d'enregistrements typés (entrée de
   méthode, sortie, thread, horodatage) vers un fichier, une file de messages ou le réseau ;
2. **l'analyse** — une chaîne de traitement relit ce flux et en *dérive* quelque chose :
   un graphe de dépendances entre composants, un diagramme de séquence, un arbre d'appel
   agrégé, une vue de déploiement.

Cette séparation est sa force : la même collecte peut alimenter plusieurs analyses, et on
peut rejouer une analyse des mois plus tard sur des traces archivées. C'est aussi sa
lourdeur : rien n'est prêt à regarder tant qu'on n'a pas monté la seconde moitié.

## Comment il s'instrumente

Trois voies, toutes **sans modifier le code source** :

- **AspectJ** (1.9.25.1 dans la 2.0.3) — tissage à la volée, le mode historique ;
- **ByteBuddy** (1.18.8) — réécriture de bytecode, le mode moderne ;
- une API de sondes à écrire soi-même quand il faut capturer des données métier précises.

C'est par cette dernière voie qu'on obtient les **valeurs des paramètres** : elles ne sont
pas capturées par défaut, il faut les déclarer.

## Ce qu'il produit — et pourquoi c'est différent

| Sortie | Ce que les autres outils n'ont pas |
|---|---|
| **Graphe de dépendances** entre classes, packages, composants | Une vue **structurelle**, pas temporelle : qui dépend de qui, tel que l'exécution l'a révélé — et non tel que les `import` le déclarent |
| **Diagrammes de séquence** reconstruits | L'enchaînement réel des appels, sous la forme qu'un architecte lit |
| **Vue de déploiement** | Quelle partie tourne où, sur un système distribué |
| **Traces archivables et rejouables** | Les autres outils produisent un rapport figé ; Kieker produit une donnée qu'on ré-analyse |

C'est là toute la différence avec le socle retenu : JaCoCo, async-profiler et Arthas
répondent à des questions **ponctuelles** sur un morceau de code. Kieker vise une
**représentation du système entier**, sur laquelle on décide un découpage.

## Ce qu'on peut en espérer pour le « but final »

Si l'objectif est réellement de **restructurer** — extraire des modules, redécouper des
responsabilités, casser des dépendances — alors le graphe de dépendances *observé* vaut
beaucoup plus qu'une analyse statique : il distingue les dépendances **réellement
exercées** de celles qui existent seulement dans les `import`. Ce n'est pas anecdotique :
c'est précisément l'écart qui fait échouer les refontes décidées sur plan.

Concrètement, sur un code à reprendre, il permettrait de répondre à : *quelles classes
travaillent toujours ensemble ?*, *quel sous-ensemble pourrait sortir en module autonome
sans casser le reste ?*, *quelles dépendances déclarées ne servent jamais ?*

## Ce qu'il en coûte

⚠️ **C'est le plus lourd du panel, et de loin.**

- Il faut monter la **chaîne d'analyse** en plus de la collecte — les diagrammes ne
  tombent pas d'un flag JVM.
- Le vocabulaire est celui de la recherche (« records », « filters », « analysis
  pipelines ») : il y a une marche à franchir avant le premier résultat.
- Le volume de traces est du même ordre que celui constaté avec JFR : sur un chemin chaud,
  ça se compte en centaines de mégaoctets. La sélection des points d'instrumentation n'est
  pas optionnelle.

⚠️ **Compatibilité Java** : la version 2.0.3 vise **Java 17 par défaut et annonce le
support jusqu'à Java 23**. Java 21 est donc couvert — mais **Java 25 ne l'est pas encore**.
Si le portage vers 25 est décidé, ce point est à revérifier avant de compter sur Kieker.

## Comment on navigue dedans

Pas de navigation dans le code : Kieker produit des **diagrammes**, qu'on regarde. Il ne
remplace donc pas le rapport de couverture pour la question « montre-moi cette ligne » — il
répond à une autre échelle.

## Verdict pour ce projet

**À considérer en second temps, comme un projet en soi.** Le socle retenu (JaCoCo +
async-profiler + Arthas) répond au besoin décrit — savoir par où le code passe, voir
l'arbre d'appel, lire les valeurs. Kieker répond à la question *suivante* : que faire de
ce code une fois qu'on l'a compris.

L'ouvrir maintenant reviendrait à monter une infrastructure d'analyse avant d'avoir la
première mesure. L'ordre raisonnable est l'inverse.

## Facile / moins facile

**Ce qui est facile.** Rien, honnêtement — et c'est le prix de ce qu'il vise. Aucun autre
outil ne reconstruit une architecture à partir de l'exécution.

**Ce qui l'est moins.** Tout : instrumentation à configurer, chaîne d'analyse à monter,
volume de traces à maîtriser, vocabulaire à apprendre. Et une compatibilité Java à
revérifier si le portage vers 25 est retenu.
