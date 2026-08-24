# Code tiers présent dans ce dépôt

La [licence du dépôt](LICENSE) — 0BSD, sans aucune condition — couvre **le code écrit
ici** : l'orchestrateur (`orchestrator/`), le programme de démonstration (`sample-app/`),
les scripts (`tools/`, `bin/`), la documentation (`docs/`) et la page d'accueil (`site/`).

Elle ne couvre pas, et ne peut pas couvrir, les fichiers ci-dessous : ils appartiennent à
leurs auteurs et restent sous leur licence d'origine.

## Fichiers tiers versionnés

Ce sont des **sorties d'outils** conservées comme pièces justificatives de l'étude. Elles
embarquent les ressources que ces outils placent eux-mêmes dans leurs rapports.

| Chemin | Origine | Licence |
|---|---|---|
| `reports-demo/**/jacoco-resources/`<br>`reports-demo/executions/**/jacoco-resources/` | Ressources de rapport JaCoCo (`report.css`, `sort.js`, les GIF d'icônes) | [EPL-2.0](https://www.eclipse.org/legal/epl-2.0/) |
| `**/jacoco-resources/prettify.js`<br>`**/jacoco-resources/prettify.css` | google-code-prettify, redistribué par JaCoCo | Apache-2.0 |
| `reports-demo/executions/**/async-profiler/flamegraph*.html` | Flamegraphes async-profiler, JS de navigation inclus | Apache-2.0 |

Ces répertoires sont **regénérables** : ils ne sont versionnés que pour que les
affirmations du [comparatif](docs/etude/comparatif.md) restent vérifiables sans relancer
toute la chaîne.

## Outils récupérés à l'exécution

Le jar ordinaire (`runtime-xray.jar`, ~170 Ko) ne redistribue rien de ce qui suit : il le
télécharge depuis un dépôt Maven au premier lancement, dans `~/.runtime-xray`.

| Outil | Licence |
|---|---|
| [JaCoCo](https://www.jacoco.org/) | EPL-2.0 |
| [async-profiler](https://github.com/async-profiler/async-profiler) | Apache-2.0 |
| [Arthas](https://arthas.aliyun.com/) | Apache-2.0 |

### Deux éditions, elles, les redistribuent

`runtime-xray-jacoco.jar` (~950 Ko, `mvn -Pjacoco package`) et `runtime-xray-complet.jar`
(~19 Mo, `mvn -Pcomplet package`) **embarquent** ces outils, sous la forme exacte de leurs
archives publiées sur Maven Central — aucune modification, aucun désassemblage, aucun mélange
avec notre code. Ils les déposent dans `~/.runtime-xray` au premier lancement, puis s'effacent
du chemin.

Ces artefacts existent pour un seul cas : la machine sans réseau, où un fichier unique à
porter vaut mieux qu'un cache à préparer. Ils ne sont pas publiés sur Maven Central.

Ce qui s'y applique, et qui ne s'applique pas au jar ordinaire :

| Fichier embarqué | Dans quelle édition | Origine | Licence |
|---|---|---|---|
| `lab/xray/composants/org.jacoco.agent-*-runtime.jar`<br>`lab/xray/composants/org.jacoco.cli-*-nodeps.jar` | `jacoco` et `complet` | [JaCoCo](https://www.jacoco.org/) | [EPL-2.0](https://www.eclipse.org/legal/epl-2.0/) |
| `lab/xray/composants/async-profiler-*.jar`<br>`lab/xray/composants/jfr-converter-*.jar` | `complet` | [async-profiler](https://github.com/async-profiler/async-profiler) | Apache-2.0 |
| `lab/xray/composants/arthas-packaging-*-bin.zip` | `complet` | [Arthas](https://arthas.aliyun.com/) | Apache-2.0 |

L'EPL-2.0 autorise cette redistribution sous forme binaire et demande que la licence
accompagne les fichiers et que la source reste accessible : les archives sont intactes,
elles portent leurs propres notices, et les sources de JaCoCo sont publiées par le projet
lui-même. La licence 0BSD de ce dépôt ne couvre ni ne modifie ces fichiers.

Toute la capacité d'observation vient de ces trois projets. Ce dépôt n'apporte que
l'assemblage de leurs sorties.
