package lab.xray;

import lab.xray.report.PackageFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The settings of one analysis.
 *
 * <p>The configuration file is a plain {@code key="value"} per line — no YAML, no TOML:
 * the format must read and write by hand without documentation, and read back here
 * without a library.
 *
 * <p>When the expected file does not exist, a {@linkplain #writeTemplate commented
 * template} is written rather than an error shown: nobody should have to guess key names.
 */
public final class Config {

    public String javaCommand = "";
    public String rootMethod = "";
    /**
     * Where to find the analysed bytecode: class directories <b>and/or</b> jar archives,
     * separated by {@code :} or {@code ,}.
     *
     * <p>Jars count as much as directories: the code one is trying to understand is often
     * an internal dependency delivered in that form — a shared module, a home-grown library
     * — and it is precisely the one nobody has a mental model of. The coverage tool can
     * read both; there was no reason to accept only one.
     */
    public String classesDir = "";
    /**
     * Packages to keep quiet about, on the same footing as the JDK —
     * {@code org.slf4j, io.netty}…
     *
     * <p>See {@link PackageFilter} for what "keep quiet about" means exactly (the time is
     * carried over to the caller, it is not lost).
     */
    public String hiddenPackages = "";
    public String sourceDirs = "";
    public String classFilter = "";
    public String outDir = "runtime-xray-out";
    public String runName = "";
    public int attachAfterSeconds = 8;
    public int maxSeconds = 600;
    public int watchCount = 10;
    /**
     * What one accepts to pay in order to observe, in order of priority of the information.
     *
     * <p>On enterprise code, measuring everything at once is not always tenable: coverage
     * instruments every class loaded, sampling wakes the JVM a thousand times a second, and
     * value capture intercepts every entry into a method. The three pieces of information
     * are not worth the same, though: knowing <b>what ran</b> comes before knowing <b>who
     * calls whom</b>, which comes before <b>with which values</b>. The level chosen says
     * how far one goes.
     *
     * <ul>
     *   <li>{@code coverage} — JaCoCo alone;</li>
     *   <li>{@code tree} — plus stack sampling;</li>
     *   <li>{@code full} — plus value capture (the default).</li>
     * </ul>
     */
    public String level = "complet";

    /** The three levels, under their internal name. */
    public static final java.util.List<String> LEVELS =
            java.util.List.of("couverture", "arbre", "complet");

    /**
     * The level brought back to its internal name.
     *
     * <p>The tool speaks English: {@code coverage}, {@code tree}, {@code full}. The French
     * names stay accepted, for life and undocumented — a script deployed before the switch
     * must never stop working over a question of language.
     */
    public static String level(String value) {
        String v = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (v) {
            case "coverage" -> "couverture";
            case "tree" -> "arbre";
            case "full" -> "complet";
            default -> v;
        };
    }
    /**
     * Classes JaCoCo instruments, in its agent's format — {@code com.example.*}, several
     * patterns separated by {@code :}. Empty: everything the JVM loads, third-party
     * libraries included, which is the most expensive and rarely the most useful.
     */
    public String coverIncludes = "";
    /**
     * The stack sampling interval, in milliseconds. Raising it is the most direct lever on
     * the profile's cost: at 10 ms, ten times fewer samples than at 1 ms.
     */
    public int sampleIntervalMs = 1;

    /**
     * The follow page's port, or 0 to serve nothing.
     *
     * <p>Zero by default, and deliberately so: {@code progression.jsonl} is written anyway,
     * and opening a port without being asked to is a decision one does not take on the
     * operator's behalf.
     */
    public int followPort = 0;
    /**
     * Rewrite formats requested — {@code perf}, {@code cpuprofile}, {@code lcov},
     * {@code values}, or {@code all}. Empty: no export, and nothing written extra.
     */
    public String exportFormats = "";
    /**
     * How many invocations have their call tree traced.
     *
     * <p>Since each invocation takes different branches, this number decides how many lines
     * of code can be annotated: with only one, one sees the path of that call alone. The
     * cost is low — one traced invocation makes a few dozen lines of output — so the
     * default is generous.
     */
    public int traceCount = 10;
    public boolean captureValues = true;
    /** The Maven repository to fetch the components from. An internal mirror is enough. */
    public String mavenRepo = "https://repo1.maven.org/maven2";
    /**
     * Directory to look in for components already present on the machine, before any
     * network access. What it holds takes priority over what might be downloaded — it is
     * the gesture of whoever brought them along.
     */
    public String componentsDir = "";

    public static Config load(Path file) throws IOException {
        Config c = new Config();
        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            String key = line.substring(0, eq).trim();
            String value = unquote(line.substring(eq + 1).trim());
            c.set(key, value);
        }
        return c;
    }

    private static String unquote(String v) {
        // A trailing comment is cut off, but only outside quotes.
        if (v.startsWith("\"")) {
            int end = v.indexOf('"', 1);
            return end > 0 ? v.substring(1, end) : v.substring(1);
        }
        int hash = v.indexOf('#');
        return (hash >= 0 ? v.substring(0, hash) : v).trim();
    }

    void set(String key, String value) {
        switch (key) {
            case "JAVA_CMD" -> javaCommand = value;
            case "ROOT_METHOD" -> rootMethod = value;
            case "CLASSES_DIR" -> classesDir = value;
            case "HIDDEN_PACKAGES" -> hiddenPackages = value;
            case "SOURCE_DIRS" -> sourceDirs = value;
            case "CLASS_FILTER" -> classFilter = value;
            case "OUT_DIR" -> outDir = value;
            case "RUN_NAME" -> runName = value;
            case "MAVEN_REPO" -> mavenRepo = value;
            case "COMPONENTS", "COMPOSANTS", "COMPONENTS_DIR" -> componentsDir = value;
            case "ATTACH_AFTER" -> attachAfterSeconds = parse(value, attachAfterSeconds);
            case "MAX_SECONDS" -> maxSeconds = parse(value, maxSeconds);
            case "WATCH_COUNT" -> watchCount = parse(value, watchCount);
            case "EXPORT" -> exportFormats = value;
            case "LEVEL", "NIVEAU" -> level = value;
            case "COVER_INCLUDES" -> coverIncludes = value;
            case "SAMPLE_INTERVAL_MS" -> sampleIntervalMs = parse(value, sampleIntervalMs);
            case "FOLLOW_PORT", "SUIVI_PORT" -> followPort = parse(value, followPort);
            case "TRACE_COUNT" -> traceCount = parse(value, traceCount);
            default -> { /* an unknown key is not an error: the file can serve other purposes */ }
        }
    }

    private static int parse(String v, int fallback) {
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** The profile filter is deduced from the root method's package, failing an instruction. */
    public String effectiveFilter() {
        if (!classFilter.isBlank()) return classFilter;
        if (rootMethod.isBlank()) return "";
        String cls = rootMethod.split("::")[0];
        int dot = cls.lastIndexOf('.');
        return dot < 0 ? "" : cls.substring(0, dot).replace('.', '/') + "/*";
    }

    /** The entries of {@link #classesDir}, directories or jars, in the order given. */
    public List<Path> classesPaths() {
        return paths(classesDir);
    }

    /**
     * A list of paths as it is written in the configuration — <b>Windows drive letters
     * included</b>.
     *
     * <p>The documented separator is {@code :}, which goes without saying on Unix and not
     * at all on Windows: {@code C:\project\src} starts there with a {@code :} that is not a
     * separator. Split naively, that path gave two entries — {@code C} and
     * {@code \project\src} — neither of which exists, and the tool concluded "directory not
     * found" about a perfectly valid path the user had in front of them.
     *
     * <p>A {@code :} that follows a single letter at the start of a segment and precedes a
     * path separator is therefore given back to the path. {@code ;} — Windows's native
     * separator — and {@code ,} are accepted as well, because that is what a Windows user
     * will write spontaneously.
     */
    public static List<Path> paths(String value) {
        List<Path> paths = new ArrayList<>();
        for (String piece : split(value)) {
            if (!piece.isBlank()) paths.add(Path.of(piece.trim()));
        }
        return paths;
    }

    /** The splitting alone, without interpretation: it is what the tests exercise. */
    static List<String> split(String value) {
        List<String> out = new ArrayList<>();
        if (value == null) return out;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean separates = (c == ',' || c == ';')
                    || (c == ':' && !driveLetter(value, i));
            if (separates) {
                out.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        out.add(current.toString());
        return out;
    }

    /**
     * True when this {@code :} belongs to a Windows drive, and is not a separator.
     *
     * <p>Three conditions, and all three are needed: a letter just before, that letter at
     * the start of a segment, and a path separator just after. {@code C:\x} is a drive;
     * {@code src:other} is not, nor is {@code ab:c}.
     */
    private static boolean driveLetter(String v, int i) {
        if (i < 1 || !Character.isLetter(v.charAt(i - 1))) return false;
        if (i >= 2) {
            char before = v.charAt(i - 2);
            boolean segmentStart = before == ',' || before == ';' || before == ':'
                    || Character.isWhitespace(before);
            if (!segmentStart) return false;
        }
        return i + 1 < v.length() && (v.charAt(i + 1) == '\\' || v.charAt(i + 1) == '/');
    }

    /** True when the level requested goes as far as stack sampling. */
    public boolean profileWanted() {
        return !"couverture".equals(level(level));
    }

    /** True when the level requested goes as far as value capture. */
    public boolean valuesWanted() {
        String l = level.trim();
        return captureValues && ("complet".equals(level(l)) || l.isBlank());
    }

    public PackageFilter hidden() {
        return PackageFilter.of(hiddenPackages);
    }

    public String rootClass() {
        return rootMethod.isBlank() ? "" : rootMethod.split("::")[0];
    }

    public Map<String, Object> describe() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("commande", javaCommand);
        m.put("methodeRacine", rootMethod.isBlank() ? null : rootMethod);
        m.put("filtreClasses", effectiveFilter().isBlank() ? null : effectiveFilter());
        m.put("repertoireClasses", classesDir);
        m.put("paquetsMasques", hiddenPackages.isBlank() ? null : hiddenPackages);
        m.put("repertoiresSources", sourceDirs.isBlank() ? null : sourceDirs);
        m.put("valeursInspectees", valuesWanted() && !rootMethod.isBlank());
        // The level and its settings travel with the run: without them, a report less
        // full than another reads as a failed measurement rather than as a choice.
        m.put("niveau", level);
        m.put("classesInstrumentees", coverIncludes.isBlank() ? null : coverIncludes);
        m.put("intervalleMs", sampleIntervalMs);
        return m;
    }

    // ------------------------------------------------------------------- gabarit

    public static void writeTemplate(Path file) throws IOException {
        Files.writeString(file, TEMPLATE, StandardCharsets.UTF_8);
    }

    private static final String TEMPLATE = """
            # ---------------------------------------------------------------------------
            # Runtime X-Ray — configuration
            #
            #   java -jar runtime-xray.jar --config ce-fichier.conf
            #
            # Seule JAVA_CMD est obligatoire. Le reste a des valeurs par défaut raisonnables,
            # et les lignes commentées montrent d'autres usages.
            # ---------------------------------------------------------------------------

            # ── Comment lancer l'application ────────────────────────────────── OBLIGATOIRE
            # Aucune contrainte : la commande est exécutée telle quelle. Les agents d'analyse
            # sont injectés par JAVA_TOOL_OPTIONS, que toute JVM lit à son démarrage.
            JAVA_CMD="java -jar target/mon-appli.jar"
            #JAVA_CMD="java -Xmx2g -jar target/mon-appli.jar --profil recette --jeu 42"
            #JAVA_CMD="mvn -q exec:java -Dexec.mainClass=com.example.Main"
            #JAVA_CMD="./gradlew run --args='--profil recette'"
            #JAVA_CMD="./scripts/demarrer-en-recette.sh"

            # ── Les classes compilées ──────────────────────────────────────────── facultatif
            # Normalement INUTILE : le bytecode analysé est lu sur les arguments réels de la
            # JVM observée, et à défaut déduit de la disposition du projet (target/classes,
            # build/classes/java/main…). Les jar de dépendances sont écartés à dessein.
            #
            # Ne l'écrire que pour analyser AUTRE CHOSE : une dépendance interne livrée
            # compilée, un jar « gras », ou un module précis.
            # Répertoires ET archives jar sont acceptés, séparés par ':' (ou ';' sous
            # Windows, où un chemin commence par « C: »). Ajouter le jar
            # d'une dépendance interne la fait entrer dans l'analyse au même titre que le
            # code du projet — c'est souvent elle que l'on cherche à comprendre.
            #CLASSES_DIR="target/classes:libs/module-commun-3.2.jar"  # + une dépendance
            #CLASSES_DIR="target/mon-appli-boot.jar"                   # un jar « gras »
            #CLASSES_DIR="modules/facturation/target/classes"          # un module précis

            # ── Les paquets à ne pas voir ───────────────────────────────────── facultatif
            # Le JDK est toujours masqué : personne n'ouvre java.util.ArrayList pour
            # comprendre son application. La même chose vaut pour des bibliothèques qui n'en
            # font pas partie mais que l'on considère comme de l'infrastructure — un
            # journal, un client HTTP, un cadriciel d'injection.
            #
            # Où passe cette frontière dépend de ce que l'équipe possède et de ce qu'elle
            # subit : c'est un choix, pas une règle, d'où ce réglage.
            #
            # Le temps des paquets masqués n'est pas perdu : il est attribué à la méthode
            # applicative qui les a appelés, exactement comme pour le JDK.
            #HIDDEN_PACKAGES="org.slf4j, ch.qos.logback"
            #HIDDEN_PACKAGES="org.slf4j, io.netty, org.springframework, com.fasterxml"

            # ── La méthode racine ───────────────────────────────────────────── recommandé
            # La fonction dont on veut voir les valeurs des paramètres et l'arbre d'un appel.
            # Format paquet.Classe::methode. Choisir un point d'entrée MÉTIER : un traitement,
            # un calcul, une commande — pas un main, pas un accesseur.
            # Laisser vide pour n'obtenir que la couverture et les temps.
            ROOT_METHOD="com.example.moteur.Calculateur::calculer"
            #ROOT_METHOD="com.example.api.CommandeService::valider"
            #ROOT_METHOD=""

            # ── Les sources ─────────────────────────────────────────────────── recommandé
            # Pour afficher le code annoté. Plusieurs racines : séparées par ':' — ou par
            # ';' sous Windows, où un chemin absolu commence lui-même par « C: ».
            SOURCE_DIRS="src/main/java"
            #SOURCE_DIRS="src/main/java:src/generated/java"

            # ── Le nom de cette exécution ───────────────────────────────────── facultatif
            # Les exécutions s'accumulent dans <OUT_DIR>/runs/ et la vue permet de passer de
            # l'une à l'autre. Un nom parlant vaut mieux qu'un horodatage.
            # Il reste modifiable après coup, sans relancer : voir OUT_DIR/noms.json.
            #RUN_NAME="recette v2"
            #RUN_NAME="incident 4712"

            # ── Le filtre de profil ─────────────────────────────────────────── facultatif
            # Restreint les mesures de temps au code applicatif. Sans lui, la majorité des
            # relevés concernent le compilateur interne de la JVM : exact, mais illisible.
            # Déduit du paquet de ROOT_METHOD s'il est absent.
            #CLASS_FILTER="com/example/*"

            # ── Sortie et garde-fous ────────────────────────────────────────── facultatif
            OUT_DIR="runtime-xray-out"

            # Délai avant d'inspecter les valeurs. L'application doit avoir démarré ET être
            # encore en train de travailler. Si elle est trop rapide, augmenter sa charge
            # plutôt que de réduire ce délai : un relevé sur deux secondes ne dit rien.
            ATTACH_AFTER=8

            # Au-delà, l'exécution est interrompue et les rapports sont tout de même produits.
            MAX_SECONDS=600

            # Nombre d'appels dont on capture les valeurs, toutes méthodes de la classe racine.
            WATCH_COUNT=10

            # Nombre d'invocations dont on trace l'arbre d'appel. Chaque invocation emprunte
            # des branches différentes : plus il y en a, plus de lignes du code portent
            # l'annotation « appelle … ». Le coût est faible.
            TRACE_COUNT=10

            # ── L'empreinte sur l'application observée ──────────────────────── facultatif
            # Trois informations, pas la même valeur ni le même coût : ce qui a tourné passe
            # avant qui appelle qui, qui passe avant avec quelles valeurs. NIVEAU dit jusqu'où
            # on va, et c'est le premier réglage à baisser quand la mesure devient trop chère.
            #
            #   couverture  JaCoCo seul — le moins cher, et l'information la plus sûre
            #   arbre       + l'échantillonnage des piles
            #   complet     + la capture des valeurs (défaut)
            #NIVEAU="arbre"

            # Classes que JaCoCo instrumente, au format de son agent (motifs séparés par ':').
            # Sans ce réglage, TOUTE classe chargée est instrumentée, dépendances comprises :
            # c'est le poste de coût principal sur une application d'entreprise.
            #COVER_INCLUDES="com.example.*:com.example.commun.*"

            # Intervalle d'échantillonnage des piles, en millisecondes. Le multiplier par dix
            # divise par dix le nombre de relevés — et le coût qui va avec.
            #SAMPLE_INTERVAL_MS=10

            # Réécriture des mesures pour d'autres outils : perf, cpuprofile, lcov, valeurs,
            # ou « tout ». Les fichiers vont dans <exécution>/exports/.
            #EXPORT="cpuprofile,lcov"

            # Servir le rapport ne se règle pas ici : c'est un mode de lancement, pas une
            # propriété du projet. « --serve » sert le répertoire de sortie et laisse la page
            # écrire ses annotations à côté des exécutions ; « --serve-host 0.0.0.0 » en fait
            # un serveur partagé, où plusieurs personnes annotent en parallèle, que
            # « --serve-token » ferme par un secret (XRAY_SERVE_TOKEN pour ne pas l'exposer
            # dans « ps »). Un secret ne se range pas dans un fichier suivi en version.

            # Dépôt d'où récupérer les composants d'analyse, une seule fois. Sur un réseau
            # fermé, indiquer le miroir interne : c'est le seul réglage qui compte pour
            # fonctionner sans accès à Internet.
            #MAVEN_REPO="https://nexus.interne.exemple.com/repository/maven-public"
            """;
}
