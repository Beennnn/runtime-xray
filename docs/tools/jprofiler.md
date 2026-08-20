# JProfiler

> **Statut : ⛔ bloqué par licence** — essai de 10 jours à activer manuellement. Non testé ici.

## Ce que c'est
Un profileur Java commercial (ej-technologies), référence du marché avec YourKit.

## Sa philosophie
**Tout dans un seul outil, avec zéro configuration.** L'argument de vente est l'immédiateté :
on attache, on voit. Là où le monde gratuit demande d'assembler trois outils, JProfiler vise
la réponse unique — et le fait payer.

## Ce qu'il sait faire
| Capacité | |
|---|---|
| Arbre d'appel | ✅ très riche |
| **Valeurs des paramètres** | ✅ — *method splitting by parameter values* : le call tree se **scinde par valeur d'argument**, une branche par valeur observée |
| Mémoire, verrous, JDBC | ✅ |
| Lignes exécutées | ❌ |

> Le *method splitting* mérite qu'on s'y arrête : c'est exactement la réponse à ce que
> `sample-app` a été conçue pour montrer — un même appel qui part dans des sous-arbres
> différents **selon la valeur du paramètre**. Là où les autres montrent un cumul,
> JProfiler montre un arbre par valeur. *(Revendiqué par la documentation, non vérifié ici.)*

## Son interface
Application de bureau riche, **plus un plugin IntelliJ natif** — l'un des rares outils qui
répondent directement au critère n° 4.

## Comment on navigue dedans
Dans son interface ✅ · dans IntelliJ ✅ · export HTML/CSV des instantanés ⚠️ moins direct
qu'un rapport pensé pour être partagé · pas d'intégration plateforme.

## Mise en œuvre
Annoncée sans configuration (attachement distant inclus). **À vérifier en essai** — c'est
l'objet de l'action n° 1 des [arbitrages](../RESULTS.md).

## Licence et coût
Commerciale. ~500 $ la licence standard, ~200 $ en académique *(chiffres du brief,
non vérifiés sur la page tarifaire)*. Licence perpétuelle + maintenance.
Fonctionne hors ligne une fois licencié *(à confirmer : mode d'activation).*

## Ce qu'on peut en espérer
**Le candidat sérieux pour combler le seul trou du socle gratuit.** La question n'est pas
« JProfiler ou JaCoCo » — c'est « JProfiler **en plus de** JaCoCo, ou bien une piste
gratuite (Arthas, JMC Agent) plus rugueuse ? ». C'est l'arbitrage n° 1.
