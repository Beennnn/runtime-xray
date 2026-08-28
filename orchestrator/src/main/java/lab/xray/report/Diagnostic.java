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
 * Ce qu'il faut savoir quand la vue ne montre pas ce qu'on attendait.
 *
 * <p>Un rapport qui manque quelque chose ne dit pas pourquoi : un panneau de code vide se
 * lit exactement comme un panneau de code qu'on n'a pas su remplir. Le 26 août 2026, une
 * analyse portant sur 447 classes affichait « Source indisponible » sur chacune, et rien
 * dans le rapport ne permettait de trancher entre les quatre causes possibles — racine non
 * renseignée, racine inexistante, racine au mauvais niveau, ou sources réellement absentes
 * de la machine. Il a fallu demander une capture d'écran.
 *
 * <p>D'où ce fichier. Il est écrit <b>à chaque assemblage</b>, à côté de la page, et il est
 * fait pour être <b>renvoyé tel quel</b> : il porte ce qu'on aurait posé comme questions.
 * Ce qui a été demandé, ce qui a été trouvé, où, et — quand un rapprochement échoue — ce
 * qu'il aurait fallu écrire pour qu'il réussisse.
 *
 * <p><b>Il ne contient aucun secret</b> : ni le jeton du serveur partagé, ni les valeurs de
 * paramètres capturées. Il porte en revanche des chemins absolus et des noms de classes,
 * comme le rapport lui-même — c'est un fichier à faire circuler avec le même soin.
 */
public final class Diagnostic {

    /** Assez pour comprendre, pas assez pour recopier le rapport. */
    private static final int EXAMPLES = 40;

    /**
     * Le nombre de classes recensées dans le bytecode, toutes racines confondues.
     *
     * <p>Cette liste voyage dans la page — c'est elle qui alimente l'arbre et la recherche.
     * Un classpath applicatif en compte quelques centaines à quelques milliers ; au-delà on
     * s'arrête, et on le dit, plutôt que de faire une page de plusieurs mégaoctets.
     */
    private static final int MAX_CLASSES = 4_000;

    private Diagnostic() {}

    /**
     * @param commonDir  le répertoire de sortie, où le fichier est déposé
     * @param runs       les exécutions telles que la vue les reçoit
     * @param index      l'index des sources, avec ce qu'il a vu en le construisant
     * @param contexte   ce que seul l'appelant sait — configuration, componentsDir, options —
     *                   ou {@code null} quand la vue est réassemblée sans lui
     * @return le contenu écrit, pour que l'appelant puisse en tirer un résumé sans le relire
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
        // La recherche ne part que s'il y a quelque chose à chercher : elle parcourt le
        // disque, et sur un rapport complet elle n'aurait rien à trouver.
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
     * Le rapprochement couverture ↔ sources, classe par classe.
     *
     * <p>C'est le cœur du fichier : la couverture énumère les fichiers qu'elle a mesurés,
     * l'index énumère ceux qu'on a lus, et l'intersection est ce que la vue saura montrer.
     * Pour chaque fichier manquant on cherche le même nom ailleurs dans l'index — c'est le
     * cas d'une racine mal placée, et il se corrige d'un chemin.
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
        // Les fichiers lus qui ne correspondent à aucune classe mesurée : leur nombre suffit
        // à la page, qui n'a rien à en montrer. Les énumérer y recopierait l'arborescence.
        int withoutClass = 0;
        for (String key : index.byKey().keySet()) if (!expected.contains(key)) withoutClass++;
        m.put("sourcesSansClasse", withoutClass);
        return m;
    }

    /**
     * Les fichiers que la couverture dit avoir mesurés, toutes exécutions confondues.
     *
     * <p>Toutes, et non celle qu'on regarde : une classe absente d'une exécution mais
     * présente dans une autre reste une classe dont on veut le code.
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

    /** Les clés que la couverture réclame et que l'index n'a pas. */
    static java.util.Set<String> missing(List<Object> runs, Sources.Index index) {
        java.util.Set<String> out = new LinkedHashSet<>();
        for (String key : samples(runs)) {
            if (!index.byKey().containsKey(key)) out.add(key);
        }
        return out;
    }

    /**
     * Où chercher les sources qui manquent, du plus probable au moins probable.
     *
     * <p>Aucun de ces endroits n'est une convention : ce sont les seuls que l'exécution nous
     * ait fait connaître. Le bytecode analysé est le meilleur indice de tous — un
     * {@code <projet>/target/classes} désigne le projet à deux répertoires près, et c'est là
     * que sont ses sources. La racine déjà configurée en est un autre : quand elle est d'un
     * cran à côté, le bon répertoire est son voisin immédiat.
     *
     * <p>On ne remonte jamais plus haut que ces indices, et jamais vers la racine du disque :
     * une recherche qui balaie tout finirait par proposer les sources d'un autre projet.
     */
    static List<Path> whereToLook(Path commonDir, Sources.Index index, List<Object> bytecode) {
        List<Path> bases = new ArrayList<>();
        // 1. autour du bytecode réellement analysé
        for (Object o : bytecode) {
            if (o instanceof Map<?, ?> b && b.get("absolu") instanceof String path) {
                climb(Path.of(path), 2, bases);
            }
        }
        // 2. autour des racines de sources déjà données — le cas « d'un cran à côté »
        for (Object o : index.roots()) {
            if (o instanceof Map<?, ?> r && r.get("absolue") instanceof String path) {
                climb(Path.of(path), 1, bases);
            }
        }
        // 3. le répertoire depuis lequel l'analyse a été lancée
        bases.add(Path.of(System.getProperty("user.dir")));
        // 4. à défaut, le voisinage du rapport lui-même
        climb(commonDir, 1, bases);
        return bases;
    }

    /** Un chemin et ses ascendants, jusqu'à {@code crans} — jamais au-delà. */
    private static void climb(Path start, int notches, List<Path> bases) {
        Path p = start.toAbsolutePath().normalize();
        if (Files.isRegularFile(p)) p = p.getParent();          // un jar désigne son répertoire
        for (int i = 0; i <= notches && p != null && p.getParent() != null; i++) {
            bases.add(p);
            p = p.getParent();
        }
    }

    private static long number(Object o) {
        return o instanceof Number n ? n.longValue() : 0;
    }

    /**
     * Un fichier mesuré dont on n'a pas la source — et ce qu'on sait d'approchant.
     *
     * <p>Le même nom trouvé sous un autre paquet est presque toujours le bon fichier vu
     * depuis une racine décalée. On donne alors le chemin réel : c'est de lui qu'on déduit
     * la racine à passer.
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
     * Ce que chaque racine de bytecode contient réellement.
     *
     * <p>« Classes introuvables » et « sources introuvables » se ressemblent dans un rapport
     * vide, et se corrigent de deux façons opposées. Les séparer demande de savoir ce que
     * l'outil a effectivement ouvert : quels répertoires, quels jar, et quelles classes il y
     * a vues. On recense donc, pour chaque entrée du classpath, les noms de classes qu'elle
     * porte — de quoi répondre, dans la page, à « où est cette classe ? » sans rien relancer.
     *
     * <p>Les classes internes ({@code Machin$1}) sont écartées : elles n'ont pas de fichier
     * source à elles, et elles tripleraient la liste sans rien apprendre.
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

    /** Une classe de premier niveau, et pas un artefact du compilateur. */
    private static boolean kept(String name) {
        return name.endsWith(".class") && !name.contains("$") && !name.endsWith("package-info.class")
                && !name.startsWith("META-INF/");
    }

    /** Une phrase, celle qu'on lirait en premier. */
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
            // Des tailles, pas des contenus : de quoi voir qu'une mesure est vide.
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
        // L'encodage et la locale expliquent des symptômes qu'on impute d'ordinaire au code :
        // accents illisibles, sources refusées à la lecture, séparateurs décimaux inattendus.
        m.put("encodageFichiers", System.getProperty("file.encoding"));
        m.put("encodageSortie", System.getProperty("stdout.encoding"));
        m.put("locale", Locale.getDefault().toLanguageTag());
        m.put("separateurChemin", java.io.File.separator);
        return m;
    }

    /** La version telle que le jar la déclare, ou « inconnue » quand on tourne sur les classes. */
    private static String version() {
        String v = Diagnostic.class.getPackage().getImplementationVersion();
        return v == null ? "inconnue" : v;
    }
}
