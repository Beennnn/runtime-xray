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

L'outil ne redistribue rien de ce qui suit : il le télécharge depuis un dépôt Maven au
premier lancement, dans `~/.runtime-xray`.

| Outil | Licence |
|---|---|
| [JaCoCo](https://www.jacoco.org/) | EPL-2.0 |
| [async-profiler](https://github.com/async-profiler/async-profiler) | Apache-2.0 |
| [Arthas](https://arthas.aliyun.com/) | Apache-2.0 |

Toute la capacité d'observation vient de ces trois projets. Ce dépôt n'apporte que
l'assemblage de leurs sorties.
