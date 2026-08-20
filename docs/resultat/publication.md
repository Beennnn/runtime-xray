# Publier le rapport — quels canaux, et lesquels marchent vraiment

> Question posée : *« ça peut se faire dans git ? un fichier GitHub, ou un espace sous
> GitHub ? »* — Réponse : **oui, de quatre façons différentes**, dont trois testées ici.
> Elles ne se valent pas.

## ⚠️ D'abord, la limite qui prime sur tout

**GitHub est en ligne.** Dans un environnement réellement déconnecté, aucun canal GitHub
ne fonctionne. Ils sont utiles pour ce dépôt d'étude, pour une équipe qui a accès à une
forge, ou pour une forge interne (GitLab auto-hébergé propose les mêmes mécanismes).

En environnement coupé, il ne reste que le **fichier transporté** — et c'est précisément
ce que produit le canal n° 3. C'est aussi ce qui a été retenu comme canal principal.

## Le problème : GitHub affiche le HTML comme du code source

Cliquer sur un `.html` dans l'interface GitHub montre ses balises, pas la page. C'est
volontaire (afficher du HTML arbitraire depuis un dépôt serait une faille), et ça ne se
contourne pas par un réglage. Quatre réponses, de la meilleure à la plus bancale :

| Réponse | Ce que ça donne | Statut |
|---|---|---|
| **Un résumé en Markdown** — [`runtime-xray-out/rapport.md`](../../runtime-xray-out/rapport.md) | GitHub le rend **nativement**, y compris sur un dépôt privé, sans Pages, sans service tiers. Tableaux de couverture, méthodes les plus coûteuses, valeurs capturées | ✅ en place |
| **GitHub Pages** | Le vrai rapport, rendu, à une URL | ✅ en place |
| **Le zip d'une release** | Le rapport complet, hors ligne | ✅ en place |
| `htmlpreview.github.io` | Un proxy qui rend le HTML brut d'un dépôt | ⚠️ service tiers, en ligne, casse souvent sur les ressources liées — **non retenu** |

**Le Markdown est la seule réponse qui marche à l'intérieur de GitHub**, sans dépendre de
Pages ni d'un tiers. Il est généré par
l'orchestrateur, dans le même passage que la page HTML et à partir des
sorties *machine* des outils — le CSV de JaCoCo, les piles repliées d'async-profiler, la
capture d'Arthas. Rien n'y est saisi à la main.

Sa limite est réelle : c'est un **résumé**. On y voit les chiffres et le classement, pas le
code colorié ligne à ligne. Pour ça, il faut Pages ou le zip.

> À noter : **le SVG, lui, s'affiche nativement dans GitHub** — d'où les schémas de ce
> dépôt. Un flame graph exporté en SVG plutôt qu'en HTML se verrait donc directement dans
> la forge. async-profiler produit du HTML ; la conversion depuis le format `collapsed`
> vers un SVG est possible avec l'outillage FlameGraph historique. Piste non explorée ici.

## 1. Les fichiers versionnés dans le dépôt — ✅ testé

Les rapports sont commis dans [`reports-demo/generated/`](../../reports-demo).

**Ce que ça donne** : l'historique. On peut comparer la couverture d'aujourd'hui à celle
d'il y a trois mois, et un `git diff` sur le CSV montre exactement quelles classes ont
changé de statut.

**La limite, et elle est rédhibitoire pour la lecture** : GitHub affiche un fichier `.html`
comme **du code source**, pas comme une page rendue. Cliquer sur `index.html` dans
l'interface GitHub donne du HTML brut illisible. Versionner sert à archiver et à comparer,
**pas à consulter**.

## 2. GitHub Pages — ✅ testé, en service

**→ [beennnn.github.io/runtime-xray](https://beennnn.github.io/runtime-xray/)**

Une branche `gh-pages` contenant les rapports, servie comme un vrai site. Le HTML est
rendu, la navigation fonctionne, on partage une URL.

**Ce que ça donne** : le canal le plus immédiat — un lien, rien à télécharger, rien à
installer, y compris depuis un téléphone.

**Les limites** : le site est **public** si le dépôt l'est (Pages privé relève des offres
payantes) ; et il faut republier la branche à chaque nouvelle exécution.

## 3. Une release avec le rapport en pièce jointe — ✅ testé, **canal retenu**

**→ [Release rapport-2026-08-20](https://github.com/Beennnn/runtime-xray/releases/tag/rapport-2026-08-20)**
— `rapport-runtime-xray.zip`, **266 Ko**.

C'est exactement la décision n° 3 : *un dossier envoyé dans un fichier, analysable sans
IntelliJ*. Le zip contient les quatre volets, numérotés, avec un `LISEZ-MOI.txt` qui dit
quoi ouvrir en premier :

```
rapport-runtime-xray/
├── LISEZ-MOI.txt
├── 1-couverture-code-execute/    ← ouvrir index.html
├── 2-couverture-complete/
├── 3-arbre-appel/
└── 4-valeurs-parametres/
```

**Ce que ça donne** : un objet autonome, daté, transportable — par clé USB, par mail, par
partage réseau. Il survit à la coupure d'Internet, ce que ni Pages ni la forge ne font.

**La commande** :

```bash
gh release create rapport-$(date +%F) rapport-runtime-xray.zip \
  --title "Rapport d'analyse dynamique — $(date +%F)"
```

**La limite** : rien n'est rendu en ligne, il faut télécharger et décompresser. C'est le
prix de l'autonomie.

## 4. Un artefact de workflow GitHub Actions — 📄 non testé ici

Un workflow qui rejoue les collecteurs à chaque exécution et publie le zip en artefact.

**Ce que ça donne** : le rapport devient automatique, rattaché à un commit précis.

**Les limites** : rétention limitée (90 jours par défaut), téléchargement réservé aux
personnes connectées à GitHub, et **sur un dépôt privé les minutes sont facturées**.

## Ce que GitHub ne sait pas faire

**Afficher la couverture dans la diff d'une pull request** — pas nativement. Il faut un
service tiers (Codecov, Coveralls), donc une connexion sortante, donc incompatible avec la
contrainte hors ligne.

**GitLab, lui, le fait nativement** à partir d'un rapport au format Cobertura, et
s'auto-héberge. Si la forge interne est un GitLab, ce canal redevient disponible — c'est la
seule façon connue de voir les lignes couvertes directement dans une revue de code, hors
ligne.

**Aucune forge ne sait afficher un arbre d'appel ni des valeurs de paramètres.** Pour
ceux-là, le fichier autonome reste le seul canal.

## Récapitulatif

| Canal | Rendu lisible | Hors ligne | Autonome | Testé |
|---|---|---|---|---|
| Fichiers versionnés | ❌ source brut | ❌ | ❌ | ✅ |
| GitHub Pages | ✅ | ❌ | ❌ | ✅ |
| **Release + zip** | ✅ après décompression | ✅ | ✅ | ✅ |
| Artefact Actions | ✅ après décompression | ❌ | ✅ | 📄 |
| Diff de PR | ❌ sur GitHub · ✅ sur GitLab | ✅ si GitLab interne | ❌ | 📄 |
