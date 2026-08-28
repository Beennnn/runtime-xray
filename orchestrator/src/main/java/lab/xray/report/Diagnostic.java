package lab.xray.report;

import lab.xray.json.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What one needs to know when the view does not show what was expected.
 *
 * <p>A report that is missing something does not say why: an empty code panel reads
 * exactly like a code panel we failed to fill. On 26 August 2026, an analysis covering
 * 447 classes showed "Source unavailable" on every one of them, and nothing in the report
 * made it possible to decide between the four possible causes — root not set, root
 * non-existent, root at the wrong level, or sources genuinely absent from the machine. A
 * screenshot had to be asked for.
 *
 * <p>Hence this file. It is written <b>at every assembly</b>, beside the page, and it is
 * made to be <b>sent back as it is</b>: it carries what we would have asked as questions.
 * What was asked for, what was found, where, and — when a match fails — what should have
 * been written for it to succeed.
 *
 * <p><b>It contains no secret</b>: neither the shared server's token nor the captured
 * argument values. It does carry absolute paths and class names, like the report itself —
 * it is a file to circulate with the same care.
 */
public final class Diagnostic {

    /** Enough to understand, not enough to copy the report out. */
    private static final int EXAMPLES = 40;

    /**
     * The number of classes listed in the bytecode, across all roots.
     *
     * <p>That list travels inside the page — it is what feeds the tree and the search. An
     * application classpath holds a few hundred to a few thousand; beyond that we stop, and
     * say so, rather than making a page of several megabytes.
     */
    private static final int MAX_CLASSES = 4_000;

    private Diagnostic() {}

    /**
     * @param commonDir  the output directory, where the file is placed
     * @param runs       the runs as the view receives them
     * @param index      the source index, with what it saw while being built
     * @param context    what only the caller knows — configuration, components, options —
     *                   or {@code null} when the view is reassembled without it
     * @return the content written, so the caller can draw a summary from it without
     *         reading it back
     */
    public static Map<String, Object> write(Path commonDir, List<Object> runs,
                                            Sources.Index index, Map<String, Object> context)
            throws IOException {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("outil", "runtime-xray");
        d.put("version", version());
        d.put("date", ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        d.put("machine", environment());
        if (context != null && !context.isEmpty()) d.put("lancement", context);
        d.put("sortie", commonDir.toAbsolutePath().normalize().toString());
        d.put("executions", runs(runs));
        d.put("sources", index.diagnostic());
        List<Object> bytecode = bytecode(context);
        d.put("bytecode", bytecode);
        Map<String, Object> matching = matching(runs, index);
        // The search only starts when there is something to look for: it walks the disk,
        // and on a complete report it would have nothing to find.
        if (number(matching.get("fichiersSansSource")) > 0) {
            matching.put("pistes", Sources.searchRoots(
                    missing(runs, index), whereToLook(commonDir, index, bytecode)));
        }
        d.put("rapprochement", matching);

        Path out = commonDir.resolve("diagnostic.json");
        Files.writeString(out, Json.write(d), StandardCharsets.UTF_8);
        return d;
    }

    /**
     * The coverage ↔ sources match, class by class.
     *
     * <p>This is the heart of the file: the coverage lists the files it measured, the index
     * lists those that were read, and the intersection is what the view will be able to
     * show. For each missing file we look for the same name elsewhere in the index — that
     * is the case of a misplaced root, and it is fixed with one path.
     */
    static Map<String, Object> matching(List<Object> runs, Sources.Index index) {
        Set<String> expected = samples(runs);

        List<Object> found = new ArrayList<>();
        List<Object> missing = new ArrayList<>();
        for (String key : expected) {
            if (index.byKey().containsKey(key)) {
                if (found.size() < EXAMPLES) found.add(key);
            } else {
                if (missing.size() < EXAMPLES) missing.add(missing(key, index));
            }
        }

        int missingCount = 0;
        for (String key : expected) if (!index.byKey().containsKey(key)) missingCount++;

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fichiersMesures", expected.size());
        m.put("fichiersAvecSource", expected.size() - missingCount);
        m.put("fichiersSansSource", missingCount);
        m.put("exemplesTrouves", found);
        m.put("exemplesManquants", missing);
        m.put("conclusion", conclusion(expected.size(), missingCount, index));
        // Files read that match no measured class: their number is enough for the page,
        // which has nothing to show of them. Listing them would copy the tree in.
        int withoutClass = 0;
        for (String key : index.byKey().keySet()) if (!expected.contains(key)) withoutClass++;
        m.put("sourcesSansClasse", withoutClass);
        return m;
    }

    /**
     * The files the coverage says it measured, across all runs.
     *
     * <p>All of them, not just the one being looked at: a class absent from one run but
     * present in another is still a class whose code we want.
     */
    static Set<String> samples(List<Object> runs) {
        Set<String> expected = new LinkedHashSet<>();
        for (Object r : runs) {
            if (r instanceof Map<?, ?> run && run.get("packages") instanceof Map<?, ?> pkgs) {
                for (Object classes : pkgs.values()) {
                    if (!(classes instanceof Iterable<?> list)) continue;
                    for (Object c : list) {
                        if (c instanceof Map<?, ?> cls && cls.get("source") instanceof String s) {
                            expected.add(s);
                        }
                    }
                }
            }
        }
        return expected;
    }

    /** The keys the coverage asks for and the index does not have. */
    static java.util.Set<String> missing(List<Object> runs, Sources.Index index) {
        java.util.Set<String> out = new LinkedHashSet<>();
        for (String key : samples(runs)) {
            if (!index.byKey().containsKey(key)) out.add(key);
        }
        return out;
    }

    /**
     * Where to look for the missing sources, from the most likely to the least.
     *
     * <p>None of these places is a convention: they are the only ones the run made known to
     * us. The analysed bytecode is the best clue of all — a {@code <project>/target/classes}
     * names the project to within two directories, and that is where its sources are. The
     * already-configured root is another: when it is one notch off, the right directory is
     * its immediate neighbour.
     *
     * <p>We never climb higher than those clues, and never towards the root of the disk: a
     * search that sweeps everything would end up proposing another project's sources.
     */
    static List<Path> whereToLook(Path commonDir, Sources.Index index, List<Object> bytecode) {
        List<Path> bases = new ArrayList<>();
        // 1. around the bytecode actually analysed
        for (Object o : bytecode) {
            if (o instanceof Map<?, ?> b && b.get("absolu") instanceof String path) {
                climb(Path.of(path), 2, bases);
            }
        }
        // 2. around the source roots already given — the "one notch off" case
        for (Object o : index.roots()) {
            if (o instanceof Map<?, ?> r && r.get("absolue") instanceof String path) {
                climb(Path.of(path), 1, bases);
            }
        }
        // 3. the directory the analysis was launched from
        bases.add(Path.of(System.getProperty("user.dir")));
        // 4. failing that, the neighbourhood of the report itself
        climb(commonDir, 1, bases);
        return bases;
    }

    /** A path and its ancestors, up to {@code notches} — never beyond. */
    private static void climb(Path start, int notches, List<Path> bases) {
        Path p = start.toAbsolutePath().normalize();
        if (Files.isRegularFile(p)) p = p.getParent();          // a jar names its directory
        for (int i = 0; i <= notches && p != null && p.getParent() != null; i++) {
            bases.add(p);
            p = p.getParent();
        }
    }

    private static long number(Object o) {
        return o instanceof Number n ? n.longValue() : 0;
    }

    /**
     * A measured file whose source we do not have — and the nearest thing we know of.
     *
     * <p>The same name found under another package is nearly always the right file seen
     * from a shifted root. We then give the real path: it is what the root to pass is
     * deduced from.
     */
    private static Map<String, Object> missing(String key, Sources.Index index) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cherche", key);
        String name = key.substring(key.lastIndexOf('/') + 1);
        Object homonyms = index.byName().get(name);
        m.put("memeNomAilleurs", homonyms);
        m.put("explication", homonyms == null
                ? "no file of that name under the roots given"
                : "a file of that name exists, but the package it declares is not the one "
                  + "the coverage reports — this is probably not the same class");
        return m;
    }

    /**
     * What each bytecode root actually contains.
     *
     * <p>"Classes not found" and "sources not found" look alike in an empty report, and are
     * fixed in two opposite ways. Telling them apart requires knowing what the tool
     * actually opened: which directories, which jars, and which classes it saw in them. So
     * for each classpath entry we list the class names it carries — enough to answer, in
     * the page, "where is this class?" without running anything again.
     *
     * <p>Inner classes ({@code Something$1}) are left out: they have no source file of
     * their own, and they would treble the list without teaching anything.
     */
    static List<Object> bytecode(Map<String, Object> context) {
        List<Object> out = new ArrayList<>();
        if (context == null) return out;
        Object roots = context.get("racinesClasses");
        if (!(roots instanceof Iterable<?> list)) return out;

        int budget = MAX_CLASSES;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> root)) continue;
            String path = String.valueOf(root.get("absolu"));
            Path p = Path.of(path);
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("chemin", String.valueOf(root.get("chemin")));
            view.put("absolu", path);
            boolean jar = Files.isRegularFile(p);
            view.put("type", jar ? "jar" : Files.isDirectory(p) ? "directory" : "missing");
            view.put("existe", Files.exists(p));

            List<Object> classes = new ArrayList<>();
            boolean truncated = false;
            try {
                for (String name : jar ? classesInJar(p) : classesInDir(p)) {
                    if (classes.size() >= budget) { truncated = true; break; }
                    classes.add(name);
                }
            } catch (IOException e) {
                view.put("motif", "lecture impossible : " + e.getMessage());
            }
            budget -= classes.size();
            view.put("classes", classes);
            view.put("nombre", classes.size());
            view.put("tronque", truncated);
            out.add(view);
        }
        return out;
    }

    private static List<String> classesInJar(Path jar) throws IOException {
        List<String> names = new ArrayList<>();
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jar.toFile())) {
            for (java.util.Enumeration<? extends java.util.zip.ZipEntry> e = zip.entries();
                 e.hasMoreElements(); ) {
                String name = e.nextElement().getName();
                if (kept(name)) names.add(name.substring(0, name.length() - ".class".length()));
            }
        }
        names.sort(null);
        return names;
    }

    private static List<String> classesInDir(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return List.of();
        List<String> names = new ArrayList<>();
        try (java.util.stream.Stream<Path> files = Files.walk(dir)) {
            for (Path f : (Iterable<Path>) files::iterator) {
                String rel = dir.relativize(f).toString().replace('\\', '/');
                if (kept(rel)) names.add(rel.substring(0, rel.length() - ".class".length()));
            }
        }
        names.sort(null);
        return names;
    }

    /** A top-level class, and not a compiler artefact. */
    private static boolean kept(String name) {
        return name.endsWith(".class") && !name.contains("$") && !name.endsWith("package-info.class")
                && !name.startsWith("META-INF/");
    }

    /** One sentence, the one that would be read first. */
    private static String conclusion(int expected, int missing, Sources.Index index) {
        if (index.roots().isEmpty()) {
            return "no source directory was given: the annotated code cannot be shown. "
                 + "Set SOURCE_DIRS in the configuration, or --sources on the command line.";
        }
        if (index.files() == 0) {
            return "the roots given yielded no .java file — see sources.racines for the "
                 + "absolute path of each one and the reason.";
        }
        if (expected == 0) {
            return "no coverage to match: the measurement recorded nothing.";
        }
        if (missing == 0) {
            return "every measured class has its source.";
        }
        if (missing == expected) {
            return "no measured class has its source, although " + index.files()
                 + " .java file(s) were read: the sources found are not those of the "
                 + "application analysed, or they are only a part of them.";
        }
        return missing + " measured class(es) out of " + expected + " have no source.";
    }

    private static List<Object> runs(List<Object> runs) {
        List<Object> out = new ArrayList<>();
        for (Object r : runs) {
            if (!(r instanceof Map<?, ?> run)) continue;
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("nom", run.get("nom"));
            e.put("uuid", run.get("uuid"));
            e.put("chemin", run.get("chemin"));
            e.put("rapports", run.get("rapports"));
            e.put("paquetsMasques", run.get("paquetsMasques"));
            e.put("contexte", run.get("context"));
            // Sizes, not contents: enough to see that a measurement is empty.
            e.put("classesMesurees", count(run.get("packages")));
            e.put("fichiersCouverts", size(run.get("coverage")));
            e.put("methodesInspectees", size(run.get("values")));
            out.add(e);
        }
        return out;
    }

    private static int count(Object packages) {
        int n = 0;
        if (packages instanceof Map<?, ?> m) {
            for (Object v : m.values()) {
                if (v instanceof java.util.Collection<?> c) n += c.size();
            }
        }
        return n;
    }

    private static int size(Object o) {
        return o instanceof Map<?, ?> m ? m.size() : 0;
    }

    private static Map<String, Object> environment() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("java", System.getProperty("java.version"));
        m.put("jvm", System.getProperty("java.vm.name"));
        m.put("javaHome", System.getProperty("java.home"));
        m.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " (" + System.getProperty("os.arch") + ")");
        m.put("repertoireCourant", System.getProperty("user.dir"));
        // The encoding and the locale explain symptoms usually blamed on the code:
        // unreadable accents, sources refused on reading, unexpected decimal separators.
        m.put("encodageFichiers", System.getProperty("file.encoding"));
        m.put("encodageSortie", System.getProperty("stdout.encoding"));
        m.put("locale", Locale.getDefault().toLanguageTag());
        m.put("separateurChemin", java.io.File.separator);
        return m;
    }

    /** The version as the jar declares it, or "unknown" when running on the classes. */
    private static String version() {
        String v = Diagnostic.class.getPackage().getImplementationVersion();
        return v == null ? "inconnue" : v;
    }
}
