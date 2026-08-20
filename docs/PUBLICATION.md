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

## 1. Les fichiers versionnés dans le dépôt — ✅ testé

Les rapports sont commis dans [`reports-demo/generated/`](../reports-demo/).

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
