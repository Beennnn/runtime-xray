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

    private static Path source(Path dir, String path, String pkg) throws IOException {
        Path f = dir.resolve(path);
        Files.createDirectories(f.getParent());
        String name = f.getFileName().toString().replace(".java", "");
        Files.writeString(f, (pkg == null ? "" : "package " + pkg + ";\n\n")
                + "class " + name + " {\n    int x() { return 1; }\n}\n", StandardCharsets.UTF_8);
        return f;
    }

    @Test
    @DisplayName("La clé vient du paquet déclaré, pas du niveau de la racine passée")
    void keysFollowTheDeclaredPackage(@TempDir Path dir) throws Exception {
        source(dir, "projet/src/main/java/com/example/app/Application.java", "com.example.app");

        // Trois racines, du bon niveau au plus improbable : la clé ne bouge pas.
        for (String root : List.of("projet/src/main/java", "projet/src", "projet")) {
            Sources.Index index = Sources.load(List.of(dir.resolve(root)));
            assertTrue(index.byKey().containsKey("com/example/app/Application.java"),
                    "racine « " + root + " » : la clé doit rester celle de JaCoCo, "
                    + "or on a " + index.byKey().keySet());
        }
    }

    @Test
    @DisplayName("Une racine SOUS le paquet retombe quand même sur la bonne clé")
    void keysSurviveARootBelowThePackage(@TempDir Path dir) throws Exception {
        source(dir, "src/com/example/app/Application.java", "com.example.app");

        Sources.Index index = Sources.load(List.of(dir.resolve("src/com/example")));
        assertTrue(index.byKey().containsKey("com/example/app/Application.java"),
                "le chemin relatif aurait donné « mod/Application.java », qui ne correspond à rien");
    }

    @Test
    @DisplayName("Un fichier sans paquet garde son chemin relatif pour clé")
    void keepsTheRelativePathWhenNoPackageIsDeclared(@TempDir Path dir) throws Exception {
        source(dir, "src/Ancien.java", null);

        Sources.Index index = Sources.load(List.of(dir.resolve("src")));
        assertTrue(index.byKey().containsKey("Ancien.java"),
                "sans déclaration, il n'y a rien de mieux que le chemin : " + index.byKey().keySet());
    }

    @Test
    @DisplayName("Un « package » écrit après le début de la classe n'est pas une déclaration")
    void ignoresAPackageWordThatComesTooLate() {
        assertEquals("app.vrai", Sources.declaredPackage(List.of(
                "package app.vrai;", "class A {", "  String s = \"package autre.chose;\";", "}")));
        assertNull(Sources.declaredPackage(List.of(
                "class A {", "  String s = \"package autre.chose;\";", "}")));
    }

    private static void assertNull(Object o) {
        org.junit.jupiter.api.Assertions.assertNull(o);
    }

    @Test
    @DisplayName("Une racine inexistante est retenue dans le diagnostic, pas ignorée en silence")
    void reportsARootThatDoesNotExist(@TempDir Path dir) {
        Sources.Index index = Sources.load(List.of(dir.resolve("nulle-part")));

        assertEquals(0, index.files(), "rien à lire dans un répertoire absent");
        assertEquals(1, index.roots().size(), "la racine demandée doit rester visible");
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) index.roots().get(0);
        assertEquals(Boolean.FALSE, root.get("existe"));
        assertNotNull(root.get("absolue"), "le chemin absolu est ce qui permet de comprendre");
        assertNotNull(root.get("motif"), "et la raison est ce qui permet de corriger");
    }

    @Test
    @DisplayName("Le diagnostic dit où chaque nom de fichier a été trouvé")
    void tellsWhereEachFileNameWasFound(@TempDir Path dir) throws Exception {
        source(dir, "src/autre/paquet/Application.java", "autre.paquet");

        Sources.Index index = Sources.load(List.of(dir.resolve("src")));

        // Le cas réel : la couverture cherche com/example/app/Application.java, l'index n'a
        // qu'un homonyme. C'est cette liste qui permet à la page de le dire.
        @SuppressWarnings("unchecked")
        List<Object> places = (List<Object>) index.byName().get("Application.java");
        assertNotNull(places, "un fichier trouvé doit être retrouvable par son nom");
        @SuppressWarnings("unchecked")
        Map<String, Object> where = (Map<String, Object>) places.get(0);
        assertEquals("autre.paquet", where.get("paquet"));
        assertEquals("autre/paquet/Application.java", where.get("cle"));
        assertTrue(String.valueOf(where.get("chemin")).endsWith("Application.java"),
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

        assertTrue(index.byKey().containsKey("app/Accent.java"),
                "un accent dans un commentaire ne doit pas coûter l'affichage du fichier");
        assertFalse(((List<?>) index.byKey().get("app/Accent.java")).isEmpty());
    }

    // ------------------------------------------------- proposer une racine, sans la deviner

    @Test
    @DisplayName("La racine proposée est celle qui explique le plus de classes manquantes")
    @SuppressWarnings("unchecked")
    void proposesTheRootThatExplainsTheMostClasses(@TempDir Path dir) throws Exception {
        source(dir, "projet/src/main/java/com/example/app/Application.java", "com.example.app");
        source(dir, "projet/src/main/java/com/example/data/Repository.java", "com.example.data");
        source(dir, "autre/src/com/example/app/Application.java", "com.example.app");

        List<Object> leads = Sources.searchRoots(
                new java.util.LinkedHashSet<>(List.of("com/example/app/Application.java",
                                                      "com/example/data/Repository.java")),
                List.of(dir));

        assertFalse(leads.isEmpty(), "les deux racines candidates doivent être trouvées");
        Map<String, Object> best = (Map<String, Object>) leads.get(0);
        // Comparer sur un chemin construit, jamais sur un littéral à séparateurs : sous
        // Windows la racine se termine par « projet\src\main\java », et le test échouait
        // alors que la recherche avait trouvé exactement ce qu'il fallait.
        assertEquals(dir.resolve("projet/src/main/java").toAbsolutePath().normalize().toString(),
                best.get("racine"),
                "celle qui résout deux classes passe devant celle qui n'en résout qu'une");
        assertEquals(2, ((Number) best.get("resout")).intValue());
        assertEquals(2, ((Number) best.get("surTotal")).intValue(),
                "le compte doit rester lisible : « 2 sur 2 », pas « 2 »");
    }

    @Test
    @DisplayName("Un fichier au bon nom mais au mauvais paquet ne compte pas")
    void doesNotCreditANamesakeFromAnotherProject(@TempDir Path dir) throws Exception {
        // C'est CE test qui sépare une proposition d'une devinette. Un Application.java d'un
        // autre projet afficherait, en face de la couverture, du code qui n'a jamais tourné —
        // plus coûteux qu'un panneau vide, parce qu'on le croirait.
        source(dir, "un-autre-projet/src/util/Application.java", "un.autre.projet");

        List<Object> leads = Sources.searchRoots(
                new java.util.LinkedHashSet<>(List.of("com/example/app/Application.java")),
                List.of(dir));

        assertTrue(leads.isEmpty(),
                "le nom concorde, le paquet non : rien ne doit être proposé, or " + leads);
    }

    @Test
    @DisplayName("Rien à proposer se dit, plutôt que de se combler")
    void proposesNothingWhenNothingMatches(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("vide"));

        assertTrue(Sources.searchRoots(
                new java.util.LinkedHashSet<>(List.of("com/example/app/Application.java")),
                List.of(dir)).isEmpty());
        assertTrue(Sources.searchRoots(java.util.Set.of(), List.of(dir)).isEmpty(),
                "sans classe manquante, il n'y a rien à chercher");
    }

    @Test
    @DisplayName("Chercher sous une base et sous son parent ne parcourt pas deux fois")
    void searchesAParentOnlyOnce(@TempDir Path dir) throws Exception {
        Path child = dir.resolve("projet/src/main/java");
        Files.createDirectories(child);

        List<Path> kept = Sources.deduplicate(List.of(child, dir, dir.resolve("projet")));

        assertEquals(1, kept.size(), "le parent couvre déjà ses descendants : " + kept);
        assertEquals(dir.toAbsolutePath().normalize(), kept.get(0));
    }

    @Test
    @DisplayName("La racine proposée est celle qu'il aurait fallu écrire, paquet retiré")
    @SuppressWarnings("unchecked")
    void proposesTheRootWithThePackagePathRemoved(@TempDir Path dir) throws Exception {
        source(dir, "depot/module/src/main/java/com/example/app/Application.java", "com.example.app");

        List<Object> leads = Sources.searchRoots(
                new java.util.LinkedHashSet<>(List.of("com/example/app/Application.java")),
                List.of(dir));

        Map<String, Object> lead = (Map<String, Object>) leads.get(0);
        assertEquals(dir.resolve("depot/module/src/main/java").toAbsolutePath().normalize()
                        .toString(),
                lead.get("racine"),
                "on rend le répertoire de sources, pas celui du fichier");
        assertEquals(List.of("com/example/app/Application.java"), lead.get("exemples"),
                "la preuve accompagne le chiffre");
    }

    @Test
    @DisplayName("Le bytecode et les métadonnées ne sont pas traversés")
    void doesNotWalkThroughBuildOutputOrVersionControl(@TempDir Path dir) throws Exception {
        source(dir, ".git/sauvegarde/com/example/app/Application.java", "com.example.app");
        source(dir, "classes/com/example/app/Application.java", "com.example.app");
        source(dir, "node_modules/paquet/com/example/app/Application.java", "com.example.app");

        assertTrue(Sources.searchRoots(
                new java.util.LinkedHashSet<>(List.of("com/example/app/Application.java")),
                List.of(dir)).isEmpty(),
                "ces répertoires ne contiennent jamais les sources d'un projet, et ce sont "
                + "eux qui font exploser le parcours");
    }
}
