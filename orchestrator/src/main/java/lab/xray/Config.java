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
 * Les réglages d'une analyse.
 *
 * <p>Le fichier de configuration est un simple {@code clé="valeur"} par ligne — pas de
 * YAML, pas de TOML : le format doit se lire et s'écrire à la main sans documentation,
 * et se relire ici sans bibliothèque.
 *
 * <p>Quand le fichier attendu n'existe pas, on en écrit un {@linkplain #writeTemplate
 * gabarit commenté} plutôt que d'afficher une erreur : personne ne devrait avoir à
 * deviner des noms de clés.
 */
public final class Config {

    public String javaCommand = "";
    public String rootMethod = "";
    /**
     * Où trouver le bytecode analysé : répertoires de classes <b>et/ou</b> archives jar,
     * séparés par {@code :} ou {@code ,}.
     *
     * <p>Les jar comptent autant que les répertoires : le code que l'on cherche à
     * comprendre est souvent une dépendance interne livrée sous cette forme — un module
     * commun, une bibliothèque maison — et c'est justement celui dont personne n'a le
     * modèle mental. L'outil de couverture sait lire les deux ; il n'y avait aucune raison
     * de n'en accepter qu'un.
     */
    public String classesDir = "";
    /**
     * Paquets à taire, au même titre que le JDK — {@code org.slf4j, io.netty}…
     *
     * <p>Voir {@link PackageFilter} pour ce que « taire » veut dire exactement (le temps
     * est reporté sur l'appelant, il n'est pas perdu).
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
     * Ce qu'on accepte de payer pour observer, par ordre de priorité des informations.
     *
     * <p>Sur un code d'entreprise, tout mesurer d'un coup n'est pas toujours tenable : la
     * couverture instrumente chaque classe chargée, l'échantillonnage réveille la JVM mille
     * fois par seconde, et la capture des valeurs intercepte chaque entrée d'une méthode.
     * Les trois informations n'ont pourtant pas la même valeur : savoir <b>ce qui a tourné</b>
     * passe avant savoir <b>qui appelle qui</b>, qui passe avant <b>avec quelles valeurs</b>.
     * Le niveau retenu dit jusqu'où on va.
     *
     * <ul>
     *   <li>{@code couverture} — JaCoCo seul ;</li>
     *   <li>{@code arbre} — plus l'échantillonnage des piles ;</li>
     *   <li>{@code complet} — plus la capture des valeurs (défaut).</li>
     * </ul>
     */
    public String level = "complet";

    /** Les trois niveaux, sous leur nom interne. */
    public static final java.util.List<String> LEVELS =
            java.util.List.of("couverture", "arbre", "complet");

    /**
     * Le niveau ramené à son nom interne.
     *
     * <p>L'outil parle anglais : {@code coverage}, {@code tree}, {@code full}. Les noms
     * français restent acceptés, à vie et sans être documentés — un script déployé avant
     * la bascule ne doit jamais cesser de marcher pour une question de langue.
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
     * Classes que JaCoCo instrumente, au format de son agent — {@code com.example.*}, plusieurs
     * motifs séparés par {@code :}. Vide : tout ce que la JVM charge, y compris les
     * bibliothèques tierces, ce qui est le plus coûteux et rarement le plus utile.
     */
    public String coverIncludes = "";
    /**
     * Intervalle d'échantillonnage des piles, en millisecondes. L'augmenter est le levier le
     * plus direct sur le coût du profil : à 10 ms, dix fois moins de relevés qu'à 1 ms.
     */
    public int sampleIntervalMs = 1;

    /**
     * Le port de la page de suivi, ou 0 pour ne rien servir.
     *
     * <p>Zéro par défaut, et c'est délibéré : {@code progression.jsonl} s'écrit de toute
     * façon, et ouvrir un port sans qu'on l'ait demandé est une décision qui ne se prend
     * pas à la place de l'exploitant.
     */
    public int followPort = 0;
    /**
     * Formats de réécriture demandés — {@code perf}, {@code cpuprofile}, {@code lcov},
     * {@code valeurs}, ou {@code tout}. Vide : aucun export, et rien d'écrit en plus.
     */
    public String exportFormats = "";
    /**
     * Nombre d'invocations dont on trace l'arbre d'appel.
     *
     * <p>Chaque invocation empruntant des branches différentes, ce nombre détermine
     * combien de lignes du code pourront être annotées : avec une seule, on ne voit que
     * le chemin de cet appel-là. Le coût est faible — une invocation tracée fait quelques
     * dizaines de lignes de sortie — donc la valeur par défaut est généreuse.
     */
    public int traceCount = 10;
    public boolean captureValues = true;
    /** Dépôt Maven d'où récupérer les composants. Un miroir interne suffit. */
    public String mavenRepo = "https://repo1.maven.org/maven2";
    /**
     * Répertoire où chercher les composants déjà présents sur la machine, avant tout
     * accès au réseau. Ce qu'il contient prime sur ce qui pourrait être téléchargé —
     * c'est le geste de celui qui les a apportés lui-même.
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
        // On coupe un éventuel commentaire de fin de ligne, mais seulement hors guillemets.
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
            default -> { /* une clé inconnue n'est pas une erreur : le fichier peut servir à autre chose */ }
        }
    }

    private static int parse(String v, int fallback) {
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Le filtre de profil se déduit du paquet de la méthode racine, faute de consigne. */
    public String effectiveFilter() {
        if (!classFilter.isBlank()) return classFilter;
        if (rootMethod.isBlank()) return "";
        String cls = rootMethod.split("::")[0];
        int dot = cls.lastIndexOf('.');
        return dot < 0 ? "" : cls.substring(0, dot).replace('.', '/') + "/*";
    }

    /** Les entrées de {@link #classesDir}, répertoires ou jar, dans l'ordre donné. */
    public List<Path> classesPaths() {
        return paths(classesDir);
    }

    /**
     * Une liste de chemins telle qu'on l'écrit dans la configuration — <b>lecteur Windows
     * compris</b>.
     *
     * <p>Le séparateur documenté est {@code :}, ce qui va de soi sur Unix et ne va pas du tout
     * sur Windows : {@code C:\projet\src} y commence par un {@code :} qui n'est pas un
     * séparateur. Découpé bêtement, ce chemin donnait deux entrées — {@code C} et
     * {@code \projet\src} — dont aucune n'existe, et l'outil concluait « répertoire
     * introuvable » sur un chemin parfaitement valide que l'utilisateur avait sous les yeux.
     *
     * <p>Le {@code :} qui suit une lettre seule en tête de segment et précède un séparateur de
     * chemin est donc rendu au chemin. {@code ;} — le séparateur natif de Windows — et
     * {@code ,} sont acceptés en plus, parce que c'est ce qu'un utilisateur de Windows écrira
     * spontanément.
     */
    public static List<Path> paths(String value) {
        List<Path> paths = new ArrayList<>();
        for (String piece : split(value)) {
            if (!piece.isBlank()) paths.add(Path.of(piece.trim()));
        }
        return paths;
    }

    /** Le découpage seul, sans interprétation : c'est lui que les tests éprouvent. */
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
     * Vrai si ce {@code :} est celui d'un lecteur Windows, et non un séparateur.
     *
     * <p>Trois conditions, et il les faut toutes : une lettre juste avant, cette lettre en
     * tête de segment, et un séparateur de chemin juste après. {@code C:\x} est un lecteur ;
     * {@code src:autre} ne l'est pas, ni {@code ab:c}.
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

    /** Vrai si le niveau demandé va jusqu'à l'échantillonnage des piles. */
    public boolean profileWanted() {
        return !"couverture".equals(level(level));
    }

    /** Vrai si le niveau demandé va jusqu'à la capture des valeurs. */
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
        // Le niveau et ses réglages voyagent avec l'exécution : sans eux, un rapport moins
        // fourni qu'un autre se lit comme une mesure ratée plutôt que comme un choix.
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
