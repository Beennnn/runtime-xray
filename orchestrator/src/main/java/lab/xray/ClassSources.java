package lab.xray;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Where the bytecode to analyse is, when nobody said so.
 *
 * <p>Asking for that path is the only setting whose answer already exists elsewhere: the
 * JVM that has just run knows perfectly well where it loaded its classes from. Having it
 * typed in a second time, under another name, is a form whose answer we already have — and
 * an opportunity to get it wrong, since the mistake only shows in the report.
 *
 * <p>Three sources are consulted in order, and <b>each one is checked before being
 * kept</b>: a guessed but wrong path would be worse than no guess at all.
 *
 * <ol>
 *   <li>the <b>real arguments of the observed JVM</b>, read from the system. That is the
 *       authoritative source, and it holds even when the command given was a script or a
 *       launcher: we read the JVM, not what we thought we were launching;</li>
 *   <li>failing that, the <b>configured command</b>, if it carries a {@code -jar} or a
 *       {@code -cp};</li>
 *   <li>failing that, the <b>project convention</b>: {@code target/classes} for Maven,
 *       {@code build/classes/java/main} for Gradle, and their equivalents.</li>
 * </ol>
 *
 * <p><b>Dependency jars are deliberately left out</b> of sources 1 and 2. A real classpath
 * holds dozens of them; analysing them all would give a report where the project's code is
 * one percent of the total, and where the question "what ran?" no longer has a readable
 * answer. The code you own is a directory of classes, or the jar you launched yourself. To
 * analyse one more, it is up to {@code --classes} to say so — and that is the only case
 * where it is still useful.
 */
public final class ClassSources {

    /** The layouts produced by the common build tools. */
    private static final List<String> CONVENTIONS = List.of(
            "target/classes",              // Maven
            "build/classes/java/main",     // Gradle
            "out/production/classes",      // IntelliJ
            "bin",                         // Eclipse
            "classes");

    private ClassSources() {}

    /**
     * @param jvmArguments the arguments read off the observed JVM, possibly empty
     * @param javaCommand  the command as configured
     * @param workingDir   the directory the analysis is launched from
     * @return the paths kept, in order, or an empty list when nothing is certain
     */
    public static List<Path> discover(List<String> jvmArguments, String javaCommand,
                                      Path workingDir) {
        List<Path> found = fromArguments(jvmArguments);
        if (!found.isEmpty()) return found;

        found = fromArguments(CommandLine.toProcessArgs(javaCommand));
        if (!found.isEmpty()) return found;

        for (String convention : CONVENTIONS) {
            Path candidate = workingDir.resolve(convention);
            if (Files.isDirectory(candidate)) {
                return List.of(candidate);
            }
        }
        return List.of();
    }

    /**
     * The application bytecode named by a Java command line.
     *
     * <p>{@code -jar} wins: when the JVM is launched on an archive, that archive holds the
     * code, and any classpath only serves the dependencies.
     */
    private static List<Path> fromArguments(List<String> args) {
        if (args == null) return List.of();
        for (int i = 0; i < args.size() - 1; i++) {
            if (args.get(i).equals("-jar")) {
                Path jar = Path.of(strip(args.get(i + 1)));
                return Files.isRegularFile(jar) ? List.of(jar) : List.of();
            }
        }
        for (int i = 0; i < args.size() - 1; i++) {
            String a = args.get(i);
            if (a.equals("-cp") || a.equals("-classpath") || a.equals("--class-path")) {
                return directoriesOf(strip(args.get(i + 1)));
            }
        }
        return List.of();
    }

    /**
     * The directories of a classpath — and them alone.
     *
     * <p>A directory of classes is code compiled on the spot, so it is the project's code.
     * A jar on a classpath is nearly always a fetched dependency, whose analysis would
     * drown the subject. The rule is coarse but it is right in almost every case, and a
     * {@code --classes} corrects it.
     */
    private static List<Path> directoriesOf(String classpath) {
        Set<Path> out = new LinkedHashSet<>();
        for (String entry : classpath.split(File.pathSeparator)) {
            if (entry.isBlank() || entry.endsWith("*")) continue;
            Path p = Path.of(strip(entry));
            if (Files.isDirectory(p)) out.add(p);
        }
        return new ArrayList<>(out);
    }

    private static String strip(String s) {
        String t = s.trim();
        if (t.length() > 1 && (t.startsWith("\"") && t.endsWith("\"")
                || t.startsWith("'") && t.endsWith("'"))) {
            return t.substring(1, t.length() - 1);
        }
        return t;
    }
}
