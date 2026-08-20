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

| Point | État | Détail |
|---|---|---|
| Nature de l'artefact | ✅ | Un jar exécutable de ~100 Ko, sans dépendance d'exécution |
| `groupId` | ✅ | `io.github.beennnn` — le seul espace de noms qu'on puisse prouver posséder sans posséder un domaine |
| Métadonnées du POM | ✅ | `name`, `description`, `url`, `licenses`, `developers`, `scm`, `issueManagement` |
| Artefacts joints | ✅ | `sources` et `javadoc` produits par le profil `release`, avec `doclint` désactivé — la documentation est rédigée pour des lecteurs, pas pour un vérificateur HTML |
| Plugin de publication | ✅ | `central-publishing-maven-plugin`, `autoPublish=false` : le dépôt est déposé mais **pas rendu définitif** sans relecture |
| Module de démonstration exclu | ✅ | `maven.deploy.skip` sur `sample-app` : un exemple n'a pas à occuper un espace de noms permanent |
| Signature GPG | ⛔ | La clé est **la vôtre** : à générer, à publier sur un serveur de clés |
| Espace de noms validé | ⛔ | À faire une fois sur le Central Portal, en prouvant le compte GitHub |
| Identifiants | ⛔ | Le jeton du Portal, dans `~/.m2/settings.xml` sous `<id>central</id>` |

### Les trois gestes qui restent

Ils demandent votre compte et votre clé, donc ils ne peuvent pas être faits à votre place.

1. **Valider l'espace de noms** sur [central.sonatype.com](https://central.sonatype.com) :
   créer un compte, déclarer `io.github.beennnn`, prouver le compte GitHub par le dépôt
   temporaire que le Portal demande. Une seule fois, définitivement.
2. **Une clé de signature** :
   ```bash
   gpg --gen-key
   gpg --list-keys --keyid-format short          # relever l'identifiant
   gpg --keyserver keyserver.ubuntu.com --send-keys <ID>
   ```
   Central refuse un artefact dont la clé n'est pas publiée : la signature doit être
   vérifiable par un tiers, sinon elle ne prouve rien.
3. **Les identifiants**, dans `~/.m2/settings.xml` — le jeton se génère depuis le Portal :
   ```xml
   <server>
     <id>central</id>
     <username>…</username>
     <password>…</password>
   </server>
   ```

Ensuite :

```bash
mvn -Prelease deploy
```

Le dépôt apparaît dans le Portal **sans être publié** : on relit ce qui part, puis on
confirme. C'est délibéré — une version publiée ne se retire pas.

Pour vérifier le montage sans rien signer ni envoyer :

```bash
mvn -Prelease clean install -Dgpg.skip=true
```

produit `runtime-xray-cli-1.0.0.jar`, `-sources.jar`, `-javadoc.jar` et le POM, c'est-à-dire
exactement les quatre artefacts que Central exige.

## Recommandation

1. **Maintenant** : rien à faire. La release GitHub suffit, et un dépôt interne suffit pour
   l'entreprise.
2. **Si plusieurs équipes l'adoptent** : déposer le jar sur le Nexus interne — une heure de
   travail, aucun compte à créer. C'est aussi ce qui permet de servir les composants
   d'analyse par le même miroir, ce dont l'outil a besoin de toute façon hors ligne.
3. **Si l'outil doit sortir du dépôt** : Maven Central devient défendable, maintenant que
   l'artefact publié serait un vrai jar exécutable et non un zip de scripts déguisé.
