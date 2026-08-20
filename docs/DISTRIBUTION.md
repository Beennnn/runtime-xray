# Diffuser l'outil — faut-il le publier sur Maven Central ?

> Question posée : *pourrait-on publier l'outil sous forme de jar sur Maven Central ?
> Est-ce pertinent ?*

**Réponse courte : pas en l'état, mais l'idée vise juste — et elle indique la bonne V2.**

## Ce que l'outil est aujourd'hui

Un script shell (`runtime-xray.sh`), un script Python (`build-dashboard.py`) et un gabarit
HTML. **Il n'y a pas une ligne de Java dedans** : l'outil orchestre des agents externes,
il ne s'exécute pas dans la JVM analysée.

Publier ça comme artefact Maven serait un abus de forme : Maven Central sert à distribuer
des bibliothèques consommées par un build. Personne ne mettra cet outil en `<dependency>`.

## Pourquoi l'idée est quand même bonne

Il y a un argument sérieux, et c'est **la contrainte hors ligne**.

Dans un environnement déconnecté, le **miroir Maven interne est souvent le seul canal
ouvert**. C'est d'ailleurs déjà ainsi que l'outil récupère ses composants : JaCoCo et
Arthas viennent de Maven Central. Publier l'outil au même endroit le rendrait installable
par le mécanisme que l'entreprise a déjà autorisé :

```bash
mvn dependency:copy -Dartifact=io.github.beennnn:runtime-xray:1.0.0:zip:bin
```

Autrement dit, l'intérêt n'est pas « être une bibliothèque », c'est **passer par le tuyau
qui existe déjà**.

## Les trois voies, comparées

| Voie | Effort | Ce que ça donne | Quand la choisir |
|---|---|---|---|
| **Release GitHub** *(en place)* | nul | Un zip téléchargeable | Aujourd'hui — c'est suffisant |
| **Nexus / Artifactory interne** | faible | Le même zip, servi par le miroir de l'entreprise | Dès que plusieurs équipes derrière le pare-feu l'utilisent. **Ne demande pas Maven Central** |
| **Maven Central** | réel | Distribution publique par un canal déjà ouvert partout | Si l'outil sort de l'entreprise |

⚠️ Le point souvent manqué : **pour un usage interne, Maven Central est inutile.** Déposer
le zip sur le Nexus de l'entreprise donne exactement le même bénéfice, sans compte, sans
signature, sans processus de publication.

## Ce que Maven Central exige, si on y va

- un **espace de noms** qu'on prouve posséder — `io.github.<compte>` s'obtient en prouvant
  le compte GitHub, c'est le chemin le plus court ;
- des artefacts **signés GPG**, accompagnés des jars `sources` et `javadoc` ;
- une publication par le **Central Portal**, avec des règles de validation strictes ;
- des versions **immuables** : rien ne se corrige après coup, seulement une version de plus.

C'est une mise en place ponctuelle, ensuite automatisable. Mais c'est du travail réel pour
un bénéfice nul tant que la diffusion reste interne.

## La vraie question que ça soulève : réécrire l'outil en Java ?

Si l'outil devait être distribué comme un jar, autant qu'il **en soit un**. Aujourd'hui il
dépend de bash **et** de Python 3 — deux prérequis de plus sur la machine analysée, alors
que **Java y est forcément présent**.

Un orchestrateur écrit en Java donnerait :

- **un seul fichier**, `java -jar runtime-xray.jar --config mon-projet.conf` ;
- **aucune dépendance** hors du JDK — plus de bash, plus de Python ;
- la **portabilité Windows** gratuite, aujourd'hui absente ;
- une distribution naturelle par Maven, interne ou publique ;
- accessoirement, la lecture du XML JaCoCo et l'assemblage de la page se font aussi bien
  en Java qu'en Python.

**C'est la bonne V2 si l'outil doit vivre au-delà de ce dépôt.** Le coût est modéré — la
logique est déjà écrite et éprouvée, il s'agit de la transposer, pas de la concevoir.

## Recommandation

1. **Maintenant** : rien à faire. La release GitHub suffit, et un dépôt interne suffit pour
   l'entreprise.
2. **Si plusieurs équipes l'adoptent** : déposer le zip sur le Nexus interne — une heure de
   travail, aucun compte à créer.
3. **Si l'outil doit sortir** : le réécrire en Java d'abord, le publier ensuite. Publier un
   zip de scripts sur Maven Central serait un habillage, pas une distribution.
