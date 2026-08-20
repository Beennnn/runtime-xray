package lab.xray;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Où se trouve le bytecode à analyser, quand on ne l'a pas dit.
 *
 * <p>Demander ce chemin est le seul réglage dont la réponse existe déjà ailleurs : la JVM
 * qui vient de tourner sait parfaitement d'où elle a chargé ses classes. La faire saisir une
 * seconde fois, sous un autre nom, c'est un formulaire dont on connaît la réponse — et une
 * occasion de se tromper, puisque l'erreur ne se voit qu'au rapport.
 *
 * <p>Trois sources sont consultées dans l'ordre, et <b>chacune est vérifiée avant d'être
 * retenue</b> : un chemin deviné mais faux serait pire que pas de devinette du tout.
 *
 * <ol>
 *   <li>les <b>arguments réels de la JVM observée</b>, lus sur le système. C'est la source
 *       qui fait autorité, et elle vaut même quand la commande donnée était un script ou un
 *       lanceur : on lit la JVM, pas ce qu'on croyait lancer ;</li>
 *   <li>à défaut, la <b>commande configurée</b>, si elle porte un {@code -jar} ou un
 *       {@code -cp} ;</li>
 *   <li>à défaut, la <b>convention du projet</b> : {@code target/classes} pour Maven,
 *       {@code build/classes/java/main} pour Gradle, et leurs équivalents.</li>
 * </ol>
 *
 * <p><b>Les jar de dépendances sont volontairement écartés</b> des sources 1 et 2. Un
 * classpath réel en compte des dizaines ; les analyser tous ferait un rapport où le code du
 * projet représente un pour cent du total, et où la question « qu'est-ce qui a tourné ? »
 * n'a plus de réponse lisible. Le code que l'on possède est un répertoire de classes, ou le
 * jar que l'on a soi-même lancé. Pour en analyser un de plus, c'est à {@code --classes}
 * de le dire — et c'est le seul cas où il reste utile.
 */
public final class ClassSources {

    /** Les dispositions produites par les outils de construction courants. */
    private static final List<String> CONVENTIONS = List.of(
            "target/classes",              // Maven
            "build/classes/java/main",     // Gradle
            "out/production/classes",      // IntelliJ
            "bin",                         // Eclipse
            "classes");

    private ClassSources() {}

    /**
     * @param jvmArguments les arguments relevés sur la JVM observée, éventuellement vides
     * @param javaCommand  la commande telle que configurée
     * @param workingDir   le répertoire depuis lequel l'analyse est lancée
     * @return les chemins retenus, dans l'ordre, ou une liste vide si rien n'est sûr
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
     * Le bytecode applicatif désigné par une ligne de commande Java.
     *
     * <p>{@code -jar} l'emporte : quand la JVM est lancée sur une archive, c'est elle qui
     * contient le code, et le classpath éventuel ne sert qu'aux dépendances.
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
     * Les répertoires d'un classpath — et eux seuls.
     *
     * <p>Un répertoire de classes est du code compilé sur place, donc du code du projet.
     * Un jar dans un classpath est presque toujours une dépendance récupérée, dont
     * l'analyse noierait le sujet. La règle est grossière mais elle est juste dans la
     * quasi-totalité des cas, et elle se corrige d'un {@code --classes}.
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
