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
            case "ATTACH_AFTER" -> attachAfterSeconds = parse(value, attachAfterSeconds);
            case "MAX_SECONDS" -> maxSeconds = parse(value, maxSeconds);
            case "WATCH_COUNT" -> watchCount = parse(value, watchCount);
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
        List<Path> paths = new ArrayList<>();
        for (String part : classesDir.split("[:,]")) {
            if (!part.isBlank()) paths.add(Path.of(part.trim()));
        }
        return paths;
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
        m.put("valeursInspectees", captureValues && !rootMethod.isBlank());
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
            # Seules JAVA_CMD et CLASSES_DIR sont obligatoires. Le reste a des valeurs par
            # défaut raisonnables ; les lignes commentées montrent d'autres usages.
            # ---------------------------------------------------------------------------

            # ── Comment lancer l'application ────────────────────────────────── OBLIGATOIRE
            # Aucune contrainte : la commande est exécutée telle quelle. Les agents d'analyse
            # sont injectés par JAVA_TOOL_OPTIONS, que toute JVM lit à son démarrage.
            JAVA_CMD="java -jar target/mon-appli.jar"
            #JAVA_CMD="java -Xmx2g -jar target/mon-appli.jar --profil recette --jeu 42"
            #JAVA_CMD="mvn -q exec:java -Dexec.mainClass=com.exemple.Main"
            #JAVA_CMD="./gradlew run --args='--profil recette'"
            #JAVA_CMD="./scripts/demarrer-en-recette.sh"

            # ── Les classes compilées ───────────────────────────────────────── OBLIGATOIRE
            # Sans elles, pas de couverture : c'est là que se trouve le bytecode analysé.
            # Répertoires ET archives jar sont acceptés, séparés par ':'. Ajouter le jar
            # d'une dépendance interne la fait entrer dans l'analyse au même titre que le
            # code du projet — c'est souvent elle que l'on cherche à comprendre.
            CLASSES_DIR="target/classes"
            #CLASSES_DIR="build/classes/java/main"                    # Gradle
            #CLASSES_DIR="target/classes:libs/module-commun-3.2.jar"  # + une dépendance
            #CLASSES_DIR="target/classes:~/.m2/repository/com/exemple/noyau/1.4/noyau-1.4.jar"

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
            ROOT_METHOD="com.exemple.moteur.Calculateur::calculer"
            #ROOT_METHOD="com.exemple.api.CommandeService::valider"
            #ROOT_METHOD=""

            # ── Les sources ─────────────────────────────────────────────────── recommandé
            # Pour afficher le code annoté. Plusieurs racines : séparées par ':'.
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
            #CLASS_FILTER="com/exemple/*"

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

            # Dépôt d'où récupérer les composants d'analyse, une seule fois. Sur un réseau
            # fermé, indiquer le miroir interne : c'est le seul réglage qui compte pour
            # fonctionner sans accès à Internet.
            #MAVEN_REPO="https://nexus.interne.exemple.com/repository/maven-public"
            """;
}
