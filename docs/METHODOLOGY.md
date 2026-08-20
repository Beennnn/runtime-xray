# Méthodologie

Objectif : comparer des outils hétérogènes sur une base commune, sans biais de
familiarité, et **sans jamais présenter une lecture de documentation comme une mesure**.

## 1. Le cas d'usage de référence

Tous les outils analysent **la même exécution** : le programme
[`sample-app`](../sample-app/) et sa fonction
[`RoutePlanner.travelTimeMinutes(Trip)`](../sample-app/src/main/java/lab/sample/RoutePlanner.java).

L'exécution est :

- **déterministe** — ni horloge, ni aléatoire : deux exécutions produisent la même trace.
  Sans ça, comparer deux rapports reviendrait à comparer deux exécutions différentes, et
  l'écart observé ne dirait plus rien de l'outil ;
- **calibrée à ~10 secondes** — le minimum pour qu'un outil qui s'attache à un process
  vivant ait le temps d'être lancé et d'enregistrer ;
- **rejouée sous Java 21 puis Java 25**, les deux plateformes évaluées.

Le programme est conçu pour **départager** : appels conditionnés par le contexte, deux
chemins jamais empruntés, une récursion, un dispatch virtuel, une opération bloquante.
Détail dans [TECHNICAL.md](TECHNICAL.md).

## 2. Les cinq critères, dans l'ordre

L'ordre n'est pas décoratif : il tranche les cas où deux outils sont bons différemment.

1. **Facilité de mise en œuvre** — se mesure en nombre de gestes entre « rien » et « un
   rapport ». Un flag JVM bat une configuration XML, même si la seconde montre davantage.
2. **Lisibilité des rapports** — se mesure en répondant : *un non-développeur peut-il
   l'ouvrir seul et y comprendre quelque chose ?* Un fichier HTML autonome bat une
   application de bureau à installer.
3. **Couverture fonctionnelle** — les données du brief, notées séparément et **hiérarchisées** :
   lignes exécutées et arbre d'appel sont l'objectif ; les valeurs des paramètres sont une
   **option de seconde priorité**. Pas de moyenne : un outil qui fait deux tiers du travail
   ne vaut pas 66 %, il vaut « il en faut un second ». Et un outil qui excellerait sur
   l'option en étant faible sur les deux premières ne serait pas retenu.
4. **Intégration IDE** — IntelliJ en priorité, ou l'équivalent web : une page de code
   annoté où l'on navigue hors IDE.
5. **Coût** — départage seulement à capacités comparables, et intègre le coût caché
   (serveur à maintenir, configuration à écrire, licence à activer en ligne).

**Hors critères, mesuré en bonus** : temps de calcul et RAM. L'application les rapporte
elle-même, ce qui donne une référence indépendante pour estimer le surcoût d'un outil.

## 3. Trois modes d'intégration — et pourquoi ça compte

Ces outils **ne sont pas des dépendances Maven**. C'est la principale surprise structurelle
de l'exercice, et elle conditionne la façon d'ajouter un outil au dépôt.

| Mode | Exemples | Ce que ça implique |
|---|---|---|
| Agent JVM | JaCoCo, async-profiler, JProfiler, Glowroot | Un flag au lancement, rien à changer dans le code |
| Option native du JDK | JFR | Rien à installer du tout |
| Attachement à un process vivant | VisualVM, JMC, Arthas | La JVM doit tourner encore — d'où le calibrage à 10 s |
| Plugin de build | OpenClover, JaCoCo en mode tests | Ne mesure que ce que les tests couvrent — autre question |

## 4. Les statuts de vérification

Chaque affirmation porte son niveau de preuve. C'est la règle la plus importante du
document.

| Statut | Sens |
|---|---|
| ✅ **Testé ici** | Exécuté sur `sample-app`, sortie versionnée dans `reports-demo/generated/` |
| ⛔ **Bloqué par licence** | Exige une licence ou un essai activé par un humain |
| 🚧 **Bloqué techniquement** | Gratuit, mais non exécutable dans cet environnement — la raison est écrite |
| 📄 **Sur documentation** | Non exécuté ; fiche établie sur sources publiques, à confirmer |

Un chiffre issu d'un site tiers porte **sa date de relevé**. Un chiffre non vérifié est
signalé comme tel plutôt que présenté avec assurance.

## 5. La séparation sans licence / avec licence

Les outils sont rangés en deux catégories, jamais mélangées dans un même classement. La
frontière recoupe presque exactement la contrainte hors ligne et la question du budget :
comparer un outil gratuit et un outil à 549 $ sur une même colonne « note globale » ferait
disparaître la seule question qui compte — *qu'est-ce que l'argent achète exactement ?*

## 6. Neutralité

Chaque affirmation doit pouvoir être reliée à une source : une sortie versionnée dans ce
dépôt, ou une URL datée. Les jugements de valeur non étayés n'ont pas leur place — « riche »
ou « puissant » ne veulent rien dire tant qu'on n'a pas dit *sur quel critère*.

Quand une mesure contredit une attente, c'est la mesure qui gagne, et l'écart est écrit :
plusieurs constats de ce dépôt viennent de là — le traçage JFR absent en Java 21, les 129 Mo
d'enregistrement pour 10 s, le profil d'abord dominé par la fabrication du jeu de test.

## 7. Ce qui reste à faire

Voir [RESULTS.md](RESULTS.md#ce-qui-reste-à-faire-pour-trancher) — les actions humaines,
essais commerciaux en tête, sont listées là.
