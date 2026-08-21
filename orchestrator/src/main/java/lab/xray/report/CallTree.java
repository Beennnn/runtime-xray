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
import java.util.regex.Pattern;

/**
 * Arbre d'appel, reconstruit depuis les piles repliées produites par la mesure de temps.
 *
 * <p>Chaque ligne du fichier est une pile complète suivie d'un compte :
 * {@code Main.main;Service.traiter;Calcul.faire 42}.
 */
public final class CallTree {

    /**
     * Frames de l'outil d'inspection des valeurs. Quand il travaille pendant la mesure, il
     * intercepte chaque entrée et sortie de la méthode observée : ses frames captent alors
     * l'essentiel des relevés et écrasent le profil.
     *
     * <p>On les <b>replie sur leur appelant</b> plutôt que de les afficher : les relevés
     * restent comptés, mais attribués à la méthode applicative qui les a déclenchés, ce qui
     * restitue la forme de l'arbre.
     */
    private static final Pattern INSTRUMENTATION = Pattern.compile(
            "arthas|SpyAPI|taobao|jacoco", Pattern.CASE_INSENSITIVE);

    /**
     * Frames du JDK et de la machine virtuelle. On les replie aussi : personne n'ouvre
     * {@code java.util.ArrayList.iterator} pour comprendre son application. Le temps
     * qu'elles représentent n'est pas perdu — il est attribué à la méthode applicative qui
     * les a appelées, ce qui est justement l'information utile.
     */
    private static final Pattern PLATFORM = Pattern.compile(
            "^(java|javax|jdk|sun|com/sun|kotlin|scala)/"        // bibliothèque standard
            + "|^[A-Za-z_][A-Za-z0-9_]*::"                       // frames natives de la VM
            + "|^(stub:|itable|vtable|call_stub)"                // trampolines
            + "|^(C1|C2|Interpreter|Compile|CompileBroker)"
            // Une méthode Java s'écrit toujours « paquet/Classe.methode » ou au minimum
            // « Classe.methode » : elle contient donc un '/' ou un '.'. Tout ce qui n'en a
            // aucun est un symbole natif de la machine virtuelle — Java_java_lang_…,
            // MHN_resolve_Mem, eventHandlerClassFileLoadHook, [unknown_Java] — c'est-à-dire
            // du code qu'aucun lecteur ne peut ouvrir ni corriger.
            + "|^[^/.]*$");
    /**
     * Piles que le profileur n'a pas su remonter.
     *
     * <p>Quand l'échantillon tombe dans un fragment de code où la remontée de pile échoue —
     * un trampoline du compilateur, une transition natale — async-profiler écrit un marqueur
     * entre crochets en guise de racine : {@code [unknown_Java];lab/sample/Speeds.forMode}.
     * Le marqueur est du code qu'on ne peut pas ouvrir, il est donc replié comme le reste de
     * la machine virtuelle — mais alors la frame suivante remonte à la racine et s'affiche à
     * côté de {@code main}, comme si le programme avait deux points d'entrée. Ce n'en est pas
     * un : c'est une pile tronquée. On la compte, on la signale, et on ne la greffe pas.
     *
     * <p>Le motif ne vise que les marqueurs d'échec. Une racine entre crochets peut aussi
     * être un nom de fil ({@code [main tid=123]}) : celle-là est une vraie racine.
     */
    private static final Pattern BROKEN_ROOT = Pattern.compile(
            "^\\[(unknown|not_walkable|broken|deopt|failed)", Pattern.CASE_INSENSITIVE);
    private static final int MAX_DEPTH = 40;

    public Map<String, Object> root;
    public String note;
    /** Réserve sur les piles tronquées, ou {@code null} s'il n'y en a pas eu. */
    public String stacksNote;

    public static CallTree parse(Path collapsed) throws IOException {
        return parse(collapsed, PackageFilter.NONE);
    }

    /**
     * @param hidden paquets à replier au même titre que le JDK — voir {@link PackageFilter}
     */
    public static CallTree parse(Path collapsed, PackageFilter hidden) throws IOException {
        CallTree t = new CallTree();
        Node root = new Node("tout");
        long folded = 0;
        long brokenStacks = 0;

        if (Files.isRegularFile(collapsed)) {
            for (String line : Files.readAllLines(collapsed, StandardCharsets.UTF_8)) {
                int space = line.lastIndexOf(' ');
                if (space < 0) continue;
                long count;
                try {
                    count = Long.parseLong(line.substring(space + 1).trim());
                } catch (NumberFormatException e) {
                    continue;
                }
                String[] frames = line.substring(0, space).split(";");
                if (frames.length > 0 && BROKEN_ROOT.matcher(frames[0]).find()) {
                    // Comptée dans le total — le temps a bien été passé — mais pas greffée.
                    root.total += count;
                    brokenStacks += count;
                    continue;
                }
                List<String> kept = new ArrayList<>(frames.length);
                boolean foldedInstrumentation = false;
                for (String f : frames) {
                    if (INSTRUMENTATION.matcher(f).find()) {
                        foldedInstrumentation = true;
                    } else if (!PLATFORM.matcher(f).find() && !hidden.hidden(f)) {
                        kept.add(f);
                    }
                }
                if (foldedInstrumentation) folded += count;
                root.total += count;
                Node node = root;
                for (int i = 0; i < kept.size() && i < MAX_DEPTH; i++) {
                    node = node.child(kept.get(i));
                    node.total += count;
                }
            }
        }

        t.root = root.toMap();
        if (folded > 0 && root.total > 0) {
            long share = Math.round(100.0 * folded / root.total);
            t.note = "Les relevés de temps ont été pris pendant que l'inspection des valeurs "
                    + "était active : " + share + " % d'entre eux tombaient dans son "
                    + "instrumentation. Ils ont été rattachés à la méthode applicative qui les "
                    + "a déclenchés, ce qui restitue la forme de l'arbre. Réserve : le coût "
                    + "affiché de la méthode observée reste surestimé.";
        }
        if (brokenStacks > 0 && root.total > 0) {
            double share = 100.0 * brokenStacks / root.total;
            t.stacksNote = brokenStacks + " relevé" + (brokenStacks > 1 ? "s" : "")
                    + " sur " + root.total + " (" + format(share) + " %) ont une pile que le "
                    + "profileur n'a pas pu remonter jusqu'à son point d'entrée. Ils sont "
                    + "comptés dans le total, mais ne figurent pas dans l'arbre : les y "
                    + "rattacher aurait fait apparaître un second point d'entrée qui n'existe "
                    + "pas.";
        }
        return t;
    }

    /** Deux décimales au plus, et pas de « 0,00 % » pour un relevé qui existe. */
    private static String format(double share) {
        String s = String.format(java.util.Locale.FRANCE, "%.2f", share);
        return s.equals("0,00") ? "< 0,01" : s;
    }

    private static final class Node {
        final String name;
        long total;
        final Map<String, Node> children = new LinkedHashMap<>();

        Node(String name) { this.name = name; }

        Node child(String n) { return children.computeIfAbsent(n, Node::new); }

        Map<String, Object> toMap() {
            List<Object> kids = new ArrayList<>(children.values()).stream()
                    .sorted(Comparator.comparingLong((Node n) -> n.total).reversed())
                    .map(Node::toMap)
                    .map(Object.class::cast)
                    .toList();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", name);
            m.put("total", total);
            m.put("children", kids);
            return m;
        }
    }
}
