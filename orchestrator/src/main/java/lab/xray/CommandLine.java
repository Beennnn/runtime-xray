package lab.xray;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns the command supplied by the user into something executable.
 *
 * <p>Two cases, and deliberately so:
 * <ul>
 *   <li>a plain command — {@code java -jar app.jar --profile acceptance} — is split right
 *       here and launched directly. No interpreter is needed, which makes the tool usable
 *       on Windows as well as anywhere else;</li>
 *   <li>a command that uses pipes, chaining or variables is handed to the system's
 *       interpreter. We do not reimplement a shell.</li>
 * </ul>
 */
public final class CommandLine {

    private static final String SHELL_CHARS = "|&;<>$`(){}*?";

    /**
     * The tilde only counts at the <b>start of a word</b>, where an interpreter would
     * expand it.
     *
     * <p>Anywhere else it is literal — and it is very common on Windows, where the 8.3
     * short names carry one: {@code C:\Users\RUNNER~1\AppData}, {@code C:\PROGRA~1}.
     * Counting it everywhere sent those commands to {@code cmd /c}, which runs them
     * perfectly well but makes the line unreadable to the tool: {@link ClassSources} no
     * longer finds the {@code -jar} in it, so no bytecode, so an empty report with nothing
     * to explain it. Seen on 26 August 2026, on the first Windows CI run.
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

    /** Splitting on spaces, honouring single and double quotes. */
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
