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
Commerciale, **licence perpétuelle**. Prix relevés le 2026-08-20 sur la boutique de
l'éditeur :

| | Sans support | Avec 1 an de support et mises à jour |
|---|---|---|
| Licence simple | **549 $** | 768 $ |
| Licence flottante (1 utilisateur simultané) | **2 199 $** | 3 078 $ |

Licence **gratuite pour les projets open source**, sur demande à l'éditeur.

⚠️ **Hors ligne** : la clé se saisit localement, mais il faut **confirmer auprès de
l'éditeur qu'aucune activation en ligne n'est exigée** avant de compter sur l'outil en
environnement déconnecté. Question à poser en même temps que la demande d'essai.

Essai de 10 jours par formulaire — procédure détaillée dans
[EVALUATION-KEYS.md](../EVALUATION-KEYS.md#jprofiler-ej-technologies).

## Ce qu'on peut en espérer
**Le candidat sérieux pour combler le seul trou du socle gratuit.** La question n'est pas
« JProfiler ou JaCoCo » — c'est « JProfiler **en plus de** JaCoCo, ou bien une piste
gratuite (Arthas, JMC Agent) plus rugueuse ? ». C'est l'arbitrage n° 1.
