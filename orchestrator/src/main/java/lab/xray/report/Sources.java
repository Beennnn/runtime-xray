package lab.xray.report;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Le code source, indexé comme le rapport de couverture l'indexe : {@code paquet/Fichier.java}.
 * C'est cette convention commune qui permet de rapprocher une ligne de code et sa couverture.
 */
public final class Sources {

    private Sources() {}

    public static Map<String, Object> load(List<Path> roots) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                    try {
                        String key = root.relativize(p).toString().replace('\\', '/');
                        out.put(key, Files.readAllLines(p, StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        // Un fichier illisible ne doit pas faire échouer tout le rapport :
                        // on le signale et on continue.
                        System.err.println("   source ignorée : " + p + " (" + e.getMessage() + ")");
                    }
                });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return out;
    }
}
