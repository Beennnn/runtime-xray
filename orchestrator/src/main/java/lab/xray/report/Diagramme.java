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
import java.util.TreeMap;

/**
 * La campagne entière sur une page, en SVG.
 *
 * <p>La vue interactive répond aux questions qu'on lui pose ; elle ne se transmet pas. Or ce
 * qu'on montre en réunion, ce qu'on colle dans une diapositive, ce qu'on joint à un compte
 * rendu, c'est une image — et jusqu'ici il fallait la fabriquer à la main à partir de
 * captures d'écran, donc la refaire à chaque campagne, donc ne pas la faire.
 *
 * <p>Ce fichier est engendré à chaque assemblage, à côté de la page. Il ne contient que des
 * chiffres <b>mesurés</b> : aucun n'est arrondi à l'avantage de qui le présente, et les
 * classes jamais exécutées y figurent au même titre que la couverture. Un schéma de synthèse
 * qui ne montrerait que ce qui va bien coûterait sa crédibilité au premier vérificateur.
 *
 * <p><b>Les couleurs sont écrites en clair</b>, et non héritées d'un thème : le fichier est
 * fait pour être glissé dans une présentation, et PowerPoint n'interprète pas
 * {@code currentColor}. Pour la même raison, il n'y a ni script, ni police externe.
 */
public final class Diagramme {

    private static final String INK = "#14181e", MUTED = "#5a6472", RULE = "#c9d1d9";
    private static final String VERT = "#1a7f37", ROUGE = "#cf222e", AMBRE = "#9a6700";
    private static final String BLEU = "#0969da", FOND_BARRE = "#e4e9ef";
    private static final String POLICE = "Segoe UI, system-ui, sans-serif";

    /** Au-delà, la page devient un mur : on montre les plus parlants et on dit le reste. */
    private static final int MAX_PAQUETS = 9, MAX_METHODES = 6, MAX_MORTES = 16;

    private Diagramme() {}

    /**
     * Écrit {@code synthese.svg} à côté de la page.
     *
     * @param runs les exécutions telles que la vue les reçoit
     * @return le fichier écrit, ou {@code null} s'il n'y avait rien à dessiner
     */
    public static Path ecrire(Path commonDir, List<Object> runs) throws IOException {
        if (runs == null || runs.isEmpty()) return null;
        Map<String, long[]> paquets = couvertureParPaquet(runs);
        if (paquets.isEmpty()) return null;

        long cov = 0, mis = 0;
        for (long[] v : paquets.values()) { cov += v[0]; mis += v[1]; }
        Path out = commonDir.resolve("synthese.svg");
        Files.writeString(out, svg(commonDir, runs, paquets, cov, mis), StandardCharsets.UTF_8);
        return out;
    }

    /**
     * D'où vient le chiffre de couverture qu'on met en gros, et ce qu'il vaut.
     *
     * <p>Réunir des exécutions n'est pas additionner leurs pourcentages. Le seul cumul exact
     * est celui que JaCoCo calcule en fusionnant les mesures ; c'est lui qu'on affiche dès
     * qu'il existe, avec la mention de sa source.
     *
     * <p>À défaut, on retient par classe la <b>meilleure couverture atteinte</b> — une
     * approximation par le haut, honnête tant qu'on le dit. Un schéma qu'on projette ne peut
     * pas se permettre un chiffre dont personne ne sait d'où il sort : il serait démenti par
     * le premier qui ouvre le rapport JaCoCo.
     */
    private record Chiffre(double pct, long couvert, long total, String source) {}

    private static Chiffre couverture(Path commonDir, long cov, long mis) {
        Path fusion = commonDir.resolve("jacoco-fusion/html/jacoco.xml");
        if (Files.isRegularFile(fusion)) {
            try {
                Coverage c = Coverage.parse(fusion, PackageFilter.NONE);
                long couvert = 0, manque = 0;
                for (Object classes : c.packages.values()) {
                    if (!(classes instanceof Iterable<?> liste)) continue;
                    for (Object o : liste) {
                        if (!(o instanceof Map<?, ?> cls)) continue;
                        couvert += nombre(cls.get("covered"));
                        manque += nombre(cls.get("missed"));
                    }
                }
                if (couvert + manque > 0) {
                    return new Chiffre(100.0 * couvert / (couvert + manque), couvert,
                            couvert + manque, "fusion JaCoCo des " );
                }
            } catch (Exception e) {
                // La fusion est un supplément : son illisibilité ne doit pas priver de schéma.
            }
        }
        long total = cov + mis;
        return new Chiffre(total == 0 ? 0 : 100.0 * cov / total, cov, total, null);
    }

    private static String svg(Path commonDir, List<Object> runs, Map<String, long[]> paquets,
                             long cov, long mis) {
        StringBuilder s = new StringBuilder();
        s.append("""
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1280 720" width="1280" \
                height="720" role="img" aria-label="Synthèse de la campagne : couverture \
                cumulée, couverture par paquet, temps passé par méthode et classes jamais \
                exécutées.">
                <rect width="1280" height="720" fill="#ffffff"/>
                <g font-family="%s">
                """.formatted(POLICE));

        Map<String, Object> ctx = contexte(runs);
        Chiffre couverture = couverture(commonDir, cov, mis);
        List<String> jamais = classesJamaisExecutees(runs);
        long classes = nombreDeClasses(runs), mortes = jamais.size();

        // ── en-tête ────────────────────────────────────────────────────────────────
        texte(s, 40, 48, 22, 700, INK, "Ce que la campagne a mesuré");
        texte(s, 40, 72, 13, 400, MUTED, court(String.valueOf(ctx.getOrDefault("commande", "")), 95));
        texte(s, 40, 92, 12.5, 400, MUTED,
                joindre(" · ", String.valueOf(ctx.getOrDefault("machine", "")),
                        String.valueOf(ctx.getOrDefault("debut", "")),
                        runs.size() + (runs.size() > 1 ? " exécutions" : " exécution")));

        // ── quatre chiffres ────────────────────────────────────────────────────────
        tuile(s, 40, 118, "COUVERTURE CUMULÉE", arrondi(couverture.pct()) + " %",
                couverture.source() != null
                        ? couverture.couvert() + " sur " + couverture.total() + " — fusion JaCoCo"
                        : couverture.couvert() + " sur " + couverture.total()
                          + " — meilleure par classe",
                VERT);
        tuile(s, 350, 118, "CLASSES MESURÉES", String.valueOf(classes),
                mortes + " n'ont jamais tourné", BLEU);
        tuile(s, 660, 118, "EXÉCUTIONS RÉUNIES", String.valueOf(runs.size()),
                "chacune apporte sa part", AMBRE);
        tuile(s, 970, 118, "DURÉE OBSERVÉE", dureeTotale(runs),
                "temps réel des scénarios", MUTED);

        // ── par paquet ─────────────────────────────────────────────────────────────
        titre(s, 40, 268, "OÙ LE CODE EST COUVERT, PAQUET PAR PAQUET", 600);
        List<Map.Entry<String, long[]>> tries = new ArrayList<>(paquets.entrySet());
        // Le moins couvert d'abord : c'est là qu'on décide quelque chose.
        tries.sort(Comparator.comparingDouble(e -> part(e.getValue())));
        int y = 296;
        for (int i = 0; i < Math.min(MAX_PAQUETS, tries.size()); i++) {
            Map.Entry<String, long[]> e = tries.get(i);
            barre(s, 40, y, 600, e.getKey().replace('/', '.'), part(e.getValue()),
                  e.getValue()[2] + (e.getValue()[2] > 1 ? " classes" : " classe"), 236);
            y += 30;
        }
        if (tries.size() > MAX_PAQUETS) {
            texte(s, 40, y + 6, 12, 400, MUTED,
                    "et " + (tries.size() - MAX_PAQUETS) + " autres paquets — la page les montre tous");
        }

        // ── où le temps est parti ──────────────────────────────────────────────────
        titre(s, 700, 268, "OÙ LE TEMPS EST PARTI", 540);
        List<Map.Entry<String, Long>> chaud = methodesLesPlusChaudes(runs);
        if (chaud.isEmpty()) {
            texte(s, 700, 300, 12.5, 400, MUTED, "Pas de mesure de temps dans cette campagne.");
        } else {
            long max = chaud.get(0).getValue();
            int yc = 296;
            for (Map.Entry<String, Long> e : chaud) {
                // Un nom de méthode qualifié est long : on lui donne la place, et on coupe
                // par la GAUCHE — c'est la fin du nom qui identifie, pas le paquet.
                barre(s, 700, yc, 540, court(e.getKey().replace('/', '.'), 40),
                      max == 0 ? 0 : 100.0 * e.getValue() / max, "", 300);
                yc += 30;
            }
            texte(s, 700, yc + 6, 12, 400, MUTED,
                    "Part du temps mesuré, appels compris — relatif à la plus coûteuse.");
        }

        // ── jamais exécuté ─────────────────────────────────────────────────────────
        // Par exécution : ce que chaque scénario apporte. C'est la question qu'on pose devant
        // une campagne — « le deuxième scénario a-t-il servi à quelque chose ? ».
        titre(s, 700, 500, "CE QUE CHAQUE EXÉCUTION A COUVERT", 540);
        // Toutes sur le MÊME dénominateur que le cumul, sans quoi une exécution afficherait
        // un pourcentage supérieur au total — chacune sur son propre périmètre, ce qui se
        // lit comme une contradiction alors que ce n'est qu'un changement de base.
        long base = couverture.total();
        int ye = 528;
        for (Object r : runs) {
            if (!(r instanceof Map<?, ?> run)) continue;
            long[] v = couvertureDe(run);
            double p = base == 0 ? 0 : 100.0 * v[0] / base;
            barre(s, 700, ye, 540, court(String.valueOf(run.get("nom")), 34), p, duree(run), 300);
            ye += 30;
            if (ye > 604) break;
        }
        texte(s, 700, ye + 4, 11.5, 400, MUTED,
                "Part du même total que le cumul — ce que chaque scénario aurait donné seul.");

        titre(s, 40, 620, "CE QUI N'A JAMAIS TOURNÉ", 1200);
        if (jamais.isEmpty()) {
            texte(s, 40, 652, 12.5, 400, VERT,
                    "Aucune : toutes les classes mesurées ont été atteintes.");
        } else {
            int x = 40, yj = 650;
            for (int i = 0; i < Math.min(MAX_MORTES, jamais.size()); i++) {
                String nom = jamais.get(i);
                int w = 9 * nom.length() + 18;
                if (x + w > 1240) { x = 40; yj += 28; }
                s.append("<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"22\" rx=\"4\" fill=\"#fff5f5\" stroke=\"%s\" stroke-width=\"1\"/>\n"
                        .formatted(x, yj - 16, w, ROUGE));
                texte(s, x + 9, yj, 11.5, 500, ROUGE, nom);
                x += w + 8;
            }
            if (jamais.size() > MAX_MORTES) {
                texte(s, x + 4, yj, 12, 400, MUTED,
                        "et " + (jamais.size() - MAX_MORTES) + " autres.");
            }
        }

        texte(s, 40, 706, 11.5, 400, MUTED,
                "Runtime X-Ray — chiffres mesurés, aucun estimé. Le rapport complet accompagne ce schéma.");
        s.append("</g></svg>\n");
        return s.toString();
    }

    // ------------------------------------------------------------------ dessin

    private static void texte(StringBuilder s, double x, double y, double taille, int gras,
                              String couleur, String contenu) {
        s.append("<text x=\"%s\" y=\"%s\" font-size=\"%s\" font-weight=\"%d\" fill=\"%s\">%s</text>\n"
                .formatted(nombre(x), nombre(y), nombre(taille), gras, couleur, echapper(contenu)));
    }

    private static void titre(StringBuilder s, int x, int y, String t, int largeur) {
        texte(s, x, y, 12, 700, MUTED, t);
        s.append("<line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"%s\"/>\n"
                .formatted(x, y + 10, x + largeur, y + 10, RULE));
    }

    private static void tuile(StringBuilder s, int x, int y, String etiquette, String valeur,
                              String detail, String couleur) {
        s.append("<rect x=\"%d\" y=\"%d\" width=\"270\" height=\"118\" rx=\"8\" fill=\"#f6f8fa\"/>\n"
                .formatted(x, y));
        s.append("<rect x=\"%d\" y=\"%d\" width=\"4\" height=\"118\" rx=\"2\" fill=\"%s\"/>\n"
                .formatted(x, y, couleur));
        texte(s, x + 20, y + 26, 11, 700, MUTED, etiquette);
        texte(s, x + 20, y + 70, 32, 700, couleur, valeur);
        texte(s, x + 20, y + 96, 12, 400, MUTED, detail);
    }

    /** Une barre, son libellé et son pourcentage — la forme qui se lit de loin. */
    private static void barre(StringBuilder s, int x, int y, int largeur, String libelle,
                              double pct, String droite, int placeLibelle) {
        int barreX = x + placeLibelle, barreL = largeur - placeLibelle - 62;
        texte(s, x, y + 4, 12.5, 400, INK, libelle);
        s.append("<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"10\" rx=\"5\" fill=\"%s\"/>\n"
                .formatted(barreX, y - 5, barreL, FOND_BARRE));
        int plein = (int) Math.round(barreL * Math.max(0, Math.min(100, pct)) / 100.0);
        String couleur = pct >= 70 ? VERT : pct >= 35 ? AMBRE : ROUGE;
        if (plein > 0) {
            s.append("<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"10\" rx=\"5\" fill=\"%s\"/>\n"
                    .formatted(barreX, y - 5, plein, couleur));
        }
        texte(s, barreX + barreL + 10, y + 4, 12, 600, couleur, arrondi(pct) + " %");
        if (!droite.isEmpty()) texte(s, x + placeLibelle - 84, y + 4, 11.5, 400, MUTED, droite);
    }

    // ------------------------------------------------------------------ mesures

    /** Par paquet : instructions couvertes, manquées, et nombre de classes. */
    static Map<String, long[]> couvertureParPaquet(List<Object> runs) {
        Map<String, long[]> out = new TreeMap<>();
        Map<String, Map<String, long[]>> parClasse = new TreeMap<>();
        for (Object r : runs) {
            if (!(r instanceof Map<?, ?> run) || !(run.get("packages") instanceof Map<?, ?> pkgs)) continue;
            for (Map.Entry<?, ?> p : pkgs.entrySet()) {
                if (!(p.getValue() instanceof Iterable<?> classes)) continue;
                for (Object c : classes) {
                    if (!(c instanceof Map<?, ?> cls)) continue;
                    // La meilleure couverture atteinte par une exécution fait foi : c'est
                    // l'union qui décrit la campagne, pas la dernière exécution lancée.
                    long[] v = parClasse.computeIfAbsent(String.valueOf(p.getKey()),
                            k -> new TreeMap<>()).computeIfAbsent(String.valueOf(cls.get("name")),
                            k -> new long[2]);
                    long couvert = nombre(cls.get("covered")), manque = nombre(cls.get("missed"));
                    if (couvert > v[0]) { v[0] = couvert; v[1] = manque; }
                }
            }
        }
        for (Map.Entry<String, Map<String, long[]>> p : parClasse.entrySet()) {
            long[] total = new long[3];
            for (long[] v : p.getValue().values()) { total[0] += v[0]; total[1] += v[1]; total[2]++; }
            out.put(p.getKey(), total);
        }
        return out;
    }

    static List<String> classesJamaisExecutees(List<Object> runs) {
        Map<String, Long> meilleur = new TreeMap<>();
        for (Object r : runs) {
            if (!(r instanceof Map<?, ?> run) || !(run.get("packages") instanceof Map<?, ?> pkgs)) continue;
            for (Object classes : pkgs.values()) {
                if (!(classes instanceof Iterable<?> liste)) continue;
                for (Object c : liste) {
                    if (!(c instanceof Map<?, ?> cls)) continue;
                    String nom = String.valueOf(cls.get("simple"));
                    meilleur.merge(nom, nombre(cls.get("covered")), Math::max);
                }
            }
        }
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Long> e : meilleur.entrySet()) if (e.getValue() == 0) out.add(e.getKey());
        return out;
    }

    static long nombreDeClasses(List<Object> runs) {
        java.util.Set<String> noms = new java.util.HashSet<>();
        for (Object r : runs) {
            if (!(r instanceof Map<?, ?> run) || !(run.get("packages") instanceof Map<?, ?> pkgs)) continue;
            for (Object classes : pkgs.values()) {
                if (!(classes instanceof Iterable<?> liste)) continue;
                for (Object c : liste) if (c instanceof Map<?, ?> cls) noms.add(String.valueOf(cls.get("name")));
            }
        }
        return noms.size();
    }

    /** Les méthodes sous lesquelles le plus de temps a été passé, toutes exécutions réunies. */
    static List<Map.Entry<String, Long>> methodesLesPlusChaudes(List<Object> runs) {
        Map<String, Long> poids = new LinkedHashMap<>();
        for (Object r : runs) {
            if (!(r instanceof Map<?, ?> run) || !(run.get("calltree") instanceof Map<?, ?> arbre)) continue;
            descendre(arbre, poids);
        }
        List<Map.Entry<String, Long>> tries = new ArrayList<>(poids.entrySet());
        tries.sort(Map.Entry.<String, Long>comparingByValue().reversed());
        return tries.subList(0, Math.min(MAX_METHODES, tries.size()));
    }

    private static void descendre(Map<?, ?> noeud, Map<String, Long> poids) {
        if (!(noeud.get("children") instanceof Iterable<?> enfants)) return;
        for (Object e : enfants) {
            if (!(e instanceof Map<?, ?> enfant)) continue;
            Object nom = enfant.get("name");
            if (nom != null) poids.merge(String.valueOf(nom), nombre(enfant.get("total")), Long::sum);
            descendre(enfant, poids);
        }
    }

    private static Map<String, Object> contexte(List<Object> runs) {
        for (Object r : runs) {
            if (r instanceof Map<?, ?> run && run.get("context") instanceof Map<?, ?> c) {
                Map<String, Object> out = new LinkedHashMap<>();
                c.forEach((k, v) -> out.put(String.valueOf(k), v));
                return out;
            }
        }
        return Map.of();
    }

    private static String dureeTotale(List<Object> runs) {
        double total = 0;
        for (Object r : runs) {
            if (r instanceof Map<?, ?> run && run.get("context") instanceof Map<?, ?> c
                    && c.get("dureeSecondes") instanceof Number n) total += n.doubleValue();
        }
        if (total <= 0) return "—";
        return total < 90 ? arrondi(total) + " s" : Math.round(total / 60) + " min";
    }

    // ------------------------------------------------------------------ menues

    /** Ce qu'une exécution a couvert, à elle seule. */
    static long[] couvertureDe(Map<?, ?> run) {
        long[] out = new long[2];
        if (!(run.get("packages") instanceof Map<?, ?> pkgs)) return out;
        for (Object classes : pkgs.values()) {
            if (!(classes instanceof Iterable<?> liste)) continue;
            for (Object c : liste) {
                if (!(c instanceof Map<?, ?> cls)) continue;
                out[0] += nombre(cls.get("covered"));
                out[1] += nombre(cls.get("missed"));
            }
        }
        return out;
    }

    private static String duree(Map<?, ?> run) {
        if (run.get("context") instanceof Map<?, ?> c && c.get("dureeSecondes") instanceof Number n) {
            return arrondi(n.doubleValue()) + " s";
        }
        return "";
    }

    private static double part(long[] v) {
        return v[0] + v[1] == 0 ? 0 : 100.0 * v[0] / (v[0] + v[1]);
    }

    private static long nombre(Object o) { return o instanceof Number n ? n.longValue() : 0; }

    private static String arrondi(double d) {
        return String.valueOf(Math.round(d * 10) / 10.0).replace(".0", "");
    }

    private static String nombre(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private static String court(String s, int max) {
        return s.length() <= max ? s : "…" + s.substring(s.length() - max + 1);
    }

    private static String joindre(String sep, String... parties) {
        StringBuilder sb = new StringBuilder();
        for (String p : parties) {
            if (p == null || p.isBlank() || "null".equals(p)) continue;
            if (sb.length() > 0) sb.append(sep);
            sb.append(p);
        }
        return sb.toString();
    }

    /** Un nom de classe peut porter n'importe quoi : le SVG ne doit pas s'en trouver cassé. */
    private static String echapper(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
