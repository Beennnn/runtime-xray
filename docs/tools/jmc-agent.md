# JMC Agent

> **Statut : 📄 sur documentation** — non exécuté ici ; pas trouvé sous forme directement
> exécutable sur Maven Central (le groupe `org.openjdk.jmc` n'y publie que les bibliothèques).

## Ce que c'est

Un agent JVM du projet Mission Control qui **ajoute des événements JFR sur mesure**, sans
toucher au code source. On décrit dans un fichier XML les méthodes à instrumenter et les
valeurs à capturer ; l'agent réécrit le bytecode au chargement.

## Sa philosophie

**Étendre JFR plutôt que le remplacer.** Tout ce que JFR sait déjà faire — enregistrement
à faible surcoût, format standard, lecture dans JMC — reste acquis ; l'agent ne fait
qu'ajouter des événements que la JVM n'émet pas nativement, dont **les valeurs des
paramètres et les valeurs de retour**.

## Ce qu'il sait faire

| Capacité | |
|---|---|
| **Valeurs des paramètres** | ✅ — déclarées en XML, capturées dans des événements JFR |
| Valeurs de retour, champs | ✅ |
| Arbre d'appel | ✅ via les piles JFR |
| Lignes exécutées | ❌ |

## Son interface

Pas d'interface propre : les événements produits se lisent **dans JMC**, à côté des
événements natifs. Avantage réel — la capture de paramètres se retrouve corrélée au GC,
aux verrous et aux temps d'exécution dans un seul et même enregistrement.

## Comment on navigue dedans

Comme JFR : dans JMC (desktop) ou IntelliJ Ultimate. **Pas de page web**, donc pas de
canal direct vers un lecteur non technique.

## Mise en œuvre

Un XML de sondes à écrire, puis `-javaagent:agent.jar=probes.xml`. La configuration est
plus lourde qu'un flag — ça pèse contre le **critère n° 1**, mais elle est déclarative
et versionnable, donc rejouable à l'identique.

## Licence et coût

Projet OpenJDK Mission Control, **open source**, **0 €**. Fonctionne hors ligne une fois le
jar récupéré.

## Ce qu'on peut en espérer

**La piste gratuite la plus cohérente pour les valeurs de paramètres**, précisément parce
qu'elle s'appuie sur JFR, déjà présent. À tester juste après Arthas — et d'autant plus
intéressante si le portage vers Java 25 est décidé, puisque tout converge alors vers un
seul enregistrement JFR.

## Facile / moins facile

**Ce qui est facile.** S'adosser à l'existant : les événements produits arrivent dans le même enregistrement JFR que le GC et les verrous, corrélés.

**Ce qui l'est moins.** **Trouver un binaire prêt à l'emploi** — il n'est pas publié sous forme exécutable sur Maven Central. Écrire le XML de sondes. Et lire le résultat, qui suppose JMC.
