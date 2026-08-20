# Reports demo

Ce dossier accueille les **sorties réellement générées** par chaque module
de `tools/`. Chaque outil dépose son rapport dans un sous-dossier dédié :

```
reports-demo/generated/<nom-outil>/
├── report.<ext>      # le(s) fichier(s) produit(s) par l'outil
└── NOTES.md          # ce qu'on y voit + interactif ou statique
```

## Grille de lecture (`NOTES.md` de chaque outil)

- **Aperçu** — capture d'écran ou description de ce qui est affiché
- **Statique / interactif** — le rapport est-il figé (HTML/PDF/image) ou
  permet-il une interaction (tri, filtre, drill-down, export depuis l'UI...) ?
- **Fidélité au cas d'usage de référence** — l'outil a-t-il permis de
  reproduire exactement le cas d'usage fixé dans `docs/METHODOLOGY.md`, ou
  a-t-il fallu s'en écarter (et pourquoi) ?

## Exemple

Le module `example-tool` dépose son rapport dans
`generated/example-tool/report.html` — un tableau HTML **statique** généré
sans aucune bibliothèque, servant de point de comparaison "sans outil".
