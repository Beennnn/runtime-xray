package lab.xray;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Transforme la commande fournie par l'utilisateur en quelque chose d'exécutable.
 *
 * <p>Deux cas, et c'est volontaire :
 * <ul>
 *   <li>une commande simple — {@code java -jar app.jar --profil recette} — est découpée
 *       ici même et lancée directement. Aucun interpréteur n'est nécessaire, ce qui rend
 *       l'outil utilisable sur Windows comme ailleurs ;</li>
 *   <li>une commande qui utilise des tubes, des enchaînements ou des variables est confiée
 *       à l'interpréteur du système. On ne réimplémente pas un shell.</li>
 * </ul>
 */
public final class CommandLine {

    private static final String SHELL_CHARS = "|&;<>$`(){}*?~";

    private CommandLine() {}

    public static List<String> toProcessArgs(String command) {
        if (needsShell(command)) {
            boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
            return windows ? List.of("cmd", "/c", command) : List.of("/bin/sh", "-c", command);
        }
        return tokenize(command);
    }

    public static boolean needsShell(String command) {
        boolean inSingle = false, inDouble = false;
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (c == '\'' && !inDouble) inSingle = !inSingle;
            else if (c == '"' && !inSingle) inDouble = !inDouble;
            else if (!inSingle && !inDouble && SHELL_CHARS.indexOf(c) >= 0) return true;
        }
        return false;
    }

    /** Découpage sur les espaces, en respectant les guillemets simples et doubles. */
    static List<String> tokenize(String command) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingle = false, inDouble = false, started = false;
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (c == '\'' && !inDouble) { inSingle = !inSingle; started = true; }
            else if (c == '"' && !inSingle) { inDouble = !inDouble; started = true; }
            else if (Character.isWhitespace(c) && !inSingle && !inDouble) {
                if (started) { out.add(current.toString()); current.setLength(0); started = false; }
            } else { current.append(c); started = true; }
        }
        if (started) out.add(current.toString());
        return out;
    }
}
