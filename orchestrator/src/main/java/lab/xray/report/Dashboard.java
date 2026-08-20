package lab.xray.report;

import lab.xray.json.Json;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assemble la page unique à partir des sorties des outils.
 *
 * <p>Rien n'est mesuré ici : on relit ce qui a été produit et on le met côte à côte. La
 * page est <b>autonome</b> — données embarquées, aucune ressource externe — ce qui la rend
 * lisible hors ligne et transmissible en pièce jointe.
 */
public final class Dashboard {

    /** Renommage après coup : associe l'identifiant permanent d'une exécution à un nom. */
    private static final String NAMES_FILE = "noms.json";

    private Dashboard() {}

    public static Path build(Path commonDir, List<Path> sourceRoots, int valuesPerMethod)
            throws Exception {
        Map<String, Object> overrides = readOverrides(commonDir);
        List<Path> bases = findRuns(commonDir, 0, 3);
        if (bases.isEmpty()) {
            throw new IOException("aucune exécution trouvée sous " + commonDir
                    + " (une exécution est un répertoire contenant run-context.json)");
        }
        // La plus récente en premier : c'est celle qu'on vient de produire.
        bases.sort(Comparator.comparingLong((Path p) -> p.toFile().lastModified()).reversed());

        List<Object> runs = new ArrayList<>();
        for (Path base : bases) {
            runs.add(readRun(base, commonDir, overrides, valuesPerMethod));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runs", runs);
        data.put("sources", Sources.load(sourceRoots));

        String template = loadTemplate();
        String page = template.replace("/*__DATA__*/", Json.write(data));
        Path out = commonDir.resolve("index.html");
        Files.writeString(out, page, StandardCharsets.UTF_8);
        return out;
    }

    private static Map<String, Object> readRun(Path base, Path commonDir,
                                               Map<String, Object> overrides, int valuesPerMethod)
            throws Exception {
        Coverage coverage = Coverage.parse(base.resolve("jacoco/html/jacoco.xml"));
        CallTree tree = CallTree.parse(base.resolve("async-profiler/profil.collapsed"));
        Inspection inspection = Inspection.read(
                base.resolve("arthas/watch-params.txt"),
                base.resolve("arthas/trace-calltree.txt"),
                valuesPerMethod);

        Map<String, Object> context = readContext(base);
        String uuid = String.valueOf(context.getOrDefault("uuid", ""));
        String origin = String.valueOf(context.getOrDefault("nomOrigine",
                base.getFileName().toString()));
        Object renamed = overrides.get(uuid);

        Map<String, Object> run = new LinkedHashMap<>();
        run.put("uuid", uuid);
        run.put("nomOrigine", origin);
        run.put("renomme", renamed != null);
        run.put("nom", renamed != null ? renamed : origin);
        run.put("chemin", relative(commonDir, base));
        run.put("rapports", reportsPresent(base));
        run.put("coverage", coverage.lines);
        run.put("methods", coverage.methods);
        run.put("packages", coverage.packages);
        run.put("calltree", tree.root);
        // Un relevé toutes les millisecondes : c'est ce qui permet de convertir un nombre
        // d'échantillons en durée estimée dans la vue.
        run.put("intervalMs", 1);
        run.put("profileNote", tree.note);
        run.put("trace", inspection.trace);
        run.put("values", inspection.values);
        run.put("context", context.isEmpty() ? null : context);
        run.put("tracedClass", tracedClass(context, inspection));
        return run;
    }

    /**
     * La classe inspectée se déduit, dans l'ordre : du contexte de l'exécution, sinon des
     * valeurs réellement capturées. Rien n'est codé en dur — le rapport doit valoir pour
     * n'importe quelle application.
     */
    private static String tracedClass(Map<String, Object> context, Inspection inspection) {
        Object root = context.get("methodeRacine");
        if (root instanceof String s && !s.isBlank()) {
            return s.split("::")[0].replace('.', '/');
        }
        for (String frame : inspection.values.keySet()) {
            return frame.substring(0, frame.lastIndexOf('.'));
        }
        return "";
    }

    private static Map<String, Object> reportsPresent(Path base) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("couverture", Files.isRegularFile(base.resolve("jacoco/html/index.html")));
        m.put("ciblee", Files.isRegularFile(base.resolve("jacoco-focused/html/index.html")));
        m.put("flamegraph", Files.isRegularFile(base.resolve("async-profiler/flamegraph.html")));
        m.put("arbre", Files.isRegularFile(base.resolve("async-profiler/tree.html")));
        return m;
    }

    private static String relative(Path commonDir, Path base) {
        Path rel = commonDir.toAbsolutePath().normalize()
                .relativize(base.toAbsolutePath().normalize());
        String s = rel.toString().replace('\\', '/');
        return s.isEmpty() ? "" : s + "/";
    }

    /** Une exécution est un répertoire qui contient son contexte, ou à défaut sa couverture. */
    private static List<Path> findRuns(Path dir, int level, int maxDepth) throws IOException {
        List<Path> found = new ArrayList<>();
        if (level > maxDepth || !Files.isDirectory(dir)) {
            return found;
        }
        if (Files.isRegularFile(dir.resolve("run-context.json"))
                || Files.isRegularFile(dir.resolve("jacoco/html/jacoco.xml"))) {
            found.add(dir);
            return found;
        }
        try (var entries = Files.list(dir)) {
            for (Path sub : entries.sorted().toList()) {
                if (Files.isDirectory(sub) && !sub.getFileName().toString().startsWith(".")) {
                    found.addAll(findRuns(sub, level + 1, maxDepth));
                }
            }
        }
        return found;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readContext(Path base) {
        Path file = base.resolve("run-context.json");
        if (!Files.isRegularFile(file)) {
            return new LinkedHashMap<>();
        }
        try {
            return (Map<String, Object>) Json.read(Files.readString(file, StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println("   contexte illisible : " + file + " (" + e.getMessage() + ")");
            return new LinkedHashMap<>();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readOverrides(Path commonDir) {
        Path file = commonDir.resolve(NAMES_FILE);
        if (!Files.isRegularFile(file)) {
            return new LinkedHashMap<>();
        }
        try {
            return (Map<String, Object>) Json.read(Files.readString(file, StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println("   " + file + " illisible — noms d'origine conservés");
            return new LinkedHashMap<>();
        }
    }

    private static String loadTemplate() throws IOException {
        try (InputStream in = Dashboard.class.getResourceAsStream("/lab/xray/dashboard.html")) {
            if (in == null) {
                throw new IOException("gabarit de page absent du jar");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
