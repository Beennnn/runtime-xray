package lab.xray.report;

import lab.xray.json.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ce qu'on ajoute à une exécution après l'avoir mesurée : un nom, une description, des
 * étiquettes, un élagage de son arbre.
 *
 * <p>Ces annotations peuvent vivre à trois endroits, et le choix n'est pas cosmétique — il
 * décide de <b>ce qui voyage avec quoi</b> :
 *
 * <table>
 *   <caption>Où une annotation peut vivre</caption>
 *   <tr><th>Emplacement</th><th>Ce que ça implique</th></tr>
 *   <tr><td>{@code runs/<exécution>/config.json}</td>
 *       <td><b>Prioritaire.</b> L'annotation est dans le répertoire de l'exécution : elle
 *       la suit partout — copie, archive, envoi à un collègue</td></tr>
 *   <tr><td>{@code runs/<exécution>-config.json}</td>
 *       <td>À côté du répertoire, même nom suffixé. L'exécution reste intacte, ce qui est
 *       utile quand elle est en lecture seule ou signée</td></tr>
 *   <tr><td>{@code noms.json}</td>
 *       <td>Un seul fichier pour tout le rapport, indexé par identifiant. C'est le format
 *       d'échange : celui qu'on exporte de la page et qu'on repasse à quelqu'un</td></tr>
 * </table>
 *
 * <p>L'ordre est celui du tableau : <b>le plus proche de l'exécution l'emporte</b>. Une
 * annotation posée dans le répertoire gagne sur celle d'à côté, qui gagne sur le fichier
 * central — parce que celui qui a pris la peine de la ranger avec la mesure a exprimé une
 * intention plus précise que celui qui a rempli le fichier commun.
 *
 * <p>Aucune fusion champ à champ entre les trois : la source la plus proche est prise
 * <b>entière</b>. Mélanger un nom d'un fichier et une description d'un autre donnerait une
 * annotation que personne n'a écrite, et qu'on ne saurait pas corriger.
 */
public final class Annotations {

    /** Le fichier commun, indexé par identifiant d'exécution. */
    public static final String CENTRAL = "noms.json";
    /** Le fichier posé DANS le répertoire de l'exécution. */
    public static final String DANS_LE_RUN = "config.json";
    /** Le suffixe du fichier posé À CÔTÉ du répertoire. */
    public static final String SUFFIXE = "-config.json";

    private Annotations() {}

    /**
     * L'annotation retenue pour une exécution, source la plus proche d'abord.
     *
     * @param central contenu de {@code noms.json}, déjà lu — il sert à toutes les exécutions
     * @return la valeur telle qu'elle a été écrite (chaîne ou objet), ou {@code null}
     */
    public static Object forRun(Path runDir, String uuid, Map<String, Object> central) {
        Object dedans = readFile(runDir.resolve(DANS_LE_RUN));
        if (dedans != null) return dedans;
        Object aCote = readFile(runDir.resolveSibling(runDir.getFileName() + SUFFIXE));
        if (aCote != null) return aCote;
        return uuid == null || uuid.isBlank() ? null : central.get(uuid);
    }

    /**
     * Où écrire l'annotation d'une exécution.
     *
     * <p>Là où elle vit déjà, si elle vit quelque part : réécrire ailleurs laisserait deux
     * versions dont l'une, prioritaire, ne serait pas celle qu'on vient de saisir. Sinon,
     * dans le répertoire de l'exécution — c'est l'emplacement qui la fait voyager avec la
     * mesure, et celui qu'on veut par défaut.
     */
    public static Path fileFor(Path runDir) {
        Path dedans = runDir.resolve(DANS_LE_RUN);
        if (Files.isRegularFile(dedans)) return dedans;
        Path aCote = runDir.resolveSibling(runDir.getFileName() + SUFFIXE);
        if (Files.isRegularFile(aCote)) return aCote;
        return dedans;
    }

    /** Écrit l'annotation d'une exécution, ou retire le fichier si elle est vide. */
    public static Path write(Path runDir, Map<String, Object> annotation) throws IOException {
        Path file = fileFor(runDir);
        if (annotation == null || annotation.isEmpty()) {
            Files.deleteIfExists(file);
            return file;
        }
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, Json.write(annotation), StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        return file;
    }

    /** Les exécutions présentes sous {@code commonDir}, par identifiant. */
    public static Map<String, Path> runsByUuid(Path commonDir) {
        Map<String, Path> out = new LinkedHashMap<>();
        for (Path run : runDirs(commonDir)) {
            String uuid = uuidOf(run);
            if (uuid != null) out.put(uuid, run);
        }
        return out;
    }

    /** Les répertoires d'exécution : ceux qui portent un {@code run-context.json}. */
    public static List<Path> runDirs(Path commonDir) {
        List<Path> out = new ArrayList<>();
        Path runs = commonDir.resolve("runs");
        if (!Files.isDirectory(runs)) return out;
        try (var listing = Files.list(runs)) {
            listing.filter(Files::isDirectory)
                   .filter(d -> Files.isRegularFile(d.resolve("run-context.json")))
                   .sorted()
                   .forEach(out::add);
        } catch (IOException e) {
            System.err.println("   exécutions illisibles sous " + runs + " : " + e.getMessage());
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static String uuidOf(Path runDir) {
        Object lu = readFile(runDir.resolve("run-context.json"));
        if (lu instanceof Map<?, ?> m) {
            Object uuid = ((Map<String, Object>) m).get("uuid");
            return uuid == null || String.valueOf(uuid).isBlank() ? null : String.valueOf(uuid);
        }
        return null;
    }

    /** Lit un fichier JSON, ou {@code null} s'il est absent — ou illisible, et on le dit. */
    public static Object readFile(Path file) {
        if (!Files.isRegularFile(file)) return null;
        try {
            return Json.read(Files.readString(file, StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println("   " + file + " illisible — ignoré (" + e.getMessage() + ")");
            return null;
        }
    }

    /** Le fichier commun, ou une carte vide s'il n'existe pas. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> readCentral(Path commonDir) {
        Object lu = readFile(commonDir.resolve(CENTRAL));
        return lu instanceof Map<?, ?> m
                ? new LinkedHashMap<>((Map<String, Object>) m)
                : new LinkedHashMap<>();
    }
}
