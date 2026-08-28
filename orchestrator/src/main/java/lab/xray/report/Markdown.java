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
        md.append("# Runtime analysis report\n\n")
          .append("> Produced from the machine outputs of JaCoCo, async-profiler and Arthas.\n")
          .append("> The same content, navigable, is in `index.html` beside this file:\n")
          .append("> a forge shows an `.html` as source code, hence this version.\n");

        for (Object o : runs) {
            Map<String, Object> run = (Map<String, Object>) o;
            md.append("\n---\n\n## ").append(run.get("nom")).append("\n");
            context(md, (Map<String, Object>) run.get("context"));
            coverage(md, (Map<String, Object>) run.get("packages"));
            time(md, (Map<String, Object>) run.get("calltree"));
            values(md, (Map<String, Object>) run.get("values"));
        }

        Path out = commonDir.resolve("rapport.md");
        Files.writeString(out, md.toString(), StandardCharsets.UTF_8);
        return out;
    }

    /** Un rapport retrouvé dans six mois doit dire de quoi il parle. */
    private static void context(StringBuilder md, Map<String, Object> ctx) {
        if (ctx == null || ctx.isEmpty()) return;
        md.append('\n');
        line(md, "Command", ctx.get("commande"));
        line(md, "Root method", ctx.get("methodeRacine"));
        line(md, "Hidden packages", ctx.get("paquetsMasques"));
        line(md, "Start", ctx.get("debut"));
        line(md, "Duration", ctx.get("dureeSecondes") == null ? null : ctx.get("dureeSecondes") + " s");
        line(md, "Java", ctx.get("java"));
        line(md, "Machine", ctx.get("systeme"));
    }

    private static void line(StringBuilder md, String key, Object value) {
        if (value == null || String.valueOf(value).isBlank()) return;
        md.append("- **").append(key).append("**: `").append(value).append("`\n");
    }

    @SuppressWarnings("unchecked")
    private static void coverage(StringBuilder md, Map<String, Object> packages) {
        if (packages == null || packages.isEmpty()) return;
        long covered = 0, missing = 0;
        int untouchedPackages = 0;
        List<String[]> lines = new ArrayList<>();
        List<String> dead = new ArrayList<>();

        for (Map.Entry<String, Object> e : packages.entrySet()) {
            long c = 0, m = 0;
            for (Object x : (List<Object>) e.getValue()) {
                Map<String, Object> cls = (Map<String, Object>) x;
                long cc = ((Number) cls.get("covered")).longValue();
                long mm = ((Number) cls.get("missed")).longValue();
                c += cc; m += mm;
                if (cc == 0) dead.add(String.valueOf(cls.get("name")).replace('/', '.'));
            }
            covered += c; missing += m;
            // Un paquet entièrement à zéro ne mérite pas une ligne de tableau : il en
            // faudrait dix-huit pour dire « rien », et elles noieraient les huit qui
            // disent quelque chose. Le compte suffit, et il est donné juste après.
            if (c > 0) {
                lines.add(new String[]{ e.getKey().replace('/', '.'), pct(c, c + m) });
            } else if (c + m > 0) {
                untouchedPackages++;
            }
        }

        md.append("\n### Where the code went\n\n")
          .append("**").append(pct(covered, covered + missing))
          .append(" of the analysed instructions** ran. The denominator is all the bytecode "
                  + "given to be analysed: if the analysis covers a whole dependency, most of "
                  + "it has by construction no reason to run, and this rate then says mostly "
                  + "how big that dependency is.\n\n")
          .append("| Package | Instructions covered |\n|---|---:|\n");
        lines.sort(Comparator.comparing(a -> a[0]));
        for (String[] l : lines) {
            md.append("| `").append(l[0]).append("` | ").append(l[1]).append(" |\n");
        }
        if (untouchedPackages > 0) {
            md.append("\n").append(untouchedPackages)
              .append(" package").append(untouchedPackages > 1 ? "s were" : " was")
              .append(" not touched at all.\n");
        }
        if (!dead.isEmpty()) {
            md.append("\n**Never executed** (").append(dead.size()).append(") — ");
            // Le code mort est ce que la lecture seule ne révèle pas : il est nommé, pas compté.
            md.append(String.join(", ", dead.stream().sorted().limit(TOP)
                    .map(s -> "`" + s + "`").toList()));
            if (dead.size() > TOP) md.append(", … (+").append(dead.size() - TOP).append(")");
            md.append('\n');
        }
    }

    @SuppressWarnings("unchecked")
    private static void time(StringBuilder md, Map<String, Object> tree) {
        if (tree == null) return;
        long total = ((Number) tree.getOrDefault("total", 0L)).longValue();
        if (total == 0) return;

        // Le poids d'une méthode est la somme de ses apparitions dans l'arbre, où qu'elle
        // soit appelée : c'est « le temps passé dans cette méthode », pas « dans ce chemin ».
        Map<String, Long> weight = new LinkedHashMap<>();
        accumulate(tree, weight);

        md.append("\n### Where the time went\n\n")
          .append(total).append(" samples. Folding the JDK, the virtual machine's own "
                  + "machinery and the hidden packages attributes their time to the "
                  + "application method that called them.\n\n")
          .append("| Method | Share |\n|---|---:|\n");
        weight.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(TOP)
                .forEach(e -> md.append("| `").append(e.getKey().replace('/', '.'))
                        .append("` | ").append(pct(e.getValue(), total)).append(" |\n"));
    }

    @SuppressWarnings("unchecked")
    private static void accumulate(Map<String, Object> node, Map<String, Long> weight) {
        for (Object o : (List<Object>) node.getOrDefault("children", List.of())) {
            Map<String, Object> kid = (Map<String, Object>) o;
            weight.merge(String.valueOf(kid.get("name")),
                    ((Number) kid.get("total")).longValue(), Long::sum);
            accumulate(kid, weight);
        }
    }

    @SuppressWarnings("unchecked")
    private static void values(StringBuilder md, Map<String, Object> values) {
        if (values == null || values.isEmpty()) return;
        md.append("\n### With which values\n");
        for (Map.Entry<String, Object> e : values.entrySet()) {
            List<Object> calls = (List<Object>) e.getValue();
            if (calls.isEmpty()) continue;
            md.append("\n**`").append(e.getKey().replace('/', '.')).append("`** — ")
              .append(calls.size()).append(" call").append(calls.size() > 1 ? "s" : "")
              .append(" observed\n\n");
            md.append("| # | Arguments received | Value returned |\n|---:|---|---|\n");
            int i = 1;
            for (Object a : calls) {
                Map<String, Object> call = (Map<String, Object>) a;
                List<Object> params = (List<Object>) call.getOrDefault("params", List.of());
                String received = params.isEmpty() ? "—" : params.stream()
                        .map(p -> String.valueOf(((Map<String, Object>) p).get("value")))
                        .map(Markdown::cell)
                        .reduce((x, y) -> x + ", " + y).orElse("—");
                Map<String, Object> returnValue = (Map<String, Object>) call.get("retour");
                md.append("| ").append(i++).append(" | ").append(received).append(" | ")
                  .append(returnValue == null ? "—" : cell(String.valueOf(returnValue.get("value"))))
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
    private static String cell(String v) {
        String s = v.replace("|", "\\|").replace("\n", " ").trim();
        return "`" + (s.length() > 90 ? s.substring(0, 87) + "…" : s) + "`";
    }

    private static String pct(long share, long total) {
        return total == 0 ? "—" : Math.round(100.0 * share / total) + "%";
    }
}
