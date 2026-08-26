package lab.xray.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L'index des sources décide de tout ce qui s'affiche à côté de la couverture. Sa clé doit
 * être celle de JaCoCo — {@code paquet/Fichier.java} — et elle doit l'être <b>quel que soit
 * le répertoire qu'on a désigné</b>. C'est la décision que ces tests gardent : le 26 août
 * 2026, une racine d'un cran trop haute suffisait à faire afficher « Source indisponible »
 * sur les 447 classes d'une analyse, sans que rien ne dise pourquoi.
 */
class SourcesTest {

    private static Path source(Path dir, String chemin, String paquet) throws IOException {
        Path f = dir.resolve(chemin);
        Files.createDirectories(f.getParent());
        String nom = f.getFileName().toString().replace(".java", "");
        Files.writeString(f, (paquet == null ? "" : "package " + paquet + ";\n\n")
                + "class " + nom + " {\n    int x() { return 1; }\n}\n", StandardCharsets.UTF_8);
        return f;
    }

    @Test
    @DisplayName("La clé vient du paquet déclaré, pas du niveau de la racine passée")
    void keysFollowTheDeclaredPackage(@TempDir Path dir) throws Exception {
        source(dir, "projet/src/main/java/org/exemple/module/Application.java", "org.exemple.module");

        // Trois racines, du bon niveau au plus improbable : la clé ne bouge pas.
        for (String racine : List.of("projet/src/main/java", "projet/src", "projet")) {
            Sources.Index index = Sources.load(List.of(dir.resolve(racine)));
            assertTrue(index.parCle().containsKey("org/exemple/module/Application.java"),
                    "racine « " + racine + " » : la clé doit rester celle de JaCoCo, "
                    + "or on a " + index.parCle().keySet());
        }
    }

    @Test
    @DisplayName("Une racine SOUS le paquet retombe quand même sur la bonne clé")
    void keysSurviveARootBelowThePackage(@TempDir Path dir) throws Exception {
        source(dir, "src/org/exemple/module/Application.java", "org.exemple.module");

        Sources.Index index = Sources.load(List.of(dir.resolve("src/org/exemple")));
        assertTrue(index.parCle().containsKey("org/exemple/module/Application.java"),
                "le chemin relatif aurait donné « mod/Application.java », qui ne correspond à rien");
    }

    @Test
    @DisplayName("Un fichier sans paquet garde son chemin relatif pour clé")
    void keepsTheRelativePathWhenNoPackageIsDeclared(@TempDir Path dir) throws Exception {
        source(dir, "src/Ancien.java", null);

        Sources.Index index = Sources.load(List.of(dir.resolve("src")));
        assertTrue(index.parCle().containsKey("Ancien.java"),
                "sans déclaration, il n'y a rien de mieux que le chemin : " + index.parCle().keySet());
    }

    @Test
    @DisplayName("Un « package » écrit après le début de la classe n'est pas une déclaration")
    void ignoresAPackageWordThatComesTooLate() {
        assertEquals("app.vrai", Sources.paquetDeclare(List.of(
                "package app.vrai;", "class A {", "  String s = \"package autre.chose;\";", "}")));
        assertNull(Sources.paquetDeclare(List.of(
                "class A {", "  String s = \"package autre.chose;\";", "}")));
    }

    private static void assertNull(Object o) {
        org.junit.jupiter.api.Assertions.assertNull(o);
    }

    @Test
    @DisplayName("Une racine inexistante est retenue dans le diagnostic, pas ignorée en silence")
    void reportsARootThatDoesNotExist(@TempDir Path dir) {
        Sources.Index index = Sources.load(List.of(dir.resolve("nulle-part")));

        assertEquals(0, index.fichiers(), "rien à lire dans un répertoire absent");
        assertEquals(1, index.racines().size(), "la racine demandée doit rester visible");
        @SuppressWarnings("unchecked")
        Map<String, Object> racine = (Map<String, Object>) index.racines().get(0);
        assertEquals(Boolean.FALSE, racine.get("existe"));
        assertNotNull(racine.get("absolue"), "le chemin absolu est ce qui permet de comprendre");
        assertNotNull(racine.get("motif"), "et la raison est ce qui permet de corriger");
    }

    @Test
    @DisplayName("Le diagnostic dit où chaque nom de fichier a été trouvé")
    void tellsWhereEachFileNameWasFound(@TempDir Path dir) throws Exception {
        source(dir, "src/autre/paquet/Application.java", "autre.paquet");

        Sources.Index index = Sources.load(List.of(dir.resolve("src")));

        // Le cas réel : la couverture cherche org/exemple/module/Application.java, l'index n'a
        // qu'un homonyme. C'est cette liste qui permet à la page de le dire.
        @SuppressWarnings("unchecked")
        List<Object> endroits = (List<Object>) index.parNom().get("Application.java");
        assertNotNull(endroits, "un fichier trouvé doit être retrouvable par son nom");
        @SuppressWarnings("unchecked")
        Map<String, Object> ou = (Map<String, Object>) endroits.get(0);
        assertEquals("autre.paquet", ou.get("paquet"));
        assertEquals("autre/paquet/Application.java", ou.get("cle"));
        assertTrue(String.valueOf(ou.get("chemin")).endsWith("Application.java"),
                "le chemin absolu est ce qu'on recopie pour corriger la configuration");
    }

    @Test
    @DisplayName("Une source qui n'est pas en UTF-8 est lue quand même")
    void readsSourcesThatAreNotUtf8(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("src/app/Accent.java");
        Files.createDirectories(f.getParent());
        Files.write(f, ("package app;\n// données déjà mesurées\n"
                + "class Accent {}\n").getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));

        Sources.Index index = Sources.load(List.of(dir.resolve("src")));

        assertTrue(index.parCle().containsKey("app/Accent.java"),
                "un accent dans un commentaire ne doit pas coûter l'affichage du fichier");
        assertFalse(((List<?>) index.parCle().get("app/Accent.java")).isEmpty());
    }
}
