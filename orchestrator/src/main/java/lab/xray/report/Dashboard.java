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

    private Dashboard() {}

    public static Path build(Path commonDir, List<Path> sourceRoots, int valuesPerMethod)
            throws Exception {
        return build(commonDir, sourceRoots, valuesPerMethod, PackageFilter.NONE);
    }

    public static Path build(Path commonDir, List<Path> sourceRoots, int valuesPerMethod,
                             PackageFilter hidden) throws Exception {
        return build(commonDir, sourceRoots, valuesPerMethod, hidden, null);
    }

    /**
     * @param hidden paquets masqués par la configuration courante. Une exécution qui a
     *               enregistré sa propre liste garde la sienne : c'est celle sous laquelle
     *               elle a été analysée, et la vue doit dire ce qui a été fait, pas ce
     *               qu'on ferait aujourd'hui.
     */
    public static Path build(Path commonDir, List<Path> sourceRoots, int valuesPerMethod,
                             PackageFilter hidden, Map<String, Object> launch)
            throws Exception {
        Map<String, Object> overrides = Annotations.readCentral(commonDir);
        List<Path> bases = findRuns(commonDir, 0, 3);
        if (bases.isEmpty()) {
            throw new IOException("no run found under " + commonDir
                    + " (a run is a directory containing run-context.json)");
        }
        // La plus récente en premier : c'est celle qu'on vient de produire.
        bases.sort(Comparator.comparingLong((Path p) -> p.toFile().lastModified()).reversed());

        List<Object> runs = new ArrayList<>();
        for (Path base : bases) {
            runs.add(readRun(base, commonDir, overrides, valuesPerMethod, hidden));
        }

        Sources.Index index = Sources.load(sourceRoots);

        // Le gros part en blocs, chargés à la demande et libérables. Ce qui reste ici est ce
        // que la PREMIÈRE IMAGE montre : l'identité des exécutions, l'arbre des classes, et
        // les clés des sources — jamais leur contenu.
        Map<String, Object> data =
                new LinkedHashMap<>(Blocks.write(commonDir, runs, showableSources(runs, index)));
        // Le diagnostic voyage AVEC la page, pas seulement à côté : c'est lui qui permet au
        // panneau de code de dire ce qu'il a cherché quand il n'a rien à montrer. Une page
        // transmise en pièce jointe reste alors explicable sans son répertoire d'origine.
        Map<String, Object> diagnostic = Diagnostic.write(commonDir, runs, index, launch);
        data.put("diagnostic", withoutCode(diagnostic));
        // Le même contenu, mais rangé pour être filtré plutôt que parcouru — voir Facts.
        // Rien de ce qui précède ne change : ce fichier s'ajoute, il ne remplace pas.
        Facts.write(commonDir, runs, index, diagnostic);
        data.put("fusion", mergePresent(commonDir, runs.size()));

        String template = loadTemplate();
        String page = template.replace("/*__DATA__*/", Json.write(data));
        Path out = commonDir.resolve("index.html");
        Files.writeString(out, page, StandardCharsets.UTF_8);

        // La campagne sur une page, en SVG : la vue interactive répond aux questions qu'on
        // lui pose, elle ne se transmet pas. Ce schéma-là se colle dans une diapositive.
        Diagram.write(commonDir, runs);

        // Le même contenu en Markdown, sans surcoût de collecte : c'est le seul format
        // qu'une forge affiche comme une page et non comme du code source.
        Markdown.write(commonDir, runs);
        return out;
    }

    /**
     * Le diagnostic allégé de ce que la page porte déjà.
     *
     * <p>Le contexte de chaque exécution et la liste de ses rapports sont dans {@code runs} :
     * les embarquer une seconde fois doublerait ces données dans une page qui se transmet
     * en pièce jointe. Le fichier {@code diagnostic.json}, lui, les garde — il se lit seul.
     */
    /**
     * Les sources que la page peut réellement montrer — et rien d'autre.
     *
     * <p>On embarquait l'index entier, c'est-à-dire tout {@code .java} rencontré sous les
     * racines. Or la vue de code n'ouvre un fichier que par une classe mesurée : une source
     * absente de la couverture n'a aucun chemin qui y mène, jamais. Elle pesait sans pouvoir
     * s'afficher.
     *
     * <p>Le coût restait invisible tant qu'on désignait le répertoire de sources exact. Il a
     * éclaté quand l'index s'est mis à accepter n'importe quelle racine : pointer le projet
     * entier devient commode, et embarque alors les tests, les sources engendrées, les
     * dépendances déballées. Un rapport de 217 Mo que Firefox renonce à afficher, constaté le
     * 26 août 2026 ; sur le projet d'exemple, <b>93 % du poids des sources</b> ne pouvait pas
     * s'afficher.
     *
     * <p>Rien de fonctionnel ne part avec : l'index complet reste dans le diagnostic, qui
     * continue de dire ce qui a été lu et où. Seul ce qui n'avait aucun chemin d'affichage
     * cesse de voyager.
     */
    static Map<String, Object> showableSources(List<Object> runs, Sources.Index index) {
        Map<String, Object> kept = new LinkedHashMap<>();
        for (String key : Diagnostic.samples(runs)) {
            Object lines = index.byKey().get(key);
            if (lines != null) kept.put(key, lines);
        }
        return kept;
    }

    /**
     * Le rapport JaCoCo de toutes les exécutions réunies, s'il a été produit.
     *
     * <p>On rapporte sa présence plutôt que de la déduire du nombre d'exécutions : il peut
     * manquer pour de bonnes raisons — bytecode inconnu en réassemblage, composant JaCoCo
     * introuvable — et un lien mort vaut moins que pas de lien.
     */
    private static Map<String, Object> mergePresent(Path commonDir, int runs) {
        Map<String, Object> m = new LinkedHashMap<>();
        Path html = commonDir.resolve("jacoco-fusion/html");
        m.put("couverture", Files.isRegularFile(html.resolve("index.html")));
        m.put("xml", Files.isRegularFile(html.resolve("jacoco.xml")));
        m.put("csv", Files.isRegularFile(html.resolve("jacoco.csv")));
        m.put("executions", runs);
        return m;
    }

    private static Map<String, Object> withoutCode(Map<String, Object> diagnostic) {
        Map<String, Object> thinned = new LinkedHashMap<>(diagnostic);
        thinned.remove("executions");
        return thinned;
    }

    private static Map<String, Object> readRun(Path base, Path commonDir,
                                               Map<String, Object> overrides, int valuesPerMethod,
                                               PackageFilter fallback)
            throws Exception {
        Map<String, Object> context = readContext(base);
        Object recorded = context.get("paquetsMasques");
        PackageFilter hidden = recorded == null
                ? fallback
                : PackageFilter.of(String.valueOf(recorded));

        Coverage coverage = Coverage.parse(base.resolve("jacoco/html/jacoco.xml"), hidden);
        CallTree tree = CallTree.parse(base.resolve("async-profiler/profil.collapsed"), hidden);
        Inspection inspection = Inspection.read(
                base.resolve("arthas/watch-params.txt"),
                base.resolve("arthas/trace-calltree.txt"),
                valuesPerMethod);

        String uuid = String.valueOf(context.getOrDefault("uuid", ""));
        Object recordedName = context.get("nomOrigine");
        String origin = recordedName == null || String.valueOf(recordedName).isBlank()
                ? null
                : String.valueOf(recordedName);
        // Trois emplacements possibles, le plus proche de l'exécution l'emporte —
        // voir Annotations pour ce que chacun implique.
        Annotation annotation = Annotation.of(Annotations.forRun(base, uuid, overrides));

        Map<String, Object> run = new LinkedHashMap<>();
        run.put("uuid", uuid);
        run.put("nomOrigine", origin);
        run.put("nomOutil", annotation.name);
        run.put("description", annotation.description);
        run.put("etiquettes", annotation.tags);
        run.put("elagage", annotation.pruning);
        run.put("renomme", annotation.name != null);
        // Trois sources, dans cet ordre : le nom posé dans l'outil, celui donné au
        // lancement, et à défaut l'identifiant. Aucun libellé inventé : sans nom, ce qui
        // s'affiche désigne quand même l'exécution, et une seule.
        run.put("nom", annotation.name != null ? annotation.name
                : origin != null ? origin
                : shortId(uuid, base));
        run.put("chemin", relative(commonDir, base));
        run.put("rapports", reportsPresent(base));
        // La vue doit pouvoir dire ce qui a été écarté AVANT la mesure : ce code-là n'est
        // pas « absent », il a été tu, et taire la différence serait mentir par omission.
        run.put("paquetsMasques", recorded);
        run.put("coverage", coverage.lines);
        run.put("methods", coverage.methods);
        run.put("packages", coverage.packages);
        run.put("calltree", tree.root);
        // Un relevé toutes les millisecondes : c'est ce qui permet de convertir un nombre
        // d'échantillons en durée estimée dans la vue.
        run.put("intervalMs", 1);
        run.put("profileNote", tree.note);
        run.put("stacksNote", tree.stacksNote);
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

    /**
     * Ce que chaque outil a réellement écrit sur le disque, y compris les fichiers bruts.
     *
     * <p>Tout est recensé, pas seulement les pages présentables : cette page est une
     * synthèse, et une synthèse peut se tromper ou ne plus s'ouvrir. Les sorties d'origine
     * doivent rester atteignables en un clic — c'est ce qui distingue un rapport
     * vérifiable d'un rapport à croire sur parole.
     */
    private static Map<String, Object> reportsPresent(Path base) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("couverture", Files.isRegularFile(base.resolve("jacoco/html/index.html")));
        m.put("ciblee", Files.isRegularFile(base.resolve("jacoco-focused/html/index.html")));
        m.put("jacocoXml", Files.isRegularFile(base.resolve("jacoco/html/jacoco.xml")));
        m.put("jacocoCsv", Files.isRegularFile(base.resolve("jacoco/html/jacoco.csv")));
        m.put("flamegraph", Files.isRegularFile(base.resolve("async-profiler/flamegraph.html")));
        m.put("flamegraphInverse",
                Files.isRegularFile(base.resolve("async-profiler/flamegraph-inverse.html")));
        m.put("collapsed", Files.isRegularFile(base.resolve("async-profiler/profil.collapsed")));
        m.put("valeurs", Files.isRegularFile(base.resolve("arthas/watch-params.txt")));
        m.put("traceBrute", Files.isRegularFile(base.resolve("arthas/trace-calltree.txt")));
        m.put("exportPerf", Files.isRegularFile(base.resolve("exports/profil.perf.txt")));
        m.put("exportCpuprofile", Files.isRegularFile(base.resolve("exports/profil.cpuprofile")));
        m.put("exportLcov", Files.isRegularFile(base.resolve("exports/couverture.lcov")));
        m.put("exportValeurs", Files.isRegularFile(base.resolve("exports/valeurs.json")));
        m.put("journal", Files.isRegularFile(base.resolve("execution.log")));
        m.put("contexte", Files.isRegularFile(base.resolve("run-context.json")));
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
            System.err.println("   run context unreadable: " + file + " (" + e.getMessage() + ")");
            return new LinkedHashMap<>();
        }
    }

    /** Faute de nom, l'identifiant abrégé : assez court pour tenir, assez long pour trier. */
    private static String shortId(String uuid, Path base) {
        if (uuid == null || uuid.isBlank()) return base.getFileName().toString();
        return uuid.length() > 8 ? uuid.substring(0, 8) : uuid;
    }

    /** Nom, description et étiquettes posés sur une exécution après sa mesure. */
    private record Annotation(String name, String description, Map<String, Object> tags,
                              Map<String, Object> pruning) {

        static final Annotation EMPTY = new Annotation(null, null, Map.of(), null);

        @SuppressWarnings("unchecked")
        static Annotation of(Object recorded) {
            if (recorded == null) return EMPTY;
            if (recorded instanceof Map<?, ?> map) {
                Map<String, Object> m = (Map<String, Object>) map;
                Object tags = m.get("etiquettes");
                Object pruning = m.get("elagage");
                return new Annotation(
                        text(m.get("nom")),
                        text(m.get("description")),
                        tags instanceof Map<?, ?> t ? (Map<String, Object>) t : Map.of(),
                        pruning instanceof Map<?, ?> p ? (Map<String, Object>) p : null);
            }
            return new Annotation(text(recorded), null, Map.of(), null);
        }

        private static String text(Object value) {
            if (value == null) return null;
            String s = String.valueOf(value).trim();
            return s.isEmpty() ? null : s;
        }
    }

    private static String loadTemplate() throws IOException {
        try (InputStream in = Dashboard.class.getResourceAsStream("/lab/xray/dashboard.html")) {
            if (in == null) {
                throw new IOException("page template missing from the jar");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
