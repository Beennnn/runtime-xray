package lab.xray.report;

import lab.xray.json.Json;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Réécriture des mesures dans des formats que d'autres outils savent lire.
 *
 * <p>La page produite par cet outil est <b>une</b> façon de lire une exécution, pas la
 * seule. Un profil s'examine très bien dans Firefox Profiler, une couverture s'affiche dans
 * un éditeur, un arbre d'appel se compare dans speedscope. Refuser ces usages reviendrait à
 * enfermer une mesure ouverte dans un rendu unique — exactement ce que l'étude reproche aux
 * outils fermés.
 *
 * <p>Le principe tenu ici : <b>on n'exporte que ce qui a été mesuré</b>. Aucun repli, aucun
 * masquage, aucune agrégation de la page ne s'applique à ces fichiers ; ils reproduisent la
 * sortie des outils dans une autre grammaire. Ce qu'un tiers en tire ne dépend alors que de
 * son propre outil.
 *
 * <p>Les formats retenus sont publics et documentés :
 * <ul>
 *   <li><b>perf script</b> — la sortie texte de {@code perf script} sous Linux. C'est le
 *       format d'import de Firefox Profiler et de plusieurs convertisseurs.</li>
 *   <li><b>cpuprofile</b> — le profil échantillonné de Chrome DevTools : compact, lu par
 *       speedscope et par les outils de développement des navigateurs.</li>
 *   <li><b>LCOV</b> — le format de couverture le plus largement accepté : éditeurs,
 *       forges, services de suivi.</li>
 *   <li><b>JSON</b> pour les valeurs capturées, dont la structure est décrite dans la
 *       documentation : il n'existe pas de format d'échange établi pour cette information.</li>
 * </ul>
 */
public final class Exports {

    /**
     * Ce qu'un export {@code perf} pèse au plus.
     *
     * <p>Le plafond porte sur les <b>octets</b>, et pas sur le nombre de relevés : ce format
     * réécrit la pile entière à chaque échantillon, si bien que sa taille dépend d'abord de
     * la <b>profondeur</b> des piles. Quatre-vingt mille relevés de quarante frames pèsent
     * plus que huit cent mille relevés de deux — compter les relevés serait compter la
     * mauvaise chose.
     *
     * <p>Au-delà, on <b>allège proportionnellement</b> plutôt que de s'arrêter en route.
     * S'arrêter coupait la fin de l'exécution : tout ce qui s'était passé après le plafond
     * disparaissait sans laisser de trace, et le profil mentait sur la forme du programme.
     * Alléger garde cette forme — c'est exactement ce que fait un profileur échantillonné
     * quand on espace ses relevés, et la même réserve s'applique : ce qui pèse très peu peut
     * disparaître. Le facteur retenu est annoncé sur la sortie standard.
     */
    private static final long MAX_OCTETS = 32L * 1024 * 1024;

    /** Formats disponibles. Le nom est celui qu'on écrit sur la ligne de commande. */
    public enum Format {
        PERF("perf", "perf"), CPUPROFILE("cpuprofile", "cpuprofile"),
        LCOV("lcov", "lcov"), VALEURS("valeurs", "values");

        public final String option;

        /**
         * Le nom qu'on affiche. Il diffère de {@link #option} pour {@code valeurs}, et
         * c'est le sens de la bascule : le nom français reste accepté à vie parce que des
         * scripts l'écrivent, mais ce n'est plus lui qu'on montre.
         */
        public final String affiche;

        Format(String option, String affiche) { this.option = option; this.affiche = affiche; }

        /** Les noms anglais, canoniques. Les français restent acceptés, à vie. */
        private static final java.util.Map<String, String> ALIAS =
                java.util.Map.of("values", "valeurs");

        public static Format of(String name) {
            String n = name.trim().toLowerCase(java.util.Locale.ROOT);
            n = ALIAS.getOrDefault(n, n);
            for (Format f : values()) {
                if (f.option.equalsIgnoreCase(n)) return f;
            }
            throw new IllegalArgumentException("unknown export format: " + name
                    + " (expected: perf, cpuprofile, lcov, values, or all)");
        }

        public static Set<Format> parse(String list) {
            String t = list.trim().toLowerCase(java.util.Locale.ROOT);
            if (list.isBlank() || t.equals("all") || t.equals("tout")) {
                return Set.of(values());
            }
            Set<Format> out = new java.util.LinkedHashSet<>();
            for (String part : list.split("[,\\s]+")) {
                if (!part.isBlank()) out.add(of(part));
            }
            return out;
        }
    }

    private Exports() {}

    /**
     * Écrit les formats demandés dans {@code <exécution>/exports/} et rend les fichiers
     * produits. Un format dont la source manque est simplement sauté : une exécution
     * lancée sans capture de valeurs n'a rien à exporter de ce côté-là.
     */
    public static List<Path> write(Path runDir, Set<Format> formats, int intervalMs,
                                   int valuesPerMethod) throws Exception {
        Path dir = runDir.resolve("exports");
        Path collapsed = runDir.resolve("async-profiler/profil.collapsed");
        Path jacoco = runDir.resolve("jacoco/html/jacoco.xml");
        List<Path> written = new ArrayList<>();

        if (formats.contains(Format.PERF) && Files.isRegularFile(collapsed)) {
            Files.createDirectories(dir);
            written.add(perf(collapsed, dir.resolve("profil.perf.txt"), intervalMs));
        }
        if (formats.contains(Format.CPUPROFILE) && Files.isRegularFile(collapsed)) {
            Files.createDirectories(dir);
            written.add(cpuprofile(collapsed, dir.resolve("profil.cpuprofile"), intervalMs));
        }
        if (formats.contains(Format.LCOV) && Files.isRegularFile(jacoco)) {
            Files.createDirectories(dir);
            written.add(lcov(Coverage.parse(jacoco), dir.resolve("couverture.lcov")));
        }
        if (formats.contains(Format.VALEURS)) {
            Inspection inspection = Inspection.read(
                    runDir.resolve("arthas/watch-params.txt"),
                    runDir.resolve("arthas/trace-calltree.txt"),
                    valuesPerMethod);
            if (!inspection.values.isEmpty()) {
                Files.createDirectories(dir);
                written.add(values(inspection, dir.resolve("valeurs.json")));
            }
        }
        return written;
    }

    // ------------------------------------------------------------------ perf script

    /**
     * Traduit les piles repliées en sortie {@code perf script}.
     *
     * <p>Un bloc par échantillon, l'appelé d'abord et l'appelant ensuite — c'est l'ordre de
     * {@code perf}, l'inverse du fichier replié. Les adresses sont nulles : elles n'ont
     * aucun sens pour du code compilé à la volée, et aucun lecteur ne s'en sert dès lors
     * que le symbole est là.
     */
    private static Path perf(Path collapsed, Path out, int intervalMs) throws IOException {
        List<String> lignes = Files.readAllLines(collapsed, StandardCharsets.UTF_8);
        Allegement allegement = Allegement.pour(lignes);
        long emitted = 0;
        double reste = 0;
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            for (String line : lignes) {
                Sample sample = Sample.of(line);
                if (sample == null) continue;
                // Le reliquat se reporte d'une pile à l'autre : sans lui, tout ce qui pèse
                // moins que le facteur disparaîtrait, et le total s'effondrerait.
                double du = sample.count * allegement.facteur + reste;
                long combien = (long) Math.floor(du);
                reste = du - combien;
                for (long i = 0; i < combien; i++, emitted++) {
                    double when = emitted * (intervalMs / 1000.0) / allegement.facteur;
                    w.write(String.format(Locale.ROOT, "java 1/1 %.6f: 1 cpu-clock:%n", when));
                    for (int f = sample.frames.length - 1; f >= 0; f--) {
                        w.write("\t0 " + sample.frames[f] + " ([jit])\n");
                    }
                    w.write("\n");
                }
            }
        }
        if (allegement.allege()) {
            System.out.println("   perf export thinned out: " + emitted + " samples out of "
                    + allegement.total + " (" + Math.round(allegement.facteur * 100)
                    + "%) — this format rewrites the whole stack at every sample, and would\n"
                    + "      otherwise weigh " + allegement.octets / (1024 * 1024) + " MB.");
            System.out.println("      The shape of the profile is kept; very small shares "
                    + "may vanish. The cpuprofile, itself, carries everything.");
        }
        return out;
    }

    /**
     * De combien alléger, et de quoi le dire.
     *
     * <p>Le facteur vaut 1 tant que le fichier tient sous le plafond : la grande majorité des
     * mesures ne sont pas concernées, et un export qui ne change rien ne doit rien annoncer.
     */
    private record Allegement(long total, long octets, double facteur) {

        /** Ce qu'écrit une ligne d'échantillon, en-tête comprise, mesuré sur les données. */
        static Allegement pour(List<String> lignes) {
            long total = 0, octets = 0;
            for (String line : lignes) {
                Sample sample = Sample.of(line);
                if (sample == null) continue;
                total += sample.count;
                long parEchantillon = ENTETE;
                for (String frame : sample.frames) {
                    parEchantillon += frame.length() + DECOR;
                }
                octets += sample.count * parEchantillon;
            }
            return new Allegement(total, octets,
                    octets <= MAX_OCTETS ? 1.0 : (double) MAX_OCTETS / octets);
        }

        boolean allege() { return facteur < 1.0; }

        /** « java 1/1 12.345678: 1 cpu-clock: » plus la ligne vide de fin. */
        private static final int ENTETE = 34;
        /** La tabulation, l'adresse, l'espace, « ([jit]) » et le retour à la ligne. */
        private static final int DECOR = 13;
    }

    // ------------------------------------------------------------------ cpuprofile

    /**
     * Traduit les piles repliées en profil échantillonné de Chrome DevTools.
     *
     * <p>Bien plus compact que {@code perf script} : les piles y sont partagées dans un
     * arbre, et l'échantillon n'est qu'un renvoi vers un nœud. Un profil d'une minute tient
     * en quelques centaines de kilo-octets là où le texte en occupe des dizaines de
     * méga-octets. Il n'a donc pas besoin d'être allégé : il porte tous les relevés.
     */
    private static Path cpuprofile(Path collapsed, Path out, int intervalMs) throws IOException {
        Node root = new Node(1, "(root)");
        List<Node> nodes = new ArrayList<>();
        nodes.add(root);
        List<Object> samples = new ArrayList<>();

        for (String line : Files.readAllLines(collapsed, StandardCharsets.UTF_8)) {
            Sample sample = Sample.of(line);
            if (sample == null) continue;
            Node node = root;
            for (String frame : sample.frames) node = node.child(frame, nodes);
            for (long i = 0; i < sample.count; i++) {
                samples.add(node.id);
            }
        }

        long step = Math.max(1L, intervalMs) * 1000L;                 // microsecondes
        List<Object> deltas = new ArrayList<>(samples.size());
        for (int i = 0; i < samples.size(); i++) deltas.add(step);

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("nodes", nodes.stream().map(Node::toMap).map(Object.class::cast).toList());
        profile.put("startTime", 0L);
        profile.put("endTime", step * samples.size());
        profile.put("samples", samples);
        profile.put("timeDeltas", deltas);
        Files.writeString(out, Json.write(profile), StandardCharsets.UTF_8);
        return out;
    }

    /** Nœud de l'arbre des piles, dans la forme attendue par le format cpuprofile. */
    private static final class Node {
        final int id;
        final String frame;
        final Map<String, Node> children = new LinkedHashMap<>();

        Node(int id, String frame) { this.id = id; this.frame = frame; }

        Node child(String name, List<Node> all) {
            Node existing = children.get(name);
            if (existing != null) return existing;
            Node created = new Node(all.size() + 1, name);
            children.put(name, created);
            all.add(created);
            return created;
        }

        Map<String, Object> toMap() {
            // async-profiler écrit « paquet/Classe.methode » : la classe fait un « url »
            // acceptable, et le lecteur retrouve ses repères habituels.
            int cut = frame.lastIndexOf('.');
            String owner = cut > 0 ? frame.substring(0, cut).replace('/', '.') : "";
            String method = cut > 0 ? frame.substring(cut + 1) : frame;

            Map<String, Object> call = new LinkedHashMap<>();
            call.put("functionName", method);
            call.put("scriptId", "0");
            call.put("url", owner);
            call.put("lineNumber", -1);
            call.put("columnNumber", -1);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("callFrame", call);
            if (!children.isEmpty()) {
                m.put("children", children.values().stream()
                        .map(n -> (Object) n.id).toList());
            }
            return m;
        }
    }

    /** Une ligne de pile repliée : les frames, de la racine à l'appelé, et son poids. */
    private record Sample(String[] frames, long count) {

        static Sample of(String line) {
            int space = line.lastIndexOf(' ');
            if (space < 0) return null;
            long count;
            try {
                count = Long.parseLong(line.substring(space + 1).trim());
            } catch (NumberFormatException e) {
                return null;
            }
            String stack = line.substring(0, space);
            return stack.isBlank() ? null : new Sample(stack.split(";"), count);
        }
    }

    // ------------------------------------------------------------------ LCOV

    /**
     * Traduit la couverture JaCoCo en LCOV.
     *
     * <p>Une réserve honnête : JaCoCo dit <b>si</b> une ligne a été exécutée, jamais
     * <b>combien de fois</b>. Le compteur LCOV vaut donc 1 ou 0 — c'est toute l'information
     * disponible, et la présenter autrement serait inventer des passages.
     */
    private static Path lcov(Coverage coverage, Path out) throws IOException {
        StringBuilder sb = new StringBuilder();
        Map<String, List<Object>> methodsBySource = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : coverage.methods.entrySet()) {
            Map<?, ?> byClass = (Map<?, ?>) e.getValue();
            methodsBySource
                    .computeIfAbsent(String.valueOf(byClass.get("source")), k -> new ArrayList<>())
                    .addAll((List<Object>) byClass.get("methods"));
        }

        for (Map.Entry<String, Map<String, Object>> file : coverage.lines.entrySet()) {
            sb.append("TN:\n");
            sb.append("SF:").append(file.getKey()).append('\n');

            List<Object> methods = methodsBySource.getOrDefault(file.getKey(), List.of());
            for (Object o : methods) {
                Map<?, ?> m = (Map<?, ?>) o;
                sb.append("FN:").append(m.get("line")).append(',').append(m.get("name")).append('\n');
            }
            long hitMethods = 0;
            for (Object o : methods) {
                Map<?, ?> m = (Map<?, ?>) o;
                int covered = ((Number) m.get("covered")).intValue();
                if (covered > 0) hitMethods++;
                sb.append("FNDA:").append(covered > 0 ? 1 : 0).append(',')
                  .append(m.get("name")).append('\n');
            }
            if (!methods.isEmpty()) {
                sb.append("FNF:").append(methods.size()).append('\n');
                sb.append("FNH:").append(hitMethods).append('\n');
            }

            long found = 0, hit = 0, branchesFound = 0, branchesHit = 0;
            for (Map.Entry<String, Object> line : file.getValue().entrySet()) {
                Map<?, ?> info = (Map<?, ?>) line.getValue();
                boolean executed = !"miss".equals(info.get("s"));
                sb.append("DA:").append(line.getKey()).append(',')
                  .append(executed ? 1 : 0).append('\n');
                found++;
                if (executed) hit++;

                if (info.get("b") instanceof List<?> b && b.size() == 2) {
                    int taken = ((Number) b.get(0)).intValue();
                    int total = ((Number) b.get(1)).intValue();
                    for (int i = 0; i < total; i++) {
                        sb.append("BRDA:").append(line.getKey()).append(",0,").append(i)
                          .append(',').append(i < taken ? "1" : "0").append('\n');
                    }
                    branchesFound += total;
                    branchesHit += taken;
                }
            }
            if (branchesFound > 0) {
                sb.append("BRF:").append(branchesFound).append('\n');
                sb.append("BRH:").append(branchesHit).append('\n');
            }
            sb.append("LF:").append(found).append('\n');
            sb.append("LH:").append(hit).append('\n');
            sb.append("end_of_record\n");
        }
        Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
        return out;
    }

    // ------------------------------------------------------------------ valeurs

    /** Les valeurs capturées, telles que la page les lit, dans un JSON qu'un script relit. */
    private static Path values(Inspection inspection, Path out) throws IOException {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("valeurs", inspection.values);
        doc.put("trace", inspection.trace);
        Files.writeString(out, Json.write(doc), StandardCharsets.UTF_8);
        return out;
    }
}
