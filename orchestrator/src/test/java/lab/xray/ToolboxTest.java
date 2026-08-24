package lab.xray;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La machine visée n'a pas de réseau : c'est le cas d'usage qui a motivé l'outil. Ce que
 * ces tests éprouvent n'est donc pas le téléchargement, mais tout ce qui permet de s'en
 * passer — et la qualité du message quand on ne peut vraiment pas.
 *
 * <p>Le dépôt est partout pointé sur une adresse morte : un test qui passerait en sortant
 * sur le réseau ne prouverait rien.
 */
class ToolboxTest {

    private static final String MORT = "http://127.0.0.1:1/aucun-depot";

    private Path fichier(Path dir, String nom) throws IOException {
        Files.createDirectories(dir);
        Path f = dir.resolve(nom);
        Files.writeString(f, "composant", StandardCharsets.UTF_8);
        return f;
    }

    private Toolbox outils(Path cache, List<Path> plats, Path depotLocal) {
        return new Toolbox(MORT, cache, plats, depotLocal);
    }

    @Test
    @DisplayName("Un composant déposé à côté du jar est pris tel quel, sans réseau")
    void takesComponentPlacedNextToTheJar(@TempDir Path dir) throws Exception {
        Path apporte = fichier(dir.resolve("voisinage"), "org.jacoco.agent-0.8.13-runtime.jar");

        Toolbox t = outils(dir.resolve("cache"), List.of(dir.resolve("voisinage")),
                dir.resolve("m2"));

        assertEquals(apporte, t.jacocoAgent());
    }

    @Test
    @DisplayName("Il est aussi reconnu sous le nom que lui donne son éditeur")
    void acceptsTheNameFromTheOfficialDistribution(@TempDir Path dir) throws Exception {
        // Celui qui a « le fichier de JaCoCo » sous la main l'a pris dans la distribution
        // de JaCoCo, pas sur Maven : il s'appelle jacocoagent.jar, et rien d'autre.
        Path apporte = fichier(dir.resolve("voisinage"), "jacocoagent.jar");

        Toolbox t = outils(dir.resolve("cache"), List.of(dir.resolve("voisinage")),
                dir.resolve("m2"));

        assertEquals(apporte, t.jacocoAgent());
    }

    @Test
    @DisplayName("À défaut, le dépôt Maven local de la machine fait l'affaire")
    void fallsBackToTheLocalMavenRepository(@TempDir Path dir) throws Exception {
        Path m2 = dir.resolve("m2");
        Path apporte = fichier(m2.resolve("org/jacoco/org.jacoco.cli/0.8.13"),
                "org.jacoco.cli-0.8.13-nodeps.jar");

        Toolbox t = outils(dir.resolve("cache"), List.of(), m2);

        assertEquals(apporte, t.jacocoCli());
    }

    @Test
    @DisplayName("Le cache de l'outil garde la priorité sur tout le reste")
    void theToolsOwnCacheComesFirst(@TempDir Path dir) throws Exception {
        Path cache = dir.resolve("cache");
        Path attendu = fichier(cache, "jfr-converter-4.1.jar");
        fichier(dir.resolve("voisinage"), "jfr-converter-4.1.jar");
        fichier(dir.resolve("m2/tools/profiler/jfr-converter/4.1"), "jfr-converter-4.1.jar");

        Toolbox t = outils(cache, List.of(dir.resolve("voisinage")), dir.resolve("m2"));

        assertEquals(attendu, t.asyncProfilerConverter());
    }

    @Test
    @DisplayName("Un Arthas déjà décompressé est reconnu, sous ses deux noms de répertoire")
    void findsAnAlreadyUnpackedArthas(@TempDir Path dir) throws Exception {
        Path voisinage = dir.resolve("voisinage");
        fichier(voisinage.resolve("arthas"), "arthas-boot.jar");

        Toolbox t = outils(dir.resolve("cache"), List.of(voisinage), dir.resolve("m2"));

        assertEquals(voisinage.resolve("arthas"), t.arthasHome());
    }

    @Test
    @DisplayName("Les répertoires sont fouillés dans l'ordre annoncé : le premier gagne")
    void searchesInTheAnnouncedOrder(@TempDir Path dir) throws Exception {
        Path designe = dir.resolve("designe");
        Path voisinage = dir.resolve("voisinage");
        Path attendu = fichier(designe, "jacocoagent.jar");
        fichier(voisinage, "jacocoagent.jar");

        Toolbox t = outils(dir.resolve("cache"), List.of(designe, voisinage), dir.resolve("m2"));

        assertEquals(attendu, t.jacocoAgent());
    }

    @Test
    @DisplayName("L'édition complète porte ses composants : ils sont déposés dans le cache")
    void extractsAComponentEmbeddedInTheJar(@TempDir Path dir) throws Exception {
        // Le classpath de test porte /lab/xray/composants/jfr-converter-4.1.jar, comme le
        // ferait le jar construit avec -Pcomplet. Rien sur le disque, dépôt injoignable.
        Path cache = dir.resolve("cache");

        Path obtenu = outils(cache, List.of(), dir.resolve("m2")).asyncProfilerConverter();

        assertEquals(cache.resolve("jfr-converter-4.1.jar"), obtenu,
                "un -javaagent veut un fichier : le composant doit atterrir sur le disque");
        assertTrue(Files.isRegularFile(obtenu));
        assertTrue(Files.list(cache).noneMatch(f -> f.getFileName().toString().startsWith("ex-")),
                "aucun fichier de travail ne doit rester derrière");
    }

    @Test
    @DisplayName("Les versions du pom ne divergent pas des constantes du code")
    void thePomVersionsMatchTheCode() throws Exception {
        // Le profil « complet » embarque ce que dit le pom ; à l'exécution, Toolbox cherche
        // ce que disent ses constantes. Deux sources qui divergeraient donneraient un jar
        // d'apparence complète, qui retéléchargerait tout.
        String pom = Files.readString(Path.of("pom.xml"), StandardCharsets.UTF_8);
        String code = Files.readString(
                Path.of("src/main/java/lab/xray/Toolbox.java"), StandardCharsets.UTF_8);

        for (String[] paire : new String[][]{{"jacoco.version", "JACOCO_VERSION"},
                                             {"arthas.version", "ARTHAS_VERSION"},
                                             {"async.version", "ASYNC_VERSION"}}) {
            String duPom = entre(pom, "<" + paire[0] + ">", "</" + paire[0] + ">");
            String duCode = entre(code, paire[1] + " = \"", "\"");
            assertEquals(duCode, duPom, paire[0] + " et " + paire[1] + " doivent coïncider");
        }
    }

    private static String entre(String texte, String debut, String fin) {
        int i = texte.indexOf(debut);
        assertTrue(i >= 0, "marqueur introuvable : " + debut);
        i += debut.length();
        return texte.substring(i, texte.indexOf(fin, i));
    }

    @Test
    @DisplayName("Introuvable, le message dit où l'on a cherché et comment s'en sortir")
    void tellsWhereItLookedWhenNothingIsFound(@TempDir Path dir) {
        Path cache = dir.resolve("cache");
        Path voisinage = dir.resolve("voisinage");

        Toolbox t = outils(cache, List.of(voisinage), dir.resolve("m2"));

        IOException echec = assertThrows(IOException.class, t::jacocoAgent);
        String message = echec.getMessage();
        assertTrue(message.contains("org.jacoco.agent-0.8.13-runtime.jar"),
                "le composant manquant doit être nommé : " + message);
        assertTrue(message.contains(cache.toString()) && message.contains(voisinage.toString()),
                "les répertoires fouillés doivent être listés : " + message);
        assertTrue(message.contains("--composants") && message.contains("--repo"),
                "les deux issues doivent être rappelées : " + message);
        assertTrue(message.contains("embarqué"), "l'édition complète doit être évoquée : " + message);
    }
}
