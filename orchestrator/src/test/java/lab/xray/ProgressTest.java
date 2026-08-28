package lab.xray;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La bande d'activité est un confort, et un confort ne doit rien coûter à ce qu'il
 * accompagne : ni la mesure, ni la lisibilité d'un journal, ni la capture.
 */
class ProgressTest {

    private static final Duration NOTHING = Duration.ZERO;

    @Test
    @DisplayName("Sans terminal, rien n'est écrit du tout")
    void silentWithoutTerminal() {
        StringBuilder out = new StringBuilder();
        Progress p = new Progress(out, false, false);
        assertFalse(p.active());
        for (int i = 1; i <= 20; i++) {
            p.tick(Duration.ofSeconds(i), Duration.ofSeconds(i), 1024L * i);
        }
        p.end();
        assertEquals("", out.toString(),
                "une ligne qui se réécrit chaque seconde noierait un journal d'intégration");
    }

    @Test
    @DisplayName("La bande défile et ne dépasse jamais sa largeur")
    void bandScrolls() {
        StringBuilder out = new StringBuilder();
        Progress p = new Progress(out, true, false);
        for (int i = 1; i <= Progress.WIDTH + 15; i++) {
            p.tick(Duration.ofSeconds(i), Duration.ofSeconds(i), 0);
        }
        String last = out.toString().substring(out.lastIndexOf("\r"));
        assertEquals(Progress.WIDTH, countSquares(last),
                "au-delà, la ligne passerait à la suivante et cesserait de se réécrire");
    }

    @Test
    @DisplayName("Un cœur saturé se voit, même sur une machine qui en a trente-deux")
    void densityCountsCoresNotMachineShare() {
        assertEquals(0, Progress.tier(0.0));
        assertTrue(Progress.tier(1.0) >= 3,
                "ramené à la taille de la machine, un cœur plein passerait pour du repos");
        assertEquals(4, Progress.tier(4.0));
        assertTrue(Progress.tier(0.3) > Progress.tier(0.05),
                "les paliers doivent être croissants, sinon la bande ne veut rien dire");
    }

    @Test
    @DisplayName("La densité porte l'information, la couleur ne fait que la redoubler")
    void colourIsNeverTheOnlyCarrier() {
        StringBuilder withoutColour = new StringBuilder();
        StringBuilder withColour = new StringBuilder();
        Progress plain = new Progress(withoutColour, true, false);
        Progress tint = new Progress(withColour, true, true);
        for (int i = 1; i <= 6; i++) {
            plain.tick(Duration.ofSeconds(i), Duration.ofMillis(i * 400L), 0);
            tint.tick(Duration.ofSeconds(i), Duration.ofMillis(i * 400L), 0);
        }
        assertFalse(withoutColour.toString().contains("\u001b"),
                "NO_COLOR, un tuyau, un terminal ancien : la ligne doit rester lisible");
        assertEquals(countSquares(withoutColour.toString()), countSquares(withColour.toString()),
                "les mêmes charges donnent les mêmes carrés, colorés ou non");
    }

    @Test
    @DisplayName("Une ligne plus courte efface entièrement celle qu'elle remplace")
    void erasesTheLongerLineBefore() {
        StringBuilder out = new StringBuilder();
        Progress p = new Progress(out, true, false);
        p.tick(Duration.ofSeconds(1), Duration.ofMillis(1_500), 5L * 1024 * 1024);
        int longLine = out.length();
        out.setLength(0);
        p.tick(Duration.ofSeconds(2), Duration.ofMillis(1_500), 10);
        assertTrue(out.length() >= longLine,
                "sans remplissage, « 5,0 Mo » laisserait sa queue derrière « 10 o »");
    }

    @Test
    @DisplayName("Sans temps processeur publié, la sortie de l'application sert de témoin")
    void fallsBackToOutputGrowth() {
        StringBuilder out = new StringBuilder();
        Progress p = new Progress(out, true, false);
        p.tick(Duration.ofSeconds(1), NOTHING, 100);
        p.tick(Duration.ofSeconds(2), NOTHING, 100);
        p.tick(Duration.ofSeconds(3), NOTHING, 4096);
        String last = out.toString().substring(out.lastIndexOf("\r"));
        assertTrue(last.contains("running"),
                "annoncer « cpu 0.0 s » ferait croire à un programme figé");
        assertTrue(last.contains("4.0 KB"));
    }

    @Test
    @DisplayName("Les caractères de la bande existent aussi en CP850")
    void glyphsSurviveAWindowsTerminal() {
        // Un poste Windows en cp850 est le défaut, pas l'exception : voir CLAUDE.md.
        for (char c : Progress.TIERS) {
            assertTrue(java.nio.charset.Charset.forName("IBM850").newEncoder().canEncode(c),
                    "le carré « " + c + " » deviendrait un point d'interrogation");
        }
    }

    private static int countSquares(String line) {
        int n = 0;
        for (char c : line.toCharArray()) {
            for (char tier : Progress.TIERS) {
                if (c == tier) n++;
            }
        }
        return n;
    }

    @Test
    @DisplayName("Un shell imbriqué peut réclamer la bande, et aussi la faire taire")
    void anestedShellCanAskForTheBandAndAlsoSilenceIt() {
        // Un shell qui en lance un autre met souvent un tuyau au milieu : la JVM ne voit
        // plus de terminal alors qu'un opérateur regarde bien un écran au bout. Le défaut
        // reste le silence ; celui qui sait qu'il regarde doit pouvoir le dire.
        assertFalse(Progress.requested(null), "sans rien, le défaut ne change pas");
        assertFalse(Progress.requested(""));
        assertFalse(Progress.requested("0"), "0 impose le silence, il ne le lève pas");
        assertFalse(Progress.requested("false"));
        assertTrue(Progress.requested("1"));
        assertTrue(Progress.requested("oui"),
                "toute autre valeur vaut demande : on ne fait pas deviner une syntaxe");
    }
}
