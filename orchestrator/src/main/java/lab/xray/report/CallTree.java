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
    private static final int MAX_DEPTH = 40;

    public Map<String, Object> root;
    public String note;

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
        return t;
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
