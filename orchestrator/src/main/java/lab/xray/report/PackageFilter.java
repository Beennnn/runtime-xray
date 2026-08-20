package lab.xray.report;

import java.util.ArrayList;
import java.util.List;

/**
 * Les paquets que l'analyse doit taire.
 *
 * <p>Le repli des frames du JDK est décidé une fois pour toutes : personne n'ouvre
 * {@code java.util.ArrayList.iterator} pour comprendre son application. Mais la même chose
 * vaut pour des bibliothèques qui ne sont <b>pas</b> dans le JDK et que l'on considère
 * néanmoins comme de l'infrastructure — un journal, un client HTTP, un cadriciel
 * d'injection. Leur présence dans l'arbre n'apprend rien et noie le code métier.
 *
 * <p>Où placer la frontière n'est pas une question technique : elle dépend de ce que
 * l'équipe possède et de ce qu'elle subit. Elle ne peut donc pas être codée en dur, d'où
 * cette liste, tenue dans le fichier de configuration et transportée avec l'exécution.
 *
 * <p>Le temps des frames masquées <b>n'est pas perdu</b> : il est attribué à la méthode
 * applicative qui les a appelées, exactement comme pour le JDK. Masquer déplace
 * l'information, ça ne la supprime pas.
 */
public final class PackageFilter {

    /** Rien n'est masqué — le comportement quand la configuration ne dit rien. */
    public static final PackageFilter NONE = new PackageFilter("", List.of());

    private final String spec;
    private final List<String> prefixes;

    private PackageFilter(String spec, List<String> prefixes) {
        this.spec = spec;
        this.prefixes = prefixes;
    }

    /**
     * Lit une liste séparée par des virgules, des points-virgules ou des espaces.
     *
     * <p>Les deux écritures d'un paquet sont acceptées, {@code org.slf4j} comme
     * {@code org/slf4j}, avec ou sans {@code *} final : ce sont les formes que l'on a sous
     * les yeux selon qu'on lit du code ou un rapport, et exiger la bonne serait une
     * chicane de plus dans un fichier qui se remplit à la main.
     */
    public static PackageFilter of(String raw) {
        if (raw == null || raw.isBlank()) return NONE;
        List<String> prefixes = new ArrayList<>();
        for (String part : raw.split("[,;\\s]+")) {
            String p = part.trim().replace('.', '/');
            while (p.endsWith("*") || p.endsWith("/")) {
                p = p.substring(0, p.length() - 1);
            }
            if (!p.isEmpty()) prefixes.add(p);
        }
        return prefixes.isEmpty() ? NONE : new PackageFilter(raw.trim(), List.copyOf(prefixes));
    }

    /**
     * {@code true} si ce nom relève d'un paquet masqué.
     *
     * @param name un nom en {@code paquet/Classe.methode}, {@code paquet/Classe} ou
     *             {@code paquet.Classe} — les trois formes circulent dans les rapports.
     */
    public boolean hidden(String name) {
        if (prefixes.isEmpty() || name == null) return false;
        String n = name.replace('.', '/');
        for (String p : prefixes) {
            // Le '/' exigé après le préfixe évite qu'un paquet « org/slf4jx » soit emporté
            // par une règle « org/slf4j » : ce sont deux bibliothèques différentes.
            if (n.startsWith(p + "/")) return true;
        }
        return false;
    }

    public boolean isEmpty() {
        return prefixes.isEmpty();
    }

    /** La liste telle qu'écrite dans la configuration, pour l'afficher au lecteur. */
    public String spec() {
        return spec;
    }
}
