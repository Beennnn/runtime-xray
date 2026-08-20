# Runtime X-Ray

Un cadre **générique et réutilisable** pour comparer objectivement plusieurs outils
(bibliothèques, frameworks, services...) sur la base de trois livrables :

1. **Un tableau comparatif** — features, contraintes techniques, coûts ([docs/COMPARISON.md](docs/COMPARISON.md))
2. **Du code Java "dummy"** — une implémentation minimale par outil, juste assez de code
   pour l'exercer réellement et produire un rapport (dossier [tools/](tools/))
3. **Une démo des rapports générés** — les sorties produites par chaque outil, avec des
   notes sur ce qu'on y voit et sur leur caractère interactif ou non
   ([reports-demo/](reports-demo/))

Ce dépôt ne présuppose **aucun outil en particulier**. Il fournit la structure,
les conventions et un module d'exemple ; à toi d'ajouter un module par outil que tu
souhaites évaluer.

## Structure

```
runtime-xray/
├── docs/
│   ├── METHODOLOGY.md      # comment mener une évaluation cohérente
│   └── COMPARISON.md       # tableau comparatif (features / contraintes / coûts)
├── tools/
│   ├── README.md           # comment ajouter un nouvel outil à évaluer
│   └── example-tool/       # module d'exemple fonctionnel (baseline sans dépendance)
├── reports-demo/
│   ├── README.md           # convention de dépôt + grille de lecture des démos
│   └── generated/          # sorties générées (HTML/PDF/CSV/... par outil)
├── scripts/
│   └── generate-all-reports.sh
└── pom.xml                 # POM parent (multi-module Maven)
```

## Démarrer

```bash
# construire tous les modules
mvn -q clean package

# générer les rapports de démo de tous les outils
./scripts/generate-all-reports.sh
```

## Ajouter un outil à comparer

Voir [tools/README.md](tools/README.md) pour la marche à suivre (copier le module
`example-tool`, brancher la dépendance de l'outil, remplir une ligne dans
`docs/COMPARISON.md`, déposer le rapport généré dans `reports-demo/generated/`).

## Licence

MIT — voir [LICENSE](LICENSE).
