package lab.xray.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The source code, indexed the way the coverage report indexes it:
 * {@code package/File.java}. It is that shared convention which makes it possible to match
 * a line of code with its coverage.
 *
 * <p><b>The key comes from the declared package, not from the directory tree.</b> Indexing
 * by the path relative to the root passed forces one to name exactly the directory above
 * the first package — {@code src/main/java} and not {@code src}, nor
 * {@code src/main/java/com}. A root one notch too high or too low gave a whole index of
 * shifted keys, so no match, so "Source unavailable" on every class at once. Yet the file
 * says itself which package it belongs to, and that declaration is authoritative for
 * JaCoCo as well as for the compiler. Reading it makes the level of the root irrelevant:
 * one can point at the whole project, or at a package directory, and it lands on its feet.
 *
 * <p>The relative path stays the fallback key, for the case — rare, but real in old code —
 * of a file with no package declaration.
 *
 * <p>The index additionally reports <b>what it saw</b>: each root asked for, resolved to an
 * absolute path, existing or not, and the number of files it yielded; then, per file name,
 * where each source was found. Without that, a missing source shows only as an empty
 * panel, which says neither what was being looked for nor where we looked.
 */
public final class Sources {

    /**
     * Beyond that, we stop walking. A badly named root — the root of a disk, a home
     * directory — otherwise takes minutes to traverse, for a useless index. The fact that
     * it was truncated is stated, because it changes how the diagnostic reads.
     */
    private static final int MAX_FILES = 20_000;

    /** {@code package com.example.app;} — the first line of that shape is authoritative. */
    private static final Pattern PACKAGE =
            Pattern.compile("^\\s*package\\s+([\\p{L}_$][\\p{L}\\p{N}_$]*(?:\\s*\\.\\s*[\\p{L}_$][\\p{L}\\p{N}_$]*)*)\\s*;");

    private Sources() {}

    /**
     * What the index holds, and what it took to obtain it.
     *
     * @param byKey     {@code package/File.java} → the file's lines
     * @param roots     one entry per root asked for: what came out of it
     * @param byName    {@code File.java} → the places that name was found in
     * @param truncated true when the walk stopped on the limit
     */
    public record Index(Map<String, Object> byKey, List<Object> roots,
                        Map<String, Object> byName, boolean truncated) {

        public int files() {
            return byKey.size();
        }

        /** The diagnostic alone, without the code: it is what travels in the page and the file. */
        public Map<String, Object> diagnostic() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("racines", roots);
            m.put("fichiers", files());
            m.put("tronque", truncated);
            m.put("parNom", byName);
            return m;
        }
    }

    /**
     * @param roots the roots asked for, in order; a non-existent root is kept in the
     *              diagnostic rather than ignored in silence
     */
    public static Index load(List<Path> roots) {
        Map<String, Object> byKey = new LinkedHashMap<>();
        Map<String, Object> byName = new LinkedHashMap<>();
        List<Object> rootViews = new ArrayList<>();
        boolean truncated = false;

        for (Path root : roots) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("demandee", root.toString());
            view.put("absolue", absolu(root));
            boolean exists = Files.isDirectory(root);
            view.put("existe", exists);
            view.put("fichiers", 0);
            rootViews.add(view);
            if (!exists) {
                view.put("motif", Files.exists(root)
                        ? "this path exists but is not a directory"
                        : "this path does not exist");
                continue;
            }

            int before = byKey.size();
            // Links are followed: a source tree mounted elsewhere, or a linked
            // directory, is a common case on development machines.
            try (Stream<Path> files = Files.walk(root, FileVisitOption.FOLLOW_LINKS)) {
                for (Path p : (Iterable<Path>) files.filter(Sources::isSource)::iterator) {
                    if (byKey.size() >= MAX_FILES) {
                        truncated = true;
                        break;
                    }
                    index(root, p, byKey, byName);
                }
            } catch (IOException e) {
                // An unreadable root must not fail the whole report: we keep it as it is
                // in the diagnostic, and move on to the next.
                view.put("motif", "parcours interrompu : " + e.getMessage());
            }
            view.put("fichiers", byKey.size() - before);
        }
        return new Index(byKey, rootViews, byName, truncated);
    }

    private static boolean isSource(Path p) {
        return p.getFileName() != null && p.getFileName().toString().endsWith(".java");
    }

    private static void index(Path root, Path file,
                                Map<String, Object> byKey, Map<String, Object> byName) {
        List<String> lines;
        try {
            lines = read(file);
        } catch (IOException e) {
            // An unreadable file must not fail the whole report: we report it
            // and carry on.
            System.err.println("   source skipped: " + file + " (" + e.getMessage() + ")");
            return;
        }
        String name = file.getFileName().toString();
        String pkg = declaredPackage(lines);
        String relative = root.relativize(file).toString().replace('\\', '/');
        String key = pkg == null ? relative : pkg.replace('.', '/') + "/" + name;

        byKey.put(key, lines);

        Map<String, Object> where = new LinkedHashMap<>();
        where.put("cle", key);
        where.put("chemin", absolu(file));
        where.put("paquet", pkg);
        where.put("racine", absolu(root));
        // The same file name can live in several packages: that is precisely the
        // case where both must be shown rather than one of them chosen.
        @SuppressWarnings("unchecked")
        List<Object> places = (List<Object>) byName.computeIfAbsent(name, k -> new ArrayList<>());
        places.add(where);
    }

    /**
     * The package this file declares, or {@code null} when it declares none.
     *
     * <p>We stop at the first line that opens the type: beyond it, a {@code package} would
     * no longer be a declaration but text — a string, a comment.
     */
    static String declaredPackage(List<String> lines) {
        for (String line : lines) {
            Matcher m = PACKAGE.matcher(line);
            if (m.find()) return m.group(1).replaceAll("\\s+", "");
            if (line.matches("^\\s*(public\\s+|final\\s+|abstract\\s+|sealed\\s+|non-sealed\\s+)*"
                    + "(class|interface|enum|record)\\s+.*")) {
                return null;
            }
        }
        return null;
    }

    /**
     * UTF-8 first, ISO-8859-1 as the last resort.
     *
     * <p>The sources of an old project are often in ISO-8859-1 or cp1252, and since Java 18
     * the platform's default encoding is UTF-8: retrying with it catches nothing.
     * ISO-8859-1 does — every byte has an image there, so the read never fails. An isolated
     * accent can come out crooked; that is far above the alternative, which was not showing
     * the file at all.
     */
    private static List<String> read(Path p) throws IOException {
        try {
            return Files.readAllLines(p, StandardCharsets.UTF_8);
        } catch (java.nio.charset.CharacterCodingException e) {
            return Files.readAllLines(p, StandardCharsets.ISO_8859_1);
        }
    }

    private static String absolu(Path p) {
        try {
            return p.toAbsolutePath().normalize().toString();
        } catch (RuntimeException e) {
            return p.toString();
        }
    }

    // ================================================================= searching for a root

    /**
     * What we accept to traverse while searching. A real project tree holds a few thousand;
     * beyond that we are looking somewhere other than where we should, and stopping is
     * better than spending a minute on it.
     */
    private static final int SEARCH_BUDGET = 120_000;

    /** How far down to go under each base. Twelve levels cover a deep package. */
    private static final int DEPTH = 12;

    /**
     * What is never traversed: neither a version control system's metadata, nor fetched
     * dependencies, nor bytecode. No useful {@code .java} lives there, and those are the
     * directories that make the walk explode.
     */
    private static final java.util.Set<String> DROPPED = java.util.Set.of(
            ".git", ".svn", ".hg", ".idea", ".vscode", ".gradle", ".m2", ".mvn",
            "node_modules", "classes", "test-classes", "runtime-xray-out");

    /**
     * The roots that ought to be added — found, and <b>proven</b>.
     *
     * <p>Guessing a source directory from a convention would be convenient where it is not
     * needed, and wrong where it would be: on the machine where the application runs far
     * from its code, the convention names nothing, or worse, names other sources — and
     * wrong code shown beside a coverage figure costs more than an empty panel.
     *
     * <p>So we do not guess: we search, and we count. Every {@code .java} met whose
     * <b>name</b> appears among the classes without source is read; if the package it
     * declares produces exactly one missing key, its root is deduced — the path with its
     * package cut off — and credited to it. A proposal then arrives with its figure: "this
     * root would resolve 431 of the 447 missing classes". That is no longer a guess, it is
     * an observation, and it checks at a glance.
     *
     * @param missing the {@code package/File.java} keys the coverage asks for in vain
     * @param bases   where to look, from the most likely to the least
     * @return at most {@link #MAX_LEADS} roots, the most explanatory first
     */
    public static List<Object> searchRoots(java.util.Set<String> missing, List<Path> bases) {
        if (missing.isEmpty() || bases.isEmpty()) return List.of();

        // By file name: it is the only filter that can be applied WITHOUT opening
        // the file, and it discards most candidates for the price of one test.
        java.util.Set<String> names = new java.util.HashSet<>();
        for (String key : missing) names.add(key.substring(key.lastIndexOf('/') + 1));

        Map<String, int[]> credits = new LinkedHashMap<>();
        Map<String, List<String>> evidence = new LinkedHashMap<>();
        int[] budget = { SEARCH_BUDGET };

        for (Path base : deduplicate(bases)) {
            if (budget[0] <= 0) break;
            explore(base, base, 0, names, missing, credits, evidence, budget);
        }

        List<Map.Entry<String, int[]>> classified = new ArrayList<>(credits.entrySet());
        // The most explanatory first; on a tie, the shortest path — it is the one
        // that covers widest, so the one that ages best in a configuration.
        classified.sort((a, b) -> {
            int byCount = Integer.compare(b.getValue()[0], a.getValue()[0]);
            return byCount != 0 ? byCount
                                    : Integer.compare(a.getKey().length(), b.getKey().length());
        });

        List<Object> leads = new ArrayList<>();
        for (Map.Entry<String, int[]> e : classified) {
            if (leads.size() >= MAX_LEADS) break;
            Map<String, Object> lead = new LinkedHashMap<>();
            lead.put("racine", e.getKey());
            lead.put("resout", e.getValue()[0]);
            lead.put("surTotal", missing.size());
            lead.put("exemples", evidence.get(e.getKey()));
            leads.add(lead);
        }
        return leads;
    }

    /** Enough leads for a multi-module project to find itself, not enough to drown. */
    private static final int MAX_LEADS = 5;

    /** Three examples are enough to recognise a project; the count does the rest. */
    private static final int EVIDENCE_PER_LEAD = 3;

    private static void explore(Path searchRoot, Path dir, int level,
                                 java.util.Set<String> names, java.util.Set<String> missing,
                                 Map<String, int[]> credits, Map<String, List<String>> evidence,
                                 int[] budget) {
        if (level > DEPTH || budget[0] <= 0) return;
        try (Stream<Path> children = Files.list(dir)) {
            for (Path child : (Iterable<Path>) children::iterator) {
                if (--budget[0] <= 0) return;
                String name = child.getFileName().toString();
                if (Files.isDirectory(child)) {
                    if (!DROPPED.contains(name) && !name.startsWith(".")) {
                        explore(searchRoot, child, level + 1, names, missing,
                                 credits, evidence, budget);
                    }
                } else if (names.contains(name)) {
                    credit(child, name, missing, credits, evidence);
                }
            }
        } catch (IOException | RuntimeException e) {
            // An unreadable directory — permissions, broken link, vanished mount — has
            // nothing to teach: we move on rather than abandon the search.
        }
    }

    /**
     * The file carries the right name: what remains is whether it carries the right package.
     *
     * <p>It is this test that separates a proposal from a guess. An {@code Application.java}
     * found in another project will not declare {@code com.example.app}, so it will not
     * count.
     */
    private static void credit(Path file, String name, java.util.Set<String> missing,
                                 Map<String, int[]> credits, Map<String, List<String>> evidence) {
        String pkg;
        try {
            pkg = declaredPackage(header(file));
        } catch (IOException e) {
            return;
        }
        if (pkg == null) return;
        String key = pkg.replace('.', '/') + "/" + name;
        if (!missing.contains(key)) return;

        // The root is the path with its package cut off: the directory that should
        // have been named. We give it as it is, even though the level no longer
        // matters to the index — it is what a reader calls "the source directory".
        Path root = file.getParent();
        for (int i = pkg.split("\\.").length; i > 0 && root != null; i--) {
            root = root.getParent();
        }
        if (root == null) return;

        String path = absolu(root);
        credits.computeIfAbsent(path, k -> new int[1])[0]++;
        List<String> examples = evidence.computeIfAbsent(path, k -> new ArrayList<>());
        if (examples.size() < EVIDENCE_PER_LEAD) examples.add(key);
    }

    /**
     * The first lines only.
     *
     * <p>The package declaration is at the top by construction of the language. Reading the
     * whole file to find it would multiply the cost of the search by the size of the
     * project.
     */
    private static List<String> header(Path p) throws IOException {
        List<String> lines = new ArrayList<>();
        try (java.io.BufferedReader r = Files.newBufferedReader(p, StandardCharsets.ISO_8859_1)) {
            String line;
            while (lines.size() < 60 && (line = r.readLine()) != null) lines.add(line);
        }
        return lines;
    }

    /**
     * The search bases, without repetition.
     *
     * <p>Searching under a directory and then under its parent walks the same content
     * twice, and on a deep tree that is what exhausts the budget before anything is found.
     */
    static List<Path> deduplicate(List<Path> bases) {
        List<Path> kept = new ArrayList<>();
        List<Path> sortedList = new ArrayList<>();
        for (Path b : bases) {
            if (b == null) continue;
            Path n = b.toAbsolutePath().normalize();
            if (Files.isDirectory(n) && !sortedList.contains(n)) sortedList.add(n);
        }
        // From shortest to longest: a parent kept already covers its descendants.
        sortedList.sort(java.util.Comparator.comparingInt(x -> x.toString().length()));
        for (Path candidate : sortedList) {
            boolean alreadyCovered = kept.stream().anyMatch(candidate::startsWith);
            if (!alreadyCovered) kept.add(candidate);
        }
        return kept;
    }
}
