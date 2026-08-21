# Reprendre le résultat dans un autre outil

> La page produite par `runtime-xray` est **une** façon de lire une exécution, pas la seule.
> Les mêmes mesures se réécrivent dans des formats publics que d'autres outils ouvrent :
> Firefox Profiler, speedscope, un éditeur, un script.

## Le besoin, énoncé une fois

Une mesure d'exécution est une **donnée**, pas un rendu. Qui la prend doit pouvoir la
regarder avec l'outil qu'il connaît déjà, la comparer à celle d'hier, l'archiver plus
longtemps que la version du programme qui l'a produite, et la transmettre à quelqu'un qui
n'utilise pas le nôtre.

C'est exactement le reproche que [le comparatif](../etude/comparatif.md) adresse aux outils
fermés : la mesure y est prisonnière du rendu. Un outil libre qui referait la même chose,
avec sa propre page pour seule sortie, n'aurait rien réglé.

D'où la règle tenue ici :

> **Tout ce qui est mesuré doit pouvoir sortir dans un format ouvert, documenté, et lisible
> par au moins un outil que nous n'écrivons pas.**

Deux conséquences pratiques :

- **On exporte la mesure, pas la synthèse.** Les replis de la page — le JDK rangé sous son
  appelant, les paquets masqués, les agrégations — ne s'appliquent pas aux fichiers
  exportés. Ce qu'un tiers ouvre est ce que l'outil d'observation a écrit, dans une autre
  grammaire.
- **Aucun format inventé quand il en existe un.** Un format maison ne se justifie que là où
  rien d'établi ne couvre l'information — c'est le cas des valeurs de paramètres, et
  seulement de celui-là.

## Produire les exports

```bash
java -jar runtime-xray.jar --report-only --out runtime-xray-out --export tout
```

`--export` accepte une liste (`--export cpuprofile,lcov`) ou `tout`. L'option marche
pendant une mesure comme après coup, avec `--report-only` : les exports se recalculent
depuis les fichiers déjà écrits, sans relancer l'application.

Les fichiers sont déposés dans chaque exécution :

```
runs/20260821-004121-recette/
└── exports/
    ├── profil.perf.txt      ← profil, format perf script
    ├── profil.cpuprofile    ← profil, format Chrome DevTools
    ├── couverture.lcov      ← couverture, format LCOV
    └── valeurs.json         ← valeurs capturées
```

La page les nomme à deux endroits : dans la **barre du haut**, sous *exports*, où ils
restent visibles mais éteints tant qu'ils n'ont pas été produits — un clic donne alors la
commande — et dans la liste des sorties brutes, sous *« Pour un autre outil »*.

## Les formats retenus

| Fichier | Format | Ce qu'il porte | Ce qui l'ouvre |
|---|---|---|---|
| `profil.perf.txt` | Sortie texte de `perf script` (Linux) | Les piles échantillonnées, un bloc par relevé | Firefox Profiler, `perf report`, les convertisseurs de la famille FlameGraph |
| `profil.cpuprofile` | CPU profile de Chrome DevTools | Les mêmes piles, partagées dans un arbre | speedscope, les outils de développement des navigateurs |
| `couverture.lcov` | LCOV | Lignes, branches et méthodes couvertes | `genhtml`, Coverage Gutters (VS Code), la plupart des services de suivi |
| `valeurs.json` | JSON décrit ci-dessous | Les appels observés, leurs paramètres et leurs retours | un script — voir la structure plus bas |

### Ce qu'on en fait

**Firefox Profiler** — ouvrir [profiler.firefox.com](https://profiler.firefox.com), bouton
*Load a profile from file*, choisir `profil.perf.txt`. C'est le format d'import documenté
pour les profils Linux.

**speedscope** — ouvrir [speedscope.app](https://www.speedscope.app) et y déposer
`profil.cpuprofile`. Le fichier `async-profiler/profil.collapsed`, écrit par la mesure
elle-même, s'y ouvre tout aussi bien : c'est le format des piles repliées.

**Couverture dans l'éditeur** — sous VS Code, l'extension *Coverage Gutters* lit
`couverture.lcov` et colorie les lignes dans l'éditeur. En ligne de commande :

```bash
genhtml couverture.lcov -o couverture-html   # le rapport HTML de lcov
lcov --summary couverture.lcov               # juste les totaux
```

**Comparer deux exécutions par script** — `valeurs.json` porte, par méthode observée, la
liste des appels avec leurs paramètres et leur retour :

```json
{
  "valeurs": {
    "lab/sample/RoutePlanner.legMinutes": [
      {
        "ts": "2026-08-21 00:41:34.018",
        "cost": "0.023709ms",
        "params": [
          { "type": "Leg", "value": "Leg[from=Albi, to=Montauban, distanceKm=57.0]" },
          { "type": "Mode", "value": "WALK" }
        ],
        "retour": { "type": "Double", "value": "717.54336" }
      }
    ]
  },
  "trace": { "lab/sample/RoutePlanner.legMinutes": [] }
}
```

Une entrée par appel observé : l'horodatage, le coût mesuré par l'outil de capture, les
paramètres reçus dans l'ordre de la signature, et le retour. `trace` porte, pour les mêmes
méthodes, les arbres d'appel relevés invocation par invocation.

## Les formats ouverts connus, et pourquoi ceux-là

Le recensement ci-dessous suit la méthode du reste de l'étude : ce qui a été retenu, ce qui
ne l'a pas été, et le motif.

| Format | Ce qu'il couvre | Décision |
|---|---|---|
| **`perf script`** (texte) | Piles échantillonnées | **Retenu.** Format d'import de Firefox Profiler, lisible à l'œil nu, sans dépendance |
| **Chrome `.cpuprofile`** | Piles échantillonnées | **Retenu.** Compact — un profil d'une minute tient en quelques centaines de Ko là où le texte en fait des dizaines de Mo |
| **Piles repliées** (`.collapsed`) | Piles échantillonnées | **Déjà produit** par async-profiler, laissé tel quel. C'est l'entrée de speedscope et de FlameGraph |
| **LCOV** | Couverture | **Retenu.** Le format de couverture le plus largement accepté |
| **JaCoCo XML / CSV** | Couverture | **Déjà produit** par JaCoCo, laissé tel quel |
| **Format Gecko** (JSON Firefox) | Profil complet, avec fils et marqueurs | **Écarté.** Format natif de Firefox Profiler, plus riche que ce qu'on mesure — l'import `perf` couvre le besoin sans l'écrire |
| **`pprof`** (protobuf, Google) | Profils de toute nature | **Écarté.** Demanderait une dépendance protobuf, contre la règle du jar sans dépendance |
| **Chrome Trace Event** (JSON) | Traces d'événements datés | **Écarté.** Il décrit des intervalles début/fin ; nos mesures sont des échantillons. L'y couler supposerait des durées qu'on n'a pas mesurées |
| **JFR** (`.jfr`) | Événements JVM | **Écarté ici.** Format d'enregistrement de la JVM, pas d'échange de résultat — et [sa fiche](../etude/fiches/jfr-jmc.md) explique pourquoi il ne répond pas aux trois questions |
| **OTLP / OpenTelemetry** | Traces réparties | **Écarté.** Conçu pour un système en production, pas pour une exécution locale — voir [sa fiche](../etude/fiches/opentelemetry.md) |
| **Cobertura XML** | Couverture | **Écarté.** LCOV couvre les mêmes usages et bien plus d'outils. À reconsidérer si une forge l'impose |
| **Couverture générique SonarQube** | Couverture | **Écarté.** Propre à un produit ; SonarQube lit déjà le XML JaCoCo |

## Réserves

Les points ci-dessous sont des limites connues, pas des défauts à découvrir.

- **Le format `perf` est volumineux.** Un relevé pesant *n* donne *n* blocs : un profil de
  82 000 relevés produit environ 55 Mo de texte, là où le `.cpuprofile` équivalent en fait
  moins d'un. Au-delà de 4 millions d'échantillons, l'écriture s'arrête et le dit sur la
  sortie standard — un fichier tronqué en silence serait pire.
- **Les adresses mémoire sont nulles** dans l'export `perf`. Elles n'ont aucun sens pour du
  code compilé à la volée, et aucun lecteur ne s'en sert dès lors que le symbole est là.
- **LCOV compte 0 ou 1 passage par ligne.** JaCoCo dit *si* une ligne a été exécutée, jamais
  *combien de fois* : le compteur ne peut pas dire autre chose sans inventer des passages.
- **`valeurs.json` n'est pas un format d'échange établi.** Il n'en existe pas pour cette
  information ; sa structure est décrite ici et suit celle que lit la page.
- **L'ouverture dans les outils tiers n'a pas été rejouée dans l'environnement où ces
  exports ont été écrits** — l'accès réseau y est fermé. Les formats suivent leur
  spécification publique et sont vérifiés à l'écriture (ordre des frames, cohérence des
  renvois, totaux LCOV) par les tests de `ExportsTest`. Statut, au sens du
  [comparatif](../etude/comparatif.md) : **conforme à la documentation, import non rejoué**.

## À lire ensuite

- [Réduire l'empreinte sur un gros code](empreinte.md) — mesurer moins, mais mesurer quand même
- [Mode d'emploi](mode-emploi.md) — tous les paramètres
- [Formats et découplage](../resultat/formats.md) — pourquoi la collecte et l'affichage sont séparés
