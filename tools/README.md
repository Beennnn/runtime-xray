# Ajouter un outil à évaluer

1. **Copier le module d'exemple**
   ```bash
   cp -r tools/example-tool tools/<nom-outil>
   ```
2. **Renommer** le `artifactId` dans `tools/<nom-outil>/pom.xml` et l'ajouter comme
   module dans le `pom.xml` parent (racine du projet).
3. **Ajouter la dépendance** de l'outil évalué dans `tools/<nom-outil>/pom.xml`.
4. **Écrire le code dummy** : reproduire le même cas d'usage de référence que les
   autres modules (voir [../docs/METHODOLOGY.md](../docs/METHODOLOGY.md)) — le
   plus simple possible, juste ce qu'il faut pour produire un rapport.
5. **Faire écrire la sortie** du rapport dans `reports-demo/generated/<nom-outil>/`.
6. **Compléter une ligne** par tableau dans [../docs/COMPARISON.md](../docs/COMPARISON.md)
   (features / contraintes / coûts).
7. **Documenter la démo** : ajouter `reports-demo/generated/<nom-outil>/NOTES.md`
   décrivant ce que montre le rapport et s'il est interactif ou statique.

Chaque module doit rester **autonome et buildable seul** (`mvn -pl tools/<nom-outil> package`).
