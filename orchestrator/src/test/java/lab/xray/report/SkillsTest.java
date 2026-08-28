package lab.xray.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Les deux compétences de {@code skills/} sont lues par un assistant, en général sur la
 * machine observée — donc sans réseau, sans le dépôt, et sans personne pour corriger.
 *
 * <p>Une option qu'on renomme ici et qui reste écrite là-bas n'échoue nulle part : elle
 * envoie quelqu'un dans le mur, avec un « Option inconnue » pour toute explication, sur la
 * machine où il ne peut rien vérifier. D'où ces tests — ils ne gardent pas une prose, ils
 * gardent l'accord entre ce que le code accepte et ce que la compétence promet.
 */
class SkillsTest {

    private static Path root() {
        // Surefire lance avec le répertoire du module pour base ; le dépôt est au-dessus.
        return Path.of("").toAbsolutePath().getParent();
    }

    private static String read(String skill) throws Exception {
        Path f = root().resolve("skills").resolve(skill).resolve("SKILL.md");
        assertTrue(Files.exists(f), "compétence introuvable : " + f);
        return Files.readString(f, StandardCharsets.UTF_8);
    }

    /** Les options que le code accepte réellement, lues dans le {@code switch} de Main. */
    private static Set<String> optionsInCode() throws Exception {
        String source = Files.readString(root().resolve("orchestrator").resolve("src")
                .resolve("main").resolve("java").resolve("lab").resolve("xray")
                .resolve("Main.java"), StandardCharsets.UTF_8);
        Set<String> out = new LinkedHashSet<>();
        Matcher m = Pattern.compile("case \"(--[a-z-]+)\"").matcher(source);
        while (m.find()) out.add(m.group(1));
        // Le switch les écrit groupées : « case "-h", "--help" ».
        Matcher m2 = Pattern.compile(", \"(--[a-z-]+)\"").matcher(source);
        while (m2.find()) out.add(m2.group(1));
        assertTrue(out.size() > 15, "l'extraction a raté : " + out);
        return out;
    }

    private static Set<String> quotedOptions(String text) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = Pattern.compile("`?(--[a-z][a-z-]+)").matcher(text);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    @Test
    @DisplayName("Toute option citée par une compétence existe vraiment dans l'outil")
    void everyOptionQuotedByASkillReallyExists() throws Exception {
        Set<String> real = optionsInCode();
        for (String skill : new String[]{"runtime-xray", "runtime-xray-lire"}) {
            for (String quoted : quotedOptions(read(skill))) {
                assertTrue(real.contains(quoted), "la compétence " + skill
                        + " cite " + quoted + ", que l'outil refuse. Sur la machine observée,"
                        + " le lecteur n'a que « Option inconnue » pour s'en sortir. Connues : "
                        + real);
            }
        }
    }

    @Test
    @DisplayName("Le vocabulaire des faits promis au lecteur est celui que l'outil écrit")
    void thePromisedVocabularyIsTheOneWritten() throws Exception {
        String read = read("runtime-xray-lire");
        for (String family : Facts.VOCABULARY.keySet()) {
            assertTrue(read.contains(family), "la compétence de lecture ne dit rien de « "
                    + family + " » : le lecteur rencontrera une ligne qu'il ne sait pas lire");
        }
        // L'inverse compte autant : une famille annoncée et jamais écrite se cherche
        // longtemps dans un fichier qui ne la contient pas. On ne regarde que la table du
        // vocabulaire — les autres tables du document parlent de fichiers, pas de familles.
        int start = read.indexOf("## Le vocabulaire");
        assertTrue(start > 0, "la table du vocabulaire doit exister sous ce titre");
        String table = read.substring(start, read.indexOf("\n## ", start + 4));
        Matcher m = Pattern.compile("\\| `([a-z][a-z._]+)` \\|").matcher(table);
        while (m.find()) {
            assertTrue(Facts.VOCABULARY.containsKey(m.group(1)),
                    "la compétence annonce la famille « " + m.group(1)
                    + " », que l'outil n'écrit jamais");
        }
    }

    @Test
    @DisplayName("Les quatre restrictions sont dites distinctes, là où on les confond")
    void theFourRestrictionsAreSaidToBeDistinct() throws Exception {
        // C'est la confusion la plus coûteuse de l'outil : on élargit le filtre qui n'y
        // était pour rien, on relance une campagne entière, on obtient le même rapport. Les
        // deux compétences doivent la désamorcer, chacune de son côté du problème.
        for (String skill : new String[]{"runtime-xray", "runtime-xray-lire"}) {
            String text = read(skill);
            for (String option : new String[]{"--sources", "--root", "--filter", "--cover"}) {
                assertTrue(text.contains(option),
                        skill + " ne nomme pas " + option + " parmi les restrictions");
            }
        }
    }

    @Test
    @DisplayName("Un zéro qui n'a pas été mesuré est annoncé comme tel, pas comme un constat")
    void aZeroThatWasNotMeasuredIsAnnouncedAsSuch() throws Exception {
        // Sans cette phrase, un temps à zéro sous Windows se lit « jamais appelé », et la
        // conclusion tombe dans le mauvais sens avec assurance.
        String read = read("runtime-xray-lire");
        assertTrue(read.contains("indisponibilite"));
        assertTrue(read.contains("Windows"), "le cas concret vaut mieux que la règle seule");
        assertTrue(read("runtime-xray").contains("Windows"));
    }

    @Test
    @DisplayName("Chaque compétence porte l'en-tête qui la rend déclenchable")
    void eachSkillCarriesTheHeaderThatMakesItTriggerable() throws Exception {
        for (String skill : new String[]{"runtime-xray", "runtime-xray-lire"}) {
            String text = read(skill);
            assertTrue(text.startsWith("---\n"), skill + " : pas d'en-tête");
            String header = text.substring(0, text.indexOf("\n---", 4));
            assertTrue(header.contains("name: " + skill),
                    skill + " : le nom déclaré doit être celui du répertoire");
            // Une description sans le vocabulaire de la question ne se déclenche jamais :
            // la compétence existe, et personne ne la voit.
            assertTrue(header.contains("description:"));
            assertTrue(header.length() > 200, skill + " : description trop maigre pour "
                    + "être choisie sur autre chose que son nom");
            assertFalse(text.contains("runtime-xray-cli"),
                    skill + " : le nom du jar publié change de version en version, "
                    + "l'écrire ici le périme");
        }
    }
}
