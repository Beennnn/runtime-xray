package lab.xray;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La commande vient de l'utilisateur et n'a aucune forme imposée : un découpage approximatif
 * lancerait le mauvais programme, ou perdrait un argument sans le dire.
 */
class CommandLineTest {

    @Test
    @DisplayName("Découpage simple sur les espaces")
    void simpleCommand() {
        assertEquals(List.of("java", "-jar", "app.jar", "--profil", "recette"),
                CommandLine.tokenize("java -jar app.jar --profil recette"));
    }

    @Test
    @DisplayName("Un argument entre guillemets reste entier, espaces compris")
    void keepsQuotedArgumentsWhole() {
        assertEquals(List.of("java", "-jar", "mon app.jar", "--nom", "cas nominal"),
                CommandLine.tokenize("java -jar \"mon app.jar\" --nom 'cas nominal'"));
    }

    @Test
    @DisplayName("Les espaces multiples ne produisent pas d'arguments vides")
    void collapsesRepeatedSpaces() {
        assertEquals(List.of("java", "-jar", "app.jar"),
                CommandLine.tokenize("  java   -jar    app.jar  "));
    }

    @Test
    @DisplayName("Un argument vide explicite est conservé")
    void keepsExplicitEmptyArgument() {
        assertEquals(List.of("prog", ""), CommandLine.tokenize("prog \"\""));
    }

    @Test
    @DisplayName("Une commande sans métacaractère n'a pas besoin d'interpréteur")
    void plainCommandNeedsNoShell() {
        assertFalse(CommandLine.needsShell("java -jar app.jar --profil recette"));
        assertEquals("java", CommandLine.toProcessArgs("java -jar app.jar").get(0));
    }

    @Test
    @DisplayName("Tubes, enchaînements et variables passent par l'interpréteur du système")
    void shellConstructsNeedShell() {
        assertTrue(CommandLine.needsShell("java -jar app.jar | tee sortie.log"));
        assertTrue(CommandLine.needsShell("build && java -jar app.jar"));
        assertTrue(CommandLine.needsShell("java -jar $APP_HOME/app.jar"));
        assertTrue(CommandLine.needsShell("java -jar app.jar > sortie.log"));
    }

    @Test
    @DisplayName("Un métacaractère à l'intérieur de guillemets n'impose pas l'interpréteur")
    void quotedMetacharacterIsNotAShellConstruct() {
        // Le motif d'un filtre contient une étoile : ce n'est pas une construction de shell.
        assertFalse(CommandLine.needsShell("java -Dfilter=\"com/exemple/*\" -jar app.jar"));
    }

    @Test
    @DisplayName("La commande passée à l'interpréteur n'est pas réécrite")
    void shellCommandIsPassedVerbatim() {
        List<String> args = CommandLine.toProcessArgs("a && b");
        assertEquals("a && b", args.get(args.size() - 1));
    }
}
