# Collecteurs, un dossier par outil

Chaque dossier contient la procédure **rejouable** d'un outil : un script qui produit une
sortie réelle dans `reports-demo/generated/<outil>/`.

| Dossier | Ce qu'il produit |
|---|---|
| [`jacoco/collect.sh`](jacoco/collect.sh) | Couverture d'une exécution → site HTML à code source annoté |
| [`jacoco/collect-focused.sh`](jacoco/collect-focused.sh) | Le même rapport **restreint aux classes réellement exécutées** |
| [`async-profiler/collect.sh`](async-profiler/collect.sh) | Flame graph + arbre d'appel, deux HTML autonomes |
| [`jfr/collect.sh`](jfr/collect.sh) | Enregistrement JFR + extraits texte, adapté à la version du JDK |
| [`java-env.sh`](java-env.sh) | Fixe la version de Java utilisée par tous les collecteurs |

## Choisir la version de Java

Le projet évalue **deux plateformes**. Par défaut c'est Java 21 ; `JAVA_TARGET` bascule :

```bash
./tools/jacoco/collect.sh              # Java 21 (défaut)
JAVA_TARGET=25 ./tools/jfr/collect.sh  # Java 25
```

Fixer la version explicitement est indispensable : prendre « le java du PATH » reviendrait
à documenter des capacités que la plateforme cible n'a pas.

## Ajouter un outil

⚠️ **Un outil n'est pas un module Maven.** C'est le point qui surprend en arrivant : ces
outils s'attachent à une JVM, ils ne se déclarent pas en dépendance.

1. Créer `tools/<outil>/collect.sh`, sur le modèle d'un existant :
   - `source "$REPO_ROOT/tools/java-env.sh"` en tête ;
   - lancer **`sample-app` sans argument** — c'est l'exécution de référence, la même pour
     tous, sinon la comparaison ne vaut rien ;
   - écrire dans `reports-demo/generated/<outil>/`.
2. Écrire la fiche `docs/tools/<outil>.md` en suivant la trame des autres.
3. Compléter les tableaux de [`docs/COMPARISON.md`](../docs/COMPARISON.md) **avec le statut
   de vérification** — ✅ testé ici seulement si le script a réellement tourné.
4. Si l'outil demande une licence : ne rien mettre dans le dépôt, lire la clé depuis
   l'environnement — voir [EVALUATION-KEYS.md](../docs/EVALUATION-KEYS.md).

## Ce qui n'est pas versionné

Les enregistrements `.jfr` (binaires, ~10 Mo pour 10 s, illisibles hors JMC, et dont le
contenu aléatoire déclenche le scanner de secrets de GitHub). Les extraits texte, eux, le sont.
