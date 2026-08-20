# BTrace & Byteman

> **Statut : 📄 sur documentation** — non exécutés ici.

Deux outils différents, même famille : **injecter du code d'observation dans une
application en cours, sans la recompiler**.

## BTrace

### Ce que c'est
Un traceur pilotable par des scripts Java annotés. On écrit un petit programme qui dit
« quand telle méthode est appelée, imprime tel paramètre » ; BTrace le compile et l'injecte.

### Sa philosophie
**Un langage bridé, exprès.** Les scripts BTrace ne peuvent ni allouer, ni boucler, ni
appeler n'importe quoi : ces restrictions garantissent qu'une sonde ne peut pas casser
l'application observée. C'est un choix de sûreté avant l'expressivité — pertinent quand on
instrumente autre chose qu'un bac à sable.

### Ce qu'il sait faire
Valeurs des paramètres ✅ · arbre d'appel ✅ · lignes exécutées ❌ · sortie texte ou JFR.

## Byteman

### Ce que c'est
Un moteur de règles d'injection (projet JBoss/Red Hat). On écrit des règles
*event–condition–action* : « à l'entrée de cette méthode, si telle condition, alors faire
ceci ».

### Sa philosophie
**Modifier le comportement, pas seulement l'observer.** Byteman vient du test de
robustesse : sa raison d'être première est d'injecter des pannes (lever une exception,
ralentir un appel). L'observation en est un sous-produit — puissant, mais l'outil vise
plus large que notre besoin.

### Ce qu'il sait faire
Valeurs des paramètres ✅ · injection de comportement ✅ · arbre d'appel ⚠️ indirect ·
lignes exécutées ❌.

## Pour les deux

**Interface** : sortie texte, pas de rapport navigable. **Aucun canal vers un lecteur non
technique** sans mise en forme supplémentaire.

**Licence** : open source, **0 €** *(licences exactes à confirmer)*. Fonctionnent hors ligne.

**Ce qu'on peut en espérer** : la capture de paramètres, au prix d'un script ou d'une règle
à écrire pour chaque question posée. Plus souples qu'Arthas, moins immédiats. À considérer
**si** Arthas et JMC Agent échouent — ce sont les plus exigeants des trois en effort
d'écriture, donc les plus pénalisés par le critère n° 1.

## Facile / moins facile

**Ce qui est facile.** Exprimer une question précise : ces outils peuvent capturer à peu près n'importe quoi.

**Ce qui l'est moins.** **Tout le reste.** Un script ou une règle à écrire par question posée, une sortie texte sans mise en forme, et pour Byteman un outil dont l'objet premier est d'injecter des pannes, pas d'observer.
