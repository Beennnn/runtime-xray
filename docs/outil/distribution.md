# Diffuser l'outil — faut-il le publier sur Maven Central ?

> Question posée : *pourrait-on publier l'outil sous forme de jar sur Maven Central ?
> Est-ce pertinent ?*

**Réponse courte : ce n'était pas possible quand la question a été posée ; ça l'est
devenu.**

## Ce que l'outil est aujourd'hui

**Un seul jar**, `runtime-xray.jar`, sans aucune dépendance hors du JDK :
`java -jar runtime-xray.jar --config mon-projet.conf`. Ni bash, ni Python.

Ça n'a pas toujours été le cas — et c'est précisément cette question qui a déclenché la
réécriture. La version d'origine était un script shell plus un script Python : deux
prérequis de plus sur une machine où **Java est forcément présent**, et aucune portabilité
Windows. L'argument « si on le distribue comme un jar, autant qu'il en soit un » a tenu.

Reste que publier ce jar sur Maven Central serait toujours un abus de forme sur un point :
Maven Central sert à distribuer des bibliothèques consommées par un build, et personne ne
mettra cet outil en `<dependency>`. L'intérêt est ailleurs — voir juste en dessous.

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

## Ce que la réécriture en Java a effectivement apporté

- **un seul fichier** à transporter, au lieu de trois plus deux interpréteurs ;
- **aucune dépendance** hors du JDK — vérifiable : le module n'a que JUnit, en portée test ;
- la **portabilité Windows**, gratuite, qui manquait ;
- une distribution naturelle par Maven, interne ou publique ;
- accessoirement, une **couverture de test réelle** sur la logique d'assemblage : lecture du
  XML de couverture, repli des frames, liens non cassés dans la page produite. Un script
  shell ne se teste pas comme ça.

## Ce qu'il resterait concrètement à faire

L'obstacle n'est plus la nature de l'artefact, il est administratif — et un point est
souvent découvert trop tard :

| Point | État actuel | Ce qu'il faudrait |
|---|---|---|
| Nature de l'artefact | ✅ un jar exécutable de ~100 Ko, sans dépendance | — |
| `groupId` | ⛔ `lab.tools` : **on ne peut pas prouver qu'on le possède** | `io.github.beennnn`, validé en prouvant le compte GitHub — c'est le chemin le plus court |
| Signature | ⛔ absente | Clé GPG publiée sur un serveur de clés, signature à la publication |
| Artefacts joints | ⛔ absents | Les jars `sources` et `javadoc`, exigés par la validation |
| Métadonnées du POM | à compléter | `description`, `url`, `licenses`, `developers`, `scm` sont obligatoires |
| Versions | ✅ `1.0.0` | Immuables : rien ne se corrige après coup, seulement une version de plus |

Aucun de ces points n'est difficile ; ensemble ils font une demi-journée la première fois,
puis un job de publication automatisé. Le vrai coût est ailleurs : une version publiée ne
se retire pas.

## Recommandation

1. **Maintenant** : rien à faire. La release GitHub suffit, et un dépôt interne suffit pour
   l'entreprise.
2. **Si plusieurs équipes l'adoptent** : déposer le jar sur le Nexus interne — une heure de
   travail, aucun compte à créer. C'est aussi ce qui permet de servir les composants
   d'analyse par le même miroir, ce dont l'outil a besoin de toute façon hors ligne.
3. **Si l'outil doit sortir du dépôt** : Maven Central devient défendable, maintenant que
   l'artefact publié serait un vrai jar exécutable et non un zip de scripts déguisé.
