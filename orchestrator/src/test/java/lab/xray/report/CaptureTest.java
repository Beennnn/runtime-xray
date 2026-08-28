package lab.xray.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Une campagne de recette coûte une matinée. Ce numéro existe pour qu'une mise à jour de
 * l'outil ne la fasse pas rejouer : c'est la décision que ces tests gardent, et elle se perd
 * au premier « tant pis, on remesurera ».
 */
class CaptureTest {

    @Test
    @DisplayName("Une capture qui n'annonce rien reste lisible")
    void aCaptureWithoutAVersionIsStillReadable() {
        // Le silence d'hier ne doit pas coûter une campagne : les exécutions faites avant que
        // ce contrat n'existe relèvent de la forme d'origine, et se relisent.
        assertEquals(Capture.ORIGIN, Capture.versionOf(null));
        assertEquals(Capture.ORIGIN, Capture.versionOf(new HashMap<>()));
        assertEquals(Capture.ORIGIN, Capture.versionOf(Map.of(Capture.FIELD, "  ")));
        assertTrue(Capture.readable(Capture.versionOf(null)));
    }

    @Test
    @DisplayName("Une capture plus récente que l'outil se lit quand même, et se signale")
    void aCaptureNewerThanTheToolIsStillRead() {
        // Refuser de lire ferait perdre une campagne pour des champs qu'on ignore.
        assertTrue(Capture.newerThanTheTool("9.0"));
        assertTrue(Capture.readable("9.0"));
        assertFalse(Capture.newerThanTheTool(Capture.CURRENT));
    }

    @Test
    @DisplayName("Les versions se comparent par nombres, pas par texte")
    void versionsCompareNumerically() {
        // « 1.10 » est postérieur à « 1.9 » ; comparé comme du texte, il serait antérieur, et
        // l'outil réclamerait une nouvelle mesure sans raison.
        assertTrue(Capture.compare("1.10", "1.9") > 0);
        assertTrue(Capture.compare("2.0", "1.99") > 0);
        assertEquals(0, Capture.compare("1.1", "1.1"));
        assertEquals(0, Capture.compare("1", "1.0"), "un segment absent vaut zéro");
    }

    @Test
    @DisplayName("La version minimale ne dépasse jamais la version courante")
    void theMinimumNeverExceedsWhatWeWrite() {
        // Sinon l'outil refuserait les captures qu'il vient lui-même de produire.
        assertTrue(Capture.compare(Capture.MINIMUM, Capture.CURRENT) <= 0);
        assertTrue(Capture.readable(Capture.CURRENT));
        assertTrue(Capture.readable(Capture.ORIGIN),
                "monter la minimale oblige tout le monde à remesurer : ce test le rend visible");
    }
}
