package lab.xray;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The command comes from the user and has no imposed shape: an approximate split would
 * launch the wrong program, or lose an argument without saying so.
 */
class CommandLineTest {

    @Test
    @DisplayName("A simple split on spaces")
    void simpleCommand() {
        assertEquals(List.of("java", "-jar", "app.jar", "--profil", "recette"),
                CommandLine.tokenize("java -jar app.jar --profil recette"));
    }

    @Test
    @DisplayName("A quoted argument stays whole, spaces included")
    void keepsQuotedArgumentsWhole() {
        assertEquals(List.of("java", "-jar", "mon app.jar", "--nom", "cas nominal"),
                CommandLine.tokenize("java -jar \"mon app.jar\" --nom 'cas nominal'"));
    }

    @Test
    @DisplayName("Repeated spaces do not produce empty arguments")
    void collapsesRepeatedSpaces() {
        assertEquals(List.of("java", "-jar", "app.jar"),
                CommandLine.tokenize("  java   -jar    app.jar  "));
    }

    @Test
    @DisplayName("An explicitly empty argument is kept")
    void keepsExplicitEmptyArgument() {
        assertEquals(List.of("prog", ""), CommandLine.tokenize("prog \"\""));
    }

    @Test
    @DisplayName("A command without a metacharacter needs no interpreter")
    void plainCommandNeedsNoShell() {
        assertFalse(CommandLine.needsShell("java -jar app.jar --profil recette"));
        assertEquals("java", CommandLine.toProcessArgs("java -jar app.jar").get(0));
    }

    @Test
    @DisplayName("Pipes, chaining and variables go through the system's interpreter")
    void shellConstructsNeedShell() {
        assertTrue(CommandLine.needsShell("java -jar app.jar | tee sortie.log"));
        assertTrue(CommandLine.needsShell("build && java -jar app.jar"));
        assertTrue(CommandLine.needsShell("java -jar $APP_HOME/app.jar"));
        assertTrue(CommandLine.needsShell("java -jar app.jar > sortie.log"));
    }

    @Test
    @DisplayName("A metacharacter inside quotes does not force the interpreter")
    void quotedMetacharacterIsNotAShellConstruct() {
        // A filter pattern contains a star: that is not a shell construct.
        assertFalse(CommandLine.needsShell("java -Dfilter=\"com/example/*\" -jar app.jar"));
    }

    @Test
    @DisplayName("The command passed to the interpreter is not rewritten")
    void shellCommandIsPassedVerbatim() {
        List<String> args = CommandLine.toProcessArgs("a && b");
        assertEquals("a && b", args.get(args.size() - 1));
    }

    @Test
    @DisplayName("A tilde in the middle of a path is not a shell character")
    void aTildeInsideAPathIsNotAShellCharacter() {
        // Windows's 8.3 short names carry one, and they are everywhere:
        // C:\Users\RUNNER~1, C:\PROGRA~1. Counting that tilde as a request for expansion
        // sent the command to "cmd /c" — it ran, but the tool no longer read the -jar in
        // it, so no bytecode, so an empty report with no explanation.
        assertFalse(CommandLine.needsShell(
                "java -jar C:\\Users\\RUNNER~1\\AppData\\Local\\Temp\\appli.jar"));
        assertEquals(List.of("java", "-jar", "C:\\Users\\RUNNER~1\\appli.jar"),
                CommandLine.toProcessArgs("java -jar C:\\Users\\RUNNER~1\\appli.jar"));
    }

    @Test
    @DisplayName("A tilde at the start of a word stays an expansion, so an interpreter")
    void aTildeStartingAWordStillNeedsAShell() {
        // That is where, and only where, an interpreter expands it to a home directory.
        assertTrue(CommandLine.needsShell("java -jar ~/apps/appli.jar"));
        assertTrue(CommandLine.needsShell("~/bin/java -jar appli.jar"));
    }
}
