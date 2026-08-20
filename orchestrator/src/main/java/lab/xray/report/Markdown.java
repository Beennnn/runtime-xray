package lab.xray.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Le même contenu, en Markdown.
 *
 * <p>La page HTML est le format de travail : elle se parcourt, elle se replie, elle se
 * cherche. Mais une forge affiche un fichier {@code .html} comme du <b>code source</b>, pas
 * comme une page — le lien est donc inutilisable là où le code est relu. Le Markdown, lui,
 * est rendu nativement par GitHub comme par GitLab, dépôt privé compris, sans Pages, sans
 * service tiers et sans réglage.
 *
 * <p>Ce n'est pas un second rapport : c'est le même, réduit à ce qui se lit sans
 * interaction. Tout ce qui suppose de cliquer — l'arbre dépliable, la navigation entre
 * appels, le code annoté ligne à ligne — reste dans la page.
 */
public final class Markdown {

    /** Au-delà, un tableau cesse d'informer et commence à faire défiler. */
    private static final int TOP = 12;

    private Markdown() {}

    @SuppressWarnings("unchecked")
    public static Path write(Path commonDir, List<Object> runs) throws IOException {
        StringBuilder md = new StringBuilder();
        md.append("# Rapport d'analyse dynamique\n\n")
          .append("> Produit depuis les sorties machine de JaCoCo, async-profiler et Arthas.\n")
          .append("> Le même contenu, navigable, est dans `index.html` à côté de ce fichier :\n")
          .append("> une forge affiche un `.html` comme du code source, d'où cette version.\n");

        for (Object o : runs) {
            Map<String, Object> run = (Map<String, Object>) o;
            md.append("\n---\n\n## ").append(run.get("nom")).append("\n");
            contexte(md, (Map<String, Object>) run.get("context"));
            couverture(md, (Map<String, Object>) run.get("packages"));
            temps(md, (Map<String, Object>) run.get("calltree"));
            valeurs(md, (Map<String, Object>) run.get("values"));
        }

        Path out = commonDir.resolve("rapport.md");
        Files.writeString(out, md.toString(), StandardCharsets.UTF_8);
        return out;
    }

    /** Un rapport retrouvé dans six mois doit dire de quoi il parle. */
    private static void contexte(StringBuilder md, Map<String, Object> ctx) {
        if (ctx == null || ctx.isEmpty()) return;
        md.append('\n');
        ligne(md, "Commande", ctx.get("commande"));
        ligne(md, "Méthode racine", ctx.get("methodeRacine"));
        ligne(md, "Paquets masqués", ctx.get("paquetsMasques"));
        ligne(md, "Début", ctx.get("debut"));
        ligne(md, "Durée", ctx.get("dureeSecondes") == null ? null : ctx.get("dureeSecondes") + " s");
        ligne(md, "Java", ctx.get("java"));
        ligne(md, "Machine", ctx.get("systeme"));
    }

    private static void ligne(StringBuilder md, String cle, Object valeur) {
        if (valeur == null || String.valueOf(valeur).isBlank()) return;
        md.append("- **").append(cle).append("** : `").append(valeur).append("`\n");
    }

    @SuppressWarnings("unchecked")
    private static void couverture(StringBuilder md, Map<String, Object> packages) {
        if (packages == null || packages.isEmpty()) return;
        long couvert = 0, manque = 0;
        int paquetsIntacts = 0;
        List<String[]> lignes = new ArrayList<>();
        List<String> mortes = new ArrayList<>();

        for (Map.Entry<String, Object> e : packages.entrySet()) {
            long c = 0, m = 0;
            for (Object x : (List<Object>) e.getValue()) {
                Map<String, Object> cls = (Map<String, Object>) x;
                long cc = ((Number) cls.get("covered")).longValue();
                long mm = ((Number) cls.get("missed")).longValue();
                c += cc; m += mm;
                if (cc == 0) mortes.add(String.valueOf(cls.get("name")).replace('/', '.'));
            }
            couvert += c; manque += m;
            // Un paquet entièrement à zéro ne mérite pas une ligne de tableau : il en
            // faudrait dix-huit pour dire « rien », et elles noieraient les huit qui
            // disent quelque chose. Le compte suffit, et il est donné juste après.
            if (c > 0) {
                lignes.add(new String[]{ e.getKey().replace('/', '.'), pct(c, c + m) });
            } else if (c + m > 0) {
                paquetsIntacts++;
            }
        }

        md.append("\n### Par où le code est passé\n\n")
          .append("**").append(pct(couvert, couvert + manque))
          .append(" des instructions analysées** ont été exécutées. Le dénominateur est ")
          .append("tout le bytecode donné à analyser : si l'analyse porte sur une ")
          .append("dépendance entière, la plus grande partie n'a par construction aucune ")
          .append("raison de tourner, et ce taux dit alors surtout la taille de cette ")
          .append("dépendance.\n\n")
          .append("| Paquet | Instructions couvertes |\n|---|---:|\n");
        lignes.sort(Comparator.comparing(a -> a[0]));
        for (String[] l : lignes) {
            md.append("| `").append(l[0]).append("` | ").append(l[1]).append(" |\n");
        }
        if (paquetsIntacts > 0) {
            md.append("\n").append(paquetsIntacts)
              .append(" paquet").append(paquetsIntacts > 1 ? "s" : "")
              .append(" n'").append(paquetsIntacts > 1 ? "ont" : "a")
              .append(" pas été touché").append(paquetsIntacts > 1 ? "s" : "")
              .append(" du tout.\n");
        }
        if (!mortes.isEmpty()) {
            md.append("\n**Jamais exécuté** (").append(mortes.size()).append(") — ");
            // Le code mort est ce que la lecture seule ne révèle pas : il est nommé, pas compté.
            md.append(String.join(", ", mortes.stream().sorted().limit(TOP)
                    .map(s -> "`" + s + "`").toList()));
            if (mortes.size() > TOP) md.append(", … (+").append(mortes.size() - TOP).append(")");
            md.append('\n');
        }
    }

    @SuppressWarnings("unchecked")
    private static void temps(StringBuilder md, Map<String, Object> tree) {
        if (tree == null) return;
        long total = ((Number) tree.getOrDefault("total", 0L)).longValue();
        if (total == 0) return;

        // Le poids d'une méthode est la somme de ses apparitions dans l'arbre, où qu'elle
        // soit appelée : c'est « le temps passé dans cette méthode », pas « dans ce chemin ».
        Map<String, Long> poids = new LinkedHashMap<>();
        accumuler(tree, poids);

        md.append("\n### Où le temps est passé\n\n")
          .append(total).append(" relevés. Le repli du JDK, des rouages de la machine ")
          .append("virtuelle et des paquets masqués rattache leur temps à la méthode ")
          .append("applicative qui les a appelés.\n\n")
          .append("| Méthode | Part |\n|---|---:|\n");
        poids.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(TOP)
                .forEach(e -> md.append("| `").append(e.getKey().replace('/', '.'))
                        .append("` | ").append(pct(e.getValue(), total)).append(" |\n"));
    }

    @SuppressWarnings("unchecked")
    private static void accumuler(Map<String, Object> node, Map<String, Long> poids) {
        for (Object o : (List<Object>) node.getOrDefault("children", List.of())) {
            Map<String, Object> kid = (Map<String, Object>) o;
            poids.merge(String.valueOf(kid.get("name")),
                    ((Number) kid.get("total")).longValue(), Long::sum);
            accumuler(kid, poids);
        }
    }

    @SuppressWarnings("unchecked")
    private static void valeurs(StringBuilder md, Map<String, Object> values) {
        if (values == null || values.isEmpty()) return;
        md.append("\n### Avec quelles valeurs\n");
        for (Map.Entry<String, Object> e : values.entrySet()) {
            List<Object> appels = (List<Object>) e.getValue();
            if (appels.isEmpty()) continue;
            md.append("\n**`").append(e.getKey().replace('/', '.')).append("`** — ")
              .append(appels.size()).append(" appel").append(appels.size() > 1 ? "s" : "")
              .append(" observé").append(appels.size() > 1 ? "s" : "").append("\n\n");
            md.append("| # | Paramètres reçus | Valeur renvoyée |\n|---:|---|---|\n");
            int i = 1;
            for (Object a : appels) {
                Map<String, Object> appel = (Map<String, Object>) a;
                List<Object> params = (List<Object>) appel.getOrDefault("params", List.of());
                String recu = params.isEmpty() ? "—" : params.stream()
                        .map(p -> String.valueOf(((Map<String, Object>) p).get("value")))
                        .map(Markdown::cellule)
                        .reduce((x, y) -> x + ", " + y).orElse("—");
                Map<String, Object> retour = (Map<String, Object>) appel.get("retour");
                md.append("| ").append(i++).append(" | ").append(recu).append(" | ")
                  .append(retour == null ? "—" : cellule(String.valueOf(retour.get("value"))))
                  .append(" |\n");
            }
        }
    }

    /**
     * Une valeur dans une cellule de tableau Markdown.
     *
     * <p>Un {@code |} non échappé y couperait la colonne en deux, et un retour à la ligne
     * la ligne entière — les valeurs capturées viennent d'une application quelconque, elles
     * peuvent contenir n'importe quoi.
     */
    private static String cellule(String v) {
        String s = v.replace("|", "\\|").replace("\n", " ").trim();
        return "`" + (s.length() > 90 ? s.substring(0, 87) + "…" : s) + "`";
    }

    private static String pct(long part, long total) {
        return total == 0 ? "—" : Math.round(100.0 * part / total) + " %";
    }
}
