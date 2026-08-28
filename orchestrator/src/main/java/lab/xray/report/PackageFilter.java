package lab.xray.report;

import java.util.ArrayList;
import java.util.List;

/**
 * The packages the analysis must keep quiet about.
 *
 * <p>Folding the JDK frames is settled once and for all: nobody opens
 * {@code java.util.ArrayList.iterator} to understand their application. But the same holds
 * for libraries that are <b>not</b> in the JDK and that one nonetheless treats as
 * infrastructure — a logger, an HTTP client, an injection framework. Their presence in the
 * tree teaches nothing and drowns the business code.
 *
 * <p>Where to put the boundary is not a technical question: it depends on what the team
 * owns and what it merely puts up with. It therefore cannot be hard-coded, hence this
 * list, held in the configuration file and carried along with the run.
 *
 * <p>The time of the hidden frames <b>is not lost</b>: it is attributed to the application
 * method that called them, exactly as for the JDK. Hiding moves the information, it does
 * not remove it.
 */
public final class PackageFilter {

    /** Nothing is hidden — the behaviour when the configuration says nothing. */
    public static final PackageFilter NONE = new PackageFilter("", List.of());

    private final String spec;
    private final List<String> prefixes;

    private PackageFilter(String spec, List<String> prefixes) {
        this.spec = spec;
        this.prefixes = prefixes;
    }

    /**
     * Reads a list separated by commas, semicolons or spaces.
     *
     * <p>Both spellings of a package are accepted, {@code org.slf4j} as well as
     * {@code org/slf4j}, with or without a trailing {@code *}: these are the forms one has
     * in front of one's eyes depending on whether one is reading code or a report, and
     * demanding the right one would be one more quibble in a file filled in by hand.
     */
    public static PackageFilter of(String raw) {
        if (raw == null || raw.isBlank()) return NONE;
        List<String> prefixes = new ArrayList<>();
        for (String piece : raw.split("[,;\\s]+")) {
            String p = piece.trim().replace('.', '/');
            while (p.endsWith("*") || p.endsWith("/")) {
                p = p.substring(0, p.length() - 1);
            }
            if (!p.isEmpty()) prefixes.add(p);
        }
        return prefixes.isEmpty() ? NONE : new PackageFilter(raw.trim(), List.copyOf(prefixes));
    }

    /**
     * {@code true} when this name belongs to a hidden package.
     *
     * @param name a name in {@code package/Class.method}, {@code package/Class} or
     *             {@code package.Class} — all three forms circulate in the reports.
     */
    public boolean hidden(String name) {
        if (prefixes.isEmpty() || name == null) return false;
        String n = name.replace('.', '/');
        for (String p : prefixes) {
            // The '/' required after the prefix stops a package "org/slf4jx" being caught
            // by a rule "org/slf4j": these are two different libraries.
            if (n.startsWith(p + "/")) return true;
        }
        return false;
    }

    public boolean isEmpty() {
        return prefixes.isEmpty();
    }

    /** The list as written in the configuration, to show it to the reader. */
    public String spec() {
        return spec;
    }
}
