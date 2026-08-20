# Glowroot

> **Statut : 📄 sur documentation** — non exécuté ici (exige de déployer son agent + serveur embarqué).

## Ce que c'est
Un APM (supervision applicative) open source : un agent JVM qui enregistre les
transactions et leurs traces, avec **son propre serveur web embarqué**.

## Sa philosophie
**Un APM sans plateforme.** Les APM du marché supposent un service distant ; Glowroot
embarque son interface et son stockage dans l'agent. On lance l'application, on ouvre un
navigateur sur l'application elle-même. **Ce choix le rend compatible hors ligne**, là où
Datadog ou New Relic sont éliminés d'office.

## Ce qu'il sait faire
Traces par transaction avec arbre d'appel ✅ · temps de réponse, percentiles ✅ ·
requêtes SQL ✅ · lignes exécutées ❌ · valeurs des paramètres ⚠️ limité et configurable.

## Son interface
**Interface web complète et soignée** — la plus « présentable » des solutions gratuites
recensées, pensée pour être regardée au quotidien.

## Comment on navigue dedans
Navigateur ✅, sans installation côté lecteur (une URL suffit). Pas d'IDE, pas de plateforme.

## Mise en œuvre
Un `-javaagent`, puis un port à ouvrir. Plus lourd qu'un flag isolé : il faut faire tourner
un service.

## Licence et coût
**Apache 2.0**, **0 €**.

## Ce qu'on peut en espérer
Le bon choix **si le besoin glisse du diagnostic ponctuel vers un suivi dans le temps** —
c'est précisément la question n° 4 des arbitrages. Pour une analyse ponctuelle, faire
tourner un serveur pour lire un arbre d'appel est disproportionné face à un HTML autonome.
