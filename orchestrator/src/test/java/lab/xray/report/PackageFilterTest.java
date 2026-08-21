package lab.xray.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Masquer un paquet retire du code de la vue. Une règle trop large emporte du code métier
 * sans le dire — c'est le mode d'échec à couvrir en priorité, parce qu'il est silencieux :
 * on ne remarque pas l'absence de ce qu'on ne cherchait pas.
 */
class PackageFilterTest {

    @Test
    @DisplayName("Sans consigne, rien n'est masqué")
    void emptyByDefault() {
        assertTrue(PackageFilter.of("").isEmpty());
        assertTrue(PackageFilter.of(null).isEmpty());
        assertFalse(PackageFilter.of("   ").hidden("org/slf4j/Logger"));
    }

    @Test
    @DisplayName("Les deux écritures d'un paquet sont acceptées, avec ou sans étoile")
    void acceptsBothNotations() {
        for (String spec : new String[]{"org.slf4j", "org/slf4j", "org.slf4j.*", "org/slf4j/*"}) {
            assertTrue(PackageFilter.of(spec).hidden("org/slf4j/Logger.debug"),
                    "forme refusée : " + spec);
        }
    }

    @Test
    @DisplayName("Un sous-paquet est masqué avec son parent")
    void hidesSubPackages() {
        PackageFilter f = PackageFilter.of("org.slf4j");
        assertTrue(f.hidden("org/slf4j/helpers/Util.report"));
    }

    @Test
    @DisplayName("Un paquet au nom voisin n'est PAS emporté")
    void doesNotCatchNeighbours() {
        // C'est l'erreur silencieuse : « org.slf4j » ne doit pas masquer « org.slf4jext ».
        PackageFilter f = PackageFilter.of("org.slf4j");
        assertFalse(f.hidden("org/slf4jext/Bridge.log"));
        assertFalse(f.hidden("org/slf4j"), "le paquet lui-même, sans classe, n'est pas un membre");
    }

    @Test
    @DisplayName("Plusieurs paquets, séparés par virgule, point-virgule ou espace")
    void readsSeveralEntries() {
        PackageFilter f = PackageFilter.of("org.slf4j, io.netty; ch.qos.logback");
        assertTrue(f.hidden("org/slf4j/Logger"));
        assertTrue(f.hidden("io/netty/channel/Pipeline"));
        assertTrue(f.hidden("ch/qos/logback/core/Appender"));
        assertFalse(f.hidden("lab/sample/RoutePlanner.travelTimeMinutes"));
    }

    @Test
    @DisplayName("Le code métier n'est jamais masqué par une règle qui ne le nomme pas")
    void neverHidesUnrelatedCode() {
        PackageFilter f = PackageFilter.of("org.slf4j, org.apache.commons");
        assertFalse(f.hidden("lab/sample/comfort/Breaks.count"));
        assertFalse(f.hidden("com/exemple/moteur/Calcul.calculer"));
    }

    @Test
    @DisplayName("La liste d'origine est conservée telle quelle, pour être montrée au lecteur")
    void keepsOriginalSpec() {
        assertTrue(PackageFilter.of("org.slf4j, io.netty").spec().contains("io.netty"));
    }
}
