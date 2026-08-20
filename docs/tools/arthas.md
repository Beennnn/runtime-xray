# Arthas

> **Statut : 🚧 bloqué techniquement ici** — le lanceur télécharge ses modules depuis
> `arthas.aliyun.com`, injoignable depuis ce poste. Blocage d'installation, pas de principe.

## Ce que c'est

Une console de diagnostic qui **s'attache à une JVM déjà en cours** et permet de
l'interroger en direct, par commandes : quelles méthodes sont appelées, avec quels
arguments, quel résultat, quelle pile.

## Sa philosophie

**Poser des questions à une application vivante**, sans la redémarrer ni la modifier.
Là où un profiler enregistre puis présente un rapport, Arthas est interactif : on tape
`watch`, on voit passer des valeurs, on affine, on arrête. C'est un outil de *diagnostic*
au sens médical, pas un outil de mesure globale.

## Ce qu'il sait faire

| Capacité | |
|---|---|
| **Valeurs des paramètres** | ✅ — `watch` avec expressions OGNL sur `params`, `returnObj`, `throwExp` |
| Arbre d'appel | ✅ — `trace` (chemin + temps par étage), `stack` (qui m'a appelé ?) |
| Lignes exécutées | ❌ |
| Décompilation à chaud, rechargement de classe | ✅ |

C'est **le seul outil gratuit recensé qui réponde directement au troisième besoin du
brief** — voir les valeurs passées — sans écrire de script d'instrumentation.

## Son interface

Une **console** (terminal ou navigateur sur `http://<hôte>:8563`). La commande centrale :

```
watch lab.sample.RoutePlanner travelTimeMinutes '{params[0].mode(), params[0].weather(), returnObj}' -n 3 -x 2
```

→ affiche, pour les 3 prochains appels, le mode, la météo et le résultat. **Les valeurs,
pas les types.**

## Comment on navigue dedans

- **Console web** ✅ — accessible sans installer quoi que ce soit côté lecteur.
- **Pas de rapport à envoyer** : la sortie est un flux de texte. Pour un non-technicien,
  il faut la reprendre et la mettre en forme.
- Pas d'intégration IDE ni plateforme.

## Mise en œuvre

Simple **si** le réseau le permet : un jar, un PID, des commandes. Hors ligne, il faut
récupérer au préalable le paquet complet (`arthas-packaging`, ~17 Mo, disponible sur Maven
Central) et le déposer dans `~/.arthas/lib/<version>/arthas` — c'est cette étape qui a
manqué ici.

## Licence et coût

**Apache 2.0**, open source, **0 €**. Projet Alibaba, largement utilisé.

## Ce qu'on peut en espérer

C'est le **meilleur candidat gratuit pour combler le trou** laissé par JaCoCo et
async-profiler. À valider en priorité : s'il tient ses promesses hors ligne, l'option
« tout gratuit » devient crédible et la question de la licence commerciale se pose
beaucoup moins.

## Facile / moins facile

**Ce qui est facile.** Poser une question à une application vivante : `watch`, et les valeurs défilent. C'est le geste le plus direct de tout le panel pour le troisième besoin.

**Ce qui l'est moins.** **L'installer hors ligne** — le lanceur veut Internet, il faut déposer le paquet complet à la main. Et **transmettre le résultat** : la sortie est un flux de texte dans une console, pas un rapport.
