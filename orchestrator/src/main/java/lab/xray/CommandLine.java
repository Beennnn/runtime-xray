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

    private static final String SHELL_CHARS = "|&;<>$`(){}*?";

    /**
     * Le tilde ne compte qu'en <b>tête de mot</b>, là où un interpréteur l'étendrait.
     *
     * <p>Ailleurs, il est littéral — et il est très courant sous Windows, où les noms courts
     * en 8.3 en portent un : {@code C:\Users\RUNNER~1\AppData}, {@code C:\PROGRA~1}. Le
     * compter partout envoyait ces commandes-là à {@code cmd /c}, ce qui les exécute très
     * bien mais rend la ligne illisible pour l'outil : {@link ClassSources} n'y trouve plus
     * le {@code -jar}, donc plus le bytecode, donc un rapport vide sans que rien ne
     * l'explique. Constaté le 26 août 2026, au premier passage de la CI sous Windows.
     */
    private static final char TILDE = '~';

    private CommandLine() {}

    public static List<String> toProcessArgs(String command) {
        if (needsShell(command)) {
            boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
            return windows ? List.of("cmd", "/c", command) : List.of("/bin/sh", "-c", command);
        }
        return tokenize(command);
    }

    public static boolean needsShell(String command) {
        boolean inSingle = false, inDouble = false, wordStart = true;
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (c == '\'' && !inDouble) { inSingle = !inSingle; wordStart = false; }
            else if (c == '"' && !inSingle) { inDouble = !inDouble; wordStart = false; }
            else if (inSingle || inDouble) wordStart = false;
            else if (Character.isWhitespace(c)) wordStart = true;
            else {
                if (c == TILDE ? wordStart : SHELL_CHARS.indexOf(c) >= 0) return true;
                wordStart = false;
            }
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
