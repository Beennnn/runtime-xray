# Diffuser l'outil

> Question de départ : *comment met-on cet outil entre les mains de quelqu'un d'autre ?*

## Ce que l'outil est aujourd'hui

**Un seul jar**, `runtime-xray.jar`, sans aucune dépendance hors du JDK :
`java -jar runtime-xray.jar --config mon-projet.conf`. Ni bash, ni Python.

Ça n'a pas toujours été le cas — et c'est cette question qui a déclenché la réécriture. La
version d'origine était un script shell plus un script Python : deux prérequis de plus sur
une machine où **Java est forcément présent**, et aucune portabilité Windows. L'argument
« si on le distribue comme un fichier, autant que ce soit un jar » a tenu.

## Les trois voies, comparées

| Voie | Effort pour celui qui l'utilise | Quand la choisir |
|---|---|---|
| **Dépôt Maven public** *(retenu)* | un `curl`, rien à installer, aucun clone | Par défaut |
| **Miroir Maven interne** | idem, servi par le Nexus maison | Réseau fermé, si le miroir ne relaie pas Central |
| **Construction depuis les sources** | cloner, installer Maven, compiler | Seulement pour modifier l'outil |

⚠️ Le point souvent manqué : **en réseau fermé, un dépôt public est inutile.** Déposer le
jar sur un miroir interne donne exactement le même bénéfice, sans compte, sans signature,
sans processus de publication.

L'intérêt d'un dépôt Maven, quel qu'il soit, n'est pas « être une bibliothèque » — personne
ne mettra cet outil en `<dependency>`. C'est de **passer par le tuyau qui existe déjà** :
derrière un pare-feu, le miroir Maven est souvent le seul canal ouvert, et c'est déjà par
lui que l'outil récupère ses composants d'analyse.

## Ce qu'un dépôt public exige

Mené jusqu'au bout sur la version `1.0.0` :

- un **espace de noms** qu'on prouve posséder, ce qui suppose de prouver un compte ou un
  domaine ;
- des artefacts **signés GPG**, la clé publiée sur un serveur public, accompagnés des jars
  `sources` et `javadoc` ;
- une publication par un portail, avec des règles de validation strictes et un paquet à
  relire avant confirmation ;
- des versions **immuables** : rien ne se corrige après coup, seulement une version de plus.

Conclusion : c'est une mise en place ponctuelle, ensuite automatisable, et elle vaut la
peine. Elle achète la seule chose qui compte pour qui veut juste essayer l'outil — **ne
rien avoir à faire**. Pas de clone, pas de JDK à aligner, pas de build à réussir, pas de
compte à créer : un `curl` et un `java -jar`.

Ce point a un corollaire qui n'est pas anecdotique. Un utilisateur qui clone le dépôt pour
compiler se retrouve avec un dépôt git sous la main, et l'outil écrit son rapport dans le
répertoire de travail — un rapport qui porte les noms de classes et les valeurs de
paramètres du code analysé. Distribuer un jar évite entièrement cette configuration :
il n'y a pas de dépôt, donc rien à y pousser par mégarde.

## Ce que la réécriture en Java a apporté

- **un seul fichier** à transporter, au lieu de trois plus deux interpréteurs ;
- **aucune dépendance** hors du JDK — vérifiable : le module n'a que JUnit, en portée test ;
- la **portabilité Windows**, gratuite, qui manquait ;
- accessoirement, une **couverture de test réelle** sur la logique d'assemblage : lecture du
  XML de couverture, repli des frames, liens non cassés dans la page produite. Un script
  shell ne se teste pas comme ça.
