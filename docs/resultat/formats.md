# Découpler la collecte de l'affichage — les formats pivots

> Question de départ : *peut-on séparer le résultat des appels de son affichage, via un format
> standardisé, pour le regarder ensuite dans IntelliJ ou dans un outil web ?*

**Réponse courte : oui pour la couverture, oui pour l'arbre d'appel, non pour les valeurs
de paramètres.** Et pour les deux premiers, ce découplage n'est pas à construire — il
existe déjà, et il a été vérifié ici.

## Couverture — le découplage est natif

JaCoCo sépare déjà les deux moments : l'agent écrit un fichier de **données brutes**
(`jacoco.exec`), et le rendu est une opération distincte, rejouable autant de fois qu'on
veut, avec un périmètre différent à chaque fois.

C'est exactement ce que fait [`collect-focused.sh`](../../tools/jacoco/collect-focused.sh) :
même `.exec`, deuxième rendu restreint aux classes réellement exécutées. **La donnée est
collectée une fois, affichée plusieurs fois.**

| Format | Écrit par | Lu par |
|---|---|---|
| **`jacoco.exec`** (binaire) | l'agent JaCoCo | la CLI JaCoCo, IntelliJ *(import à confirmer)* |
| **XML JaCoCo** | le rendu JaCoCo | **SonarQube**, Jenkins, la plupart des outils de qualité |
| **CSV** | le rendu JaCoCo | n'importe quel script — c'est la source du [résumé Markdown](../../reports-demo/executions/rapport.md) |
| **Cobertura XML** | conversion depuis le XML JaCoCo | **GitLab**, nativement, dans la diff d'une merge request |
| **LCOV** | monde JS surtout | VS Code (extensions de couverture), Codecov |
| **`.ic` IntelliJ** | IDEA | IDEA seul — format fermé, à éviter comme pivot |

**Le pivot à retenir : le XML JaCoCo.** C'est lui que consomme l'écosystème. Le
`.exec` reste utile en amont, puisqu'il permet de re-rendre autrement sans réexécuter.

## Arbre d'appel — deux pivots, dont un vérifié ici

async-profiler écrit **sept formats** : `flat`, `traces`, `collapsed`, `flamegraph`,
`tree`, `jfr`, `otlp`. Les trois derniers intéressent le découplage.

### JFR — le pivot riche, et la démonstration

**Vérifié sur ce poste** : async-profiler écrit un `.jfr`, et l'outil `jfr` du JDK le relit
sans rien savoir de son origine.

```
$ jfr summary reports-demo/generated/formats/profil.jfr
 Event Type                          Count
 jdk.ExecutionSample                  1296
 jdk.NativeLibrary                     525
```

**C'est le découplage demandé, fait** : un profil collecté par un outil tiers, dans un
format du JDK, lisible par les outils du JDK — donc par **JMC** et par **IntelliJ IDEA
Ultimate**, qui ouvrent les `.jfr` nativement.

### Collapsed — le pivot universel des flame graphs

Le format « piles repliées » (une ligne par pile d'appel, suivie d'un compte) est la
lingua franca du profilage :

```
lab/sample/Main.main;lab/sample/RoutePlanner.travelTimeMinutes;lab/sample/terrain/Terrain.slowdownFactor 63
```

Trivial à produire, trivial à relire — c'est d'ailleurs à partir de lui qu'est calculé le
classement des méthodes du [résumé Markdown](../../reports-demo/executions/rapport.md). Il est lu par
**speedscope** (visualiseur web statique, donc auto-hébergeable hors ligne), par le
`flamegraph.pl` historique, et par les backends de profilage continu. *(Ces trois
lectures reposent sur la documentation : non testées ici.)*

### OTLP — le pivot des backends

`-o otlp` produit un profil au format OpenTelemetry, destiné aux backends de profils
continus. Pertinent seulement si le besoin bascule vers de la supervision — écarté par la
décision n° 4.

## Valeurs des paramètres — aucun format standard

C'est le point faible, et il faut le dire nettement.

| Outil | Ce qu'il produit | Standardisé ? |
|---|---|---|
| Arthas | texte de console, expressions OGNL rendues | ❌ ad hoc, sans schéma |
| JProfiler / YourKit | instantanés | ❌ formats propriétaires |
| **JMC Agent** | **des événements JFR avec champs typés** | ✅ **JFR** |

**Une seule voie standardisée existe : JFR.** Un événement JFR porte des champs typés, et
c'est précisément ce qu'exploite le [JMC Agent](../etude/fiches/jmc-agent.md) — on déclare en XML les
paramètres à capturer, ils deviennent des événements JFR, lisibles par n'importe quel outil
qui lit le JFR.

**Conséquence stratégique** : si le découplage des valeurs de paramètres devient un
objectif, la piste JMC Agent cesse d'être une simple alternative à Arthas. Elle devient la
seule qui produise une donnée **structurée et réutilisable**, là où Arthas produit du texte
à relire à l'œil. C'est un argument que le tableau des capacités seul ne fait pas apparaître.

## Les trois architectures possibles

```
1.  agent JaCoCo ──► jacoco.exec ──┬─► HTML annoté        (navigateur, hors ligne)
                                   ├─► XML ──► SonarQube  (portail web auto-hébergé)
                                   ├─► XML ──► Cobertura ──► GitLab (diff de MR)
                                   └─► CSV ──► résumé Markdown (rendu par la forge)

2.  async-profiler ──┬─► .jfr ──────► JMC / IntelliJ Ultimate
                     ├─► collapsed ─► speedscope (web statique) / flamegraph.pl
                     └─► HTML ───────► navigateur, sans rien installer

3.  JMC Agent ──► événements JFR ──► JMC / IntelliJ Ultimate   (valeurs de paramètres)
```

## Les limites, honnêtement

- **Aucun format ne porte les trois données ensemble.** Il faudra toujours au moins deux
  fichiers : un pour la couverture, un pour le profil. Un « rapport unifié » n'existe pas.
- **Les visualiseurs web de profils ne montrent pas le code source annoté.** speedscope
  affiche un flame graph, pas vos fichiers. Pour naviguer dans le code, le HTML de JaCoCo
  reste incontournable.
- **Le pivot vers GitLab demande une conversion** JaCoCo → Cobertura, à outiller.
- **speedscope, Perfetto et consorts sont des applications web** : statiques et donc
  auto-hébergeables hors ligne, mais à installer et à maintenir — ce que les décisions
  n° 3 et 4 cherchaient justement à éviter.

## Ce que ça change pour le choix

Rien sur les outils retenus : JaCoCo et async-profiler produisent déjà les formats pivots,
sans effort supplémentaire. **Le découplage est un acquis, pas un chantier.**

Mais il ouvre une porte : le jour où l'affichage ne convient plus — parce qu'il faut un
portail, parce qu'il faut GitLab, parce qu'il faut IntelliJ — **on change l'affichage sans
changer la collecte**. Aucun de ces outils ne vous enferme dans sa propre visionneuse.

C'est cette porte que franchit `--export` : les mesures y sont réécrites en `perf script`,
en `cpuprofile` et en LCOV, pour être ouvertes dans Firefox Profiler, speedscope ou un
éditeur — sans que rien de la collecte ne change. Le recensement des formats ouverts,
retenus et écartés avec le motif, est dans [Reprendre le résultat dans un autre
outil](../outil/exports.md).
