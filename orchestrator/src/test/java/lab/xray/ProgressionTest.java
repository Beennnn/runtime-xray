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
class ProgressionTest {

    private static final Duration RIEN = Duration.ZERO;

    @Test
    @DisplayName("Sans terminal, rien n'est écrit du tout")
    void silentWithoutTerminal() {
        StringBuilder sortie = new StringBuilder();
        Progression p = new Progression(sortie, false, false);
        assertFalse(p.active());
        for (int i = 1; i <= 20; i++) {
            p.avancement(Duration.ofSeconds(i), Duration.ofSeconds(i), 1024L * i);
        }
        p.fin();
        assertEquals("", sortie.toString(),
                "une ligne qui se réécrit chaque seconde noierait un journal d'intégration");
    }

    @Test
    @DisplayName("La bande défile et ne dépasse jamais sa largeur")
    void bandScrolls() {
        StringBuilder sortie = new StringBuilder();
        Progression p = new Progression(sortie, true, false);
        for (int i = 1; i <= Progression.LARGEUR + 15; i++) {
            p.avancement(Duration.ofSeconds(i), Duration.ofSeconds(i), 0);
        }
        String derniere = sortie.toString().substring(sortie.lastIndexOf("\r"));
        assertEquals(Progression.LARGEUR, compterCarres(derniere),
                "au-delà, la ligne passerait à la suivante et cesserait de se réécrire");
    }

    @Test
    @DisplayName("Un cœur saturé se voit, même sur une machine qui en a trente-deux")
    void densityCountsCoresNotMachineShare() {
        assertEquals(0, Progression.palier(0.0));
        assertTrue(Progression.palier(1.0) >= 3,
                "ramené à la taille de la machine, un cœur plein passerait pour du repos");
        assertEquals(4, Progression.palier(4.0));
        assertTrue(Progression.palier(0.3) > Progression.palier(0.05),
                "les paliers doivent être croissants, sinon la bande ne veut rien dire");
    }

    @Test
    @DisplayName("La densité porte l'information, la couleur ne fait que la redoubler")
    void colourIsNeverTheOnlyCarrier() {
        StringBuilder sansCouleur = new StringBuilder();
        StringBuilder avecCouleur = new StringBuilder();
        Progression sobre = new Progression(sansCouleur, true, false);
        Progression teinte = new Progression(avecCouleur, true, true);
        for (int i = 1; i <= 6; i++) {
            sobre.avancement(Duration.ofSeconds(i), Duration.ofMillis(i * 400L), 0);
            teinte.avancement(Duration.ofSeconds(i), Duration.ofMillis(i * 400L), 0);
        }
        assertFalse(sansCouleur.toString().contains("\u001b"),
                "NO_COLOR, un tuyau, un terminal ancien : la ligne doit rester lisible");
        assertEquals(compterCarres(sansCouleur.toString()), compterCarres(avecCouleur.toString()),
                "les mêmes charges donnent les mêmes carrés, colorés ou non");
    }

    @Test
    @DisplayName("Une ligne plus courte efface entièrement celle qu'elle remplace")
    void erasesTheLongerLineBefore() {
        StringBuilder sortie = new StringBuilder();
        Progression p = new Progression(sortie, true, false);
        p.avancement(Duration.ofSeconds(1), Duration.ofMillis(1_500), 5L * 1024 * 1024);
        int longue = sortie.length();
        sortie.setLength(0);
        p.avancement(Duration.ofSeconds(2), Duration.ofMillis(1_500), 10);
        assertTrue(sortie.length() >= longue,
                "sans remplissage, « 5,0 Mo » laisserait sa queue derrière « 10 o »");
    }

    @Test
    @DisplayName("Sans temps processeur publié, la sortie de l'application sert de témoin")
    void fallsBackToOutputGrowth() {
        StringBuilder sortie = new StringBuilder();
        Progression p = new Progression(sortie, true, false);
        p.avancement(Duration.ofSeconds(1), RIEN, 100);
        p.avancement(Duration.ofSeconds(2), RIEN, 100);
        p.avancement(Duration.ofSeconds(3), RIEN, 4096);
        String derniere = sortie.toString().substring(sortie.lastIndexOf("\r"));
        assertTrue(derniere.contains("running"),
                "annoncer « cpu 0.0 s » ferait croire à un programme figé");
        assertTrue(derniere.contains("4.0 KB"));
    }

    @Test
    @DisplayName("Les caractères de la bande existent aussi en CP850")
    void glyphsSurviveAWindowsTerminal() {
        // Un poste Windows en cp850 est le défaut, pas l'exception : voir CLAUDE.md.
        for (char c : Progression.PALIERS) {
            assertTrue(java.nio.charset.Charset.forName("IBM850").newEncoder().canEncode(c),
                    "le carré « " + c + " » deviendrait un point d'interrogation");
        }
    }

    private static int compterCarres(String ligne) {
        int n = 0;
        for (char c : ligne.toCharArray()) {
            for (char palier : Progression.PALIERS) {
                if (c == palier) n++;
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
        assertFalse(Progression.demandee(null), "sans rien, le défaut ne change pas");
        assertFalse(Progression.demandee(""));
        assertFalse(Progression.demandee("0"), "0 impose le silence, il ne le lève pas");
        assertFalse(Progression.demandee("false"));
        assertTrue(Progression.demandee("1"));
        assertTrue(Progression.demandee("oui"),
                "toute autre valeur vaut demande : on ne fait pas deviner une syntaxe");
    }
}
