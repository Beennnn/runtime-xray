# Deux compétences, à recopier dans son projet

Ce sont deux fichiers Markdown. Rien à installer, rien à compiler, aucune dépendance : ils
disent à un assistant ce qu'il faut savoir sur `runtime-xray` avant de s'en servir.

```sh
mkdir -p .claude/skills
cp -r skills/runtime-xray skills/runtime-xray-lire .claude/skills/
```

Elles se déclenchent seules, sur le vocabulaire de la question ou sur ce que contient le
répertoire de travail — un `faits.jsonl` suffit à réclamer la seconde.

| Compétence | Ce qu'elle sait |
|---|---|
| [`runtime-xray`](runtime-xray/SKILL.md) | conduire une campagne : la commande, le choix de la racine, ce que coûte chaque niveau, l'injection dans un processus qu'on ne lance pas, la machine sans réseau |
| [`runtime-xray-lire`](runtime-xray-lire/SKILL.md) | répondre à partir d'un rapport déjà produit : par où commencer, le vocabulaire des faits, et ce qu'on n'a pas le droit d'en conclure |

## Pourquoi deux, et pas une

Ce sont **deux moments séparés par des heures**, et souvent par deux personnes. Qui lance
une recette n'est pas qui la dépouille ; qui dépouille n'a en général plus la main sur la
machine observée. Une compétence unique ferait porter à chacun ce dont l'autre a besoin,
et — plus grave — laisserait croire au lecteur d'un rapport qu'il peut relancer la mesure.

## Ce qu'elles portent, et qui ne se devine pas

Le contenu n'est pas un résumé du mode d'emploi. Ce sont les erreurs qui coûtent une
campagne entière, et qu'aucune documentation lue *après coup* ne rattrape :

- **Les quatre restrictions ne restreignent pas la même chose.** `--sources` commande
  l'affichage, `--root` les valeurs, `--filter` le temps, `--cover` la couverture. On élargit
  la mauvaise, on relance, on obtient le même rapport.
- **Un zéro peut vouloir dire « pas mesuré ».** Sous Windows il n'y a aucune mesure de
  temps ; sans cette phrase, tout y ressemble à du code jamais appelé.
- **Une racine de sources ne se devine pas.** Du code faux affiché en face d'une couverture
  coûte plus cher qu'un panneau vide, parce qu'on le croit.
- **`index.html` n'est pas la donnée.** C'est une application ; les faits sont dans
  `faits.jsonl`, et il porte son propre dictionnaire en première ligne.

## Pour un assistant qui n'a pas de compétences

Ces fichiers restent lisibles tels quels : les donner en pièce jointe, ou pointer
`skills/runtime-xray-lire/SKILL.md`. Ils ne dépendent d'aucun format propriétaire — c'est du
Markdown avec un en-tête.

Voir aussi [Faire lire le rapport par une IA](../docs/outil/integration-ia.md), qui traite du
cas où c'est le *rapport* qu'on donne à lire, et non la marche à suivre.
