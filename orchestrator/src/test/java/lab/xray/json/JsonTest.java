package lab.xray.json;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le JSON produit ici est <b>embarqué dans une balise script</b> : une erreur d'échappement
 * ne casse pas un fichier de données, elle casse la page entière. D'où l'insistance sur les
 * caractères pénibles.
 */
class JsonTest {

    @Test
    @DisplayName("Une chaîne avec guillemets et retours à la ligne se relit à l'identique")
    void escapesAndReadsBack() {
        String tricky = "il a dit \"bonjour\"\nligne 2\tfin\\";
        Object back = Json.read(Json.write(Map.of("k", tricky)));
        assertEquals(tricky, ((Map<?, ?>) back).get("k"));
    }

    @Test
    @DisplayName("Les séparateurs de ligne Unicode sont échappés : ils casseraient le script")
    void escapesUnicodeLineSeparators() {
        // U+2028 et U+2029 sont valides en JSON mais terminent une ligne pour un moteur
        // JavaScript : non échappés, la page ne se charge plus.
        String json = Json.write(Map.of("k", "avant\u2028apres\u2029fin"));
        assertTrue(json.contains("\\u2028"), "U+2028 doit être échappé : " + json);
        assertTrue(json.contains("\\u2029"), "U+2029 doit être échappé : " + json);
        assertEquals("avant\u2028apres\u2029fin", ((Map<?, ?>) Json.read(json)).get("k"));
    }

    @Test
    @DisplayName("Les caractères de contrôle sont échappés")
    void escapesControlCharacters() {
        String json = Json.write(Map.of("k", "a\u0001b"));
        assertTrue(json.contains("\\u0001"), json);
    }

    @Test
    @DisplayName("NaN et l'infini deviennent 0 plutôt qu'un document illisible")
    void neutralisesNonFiniteNumbers() {
        String json = Json.write(Map.of("a", Double.NaN, "b", Double.POSITIVE_INFINITY));
        assertTrue(json.contains("\"a\":0"), json);
        assertTrue(json.contains("\"b\":0"), json);
        assertInstanceOf(Map.class, Json.read(json));
    }

    @Test
    @DisplayName("Structures imbriquées : objets, tableaux, null, booléens, nombres")
    void nestedStructures() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("liste", List.of(1, 2.5, "trois"));
        data.put("vide", null);
        data.put("vrai", true);
        data.put("objet", Map.of("dedans", List.of(Map.of("x", 1))));

        @SuppressWarnings("unchecked")
        Map<String, Object> back = (Map<String, Object>) Json.read(Json.write(data));
        assertEquals(3, ((List<?>) back.get("liste")).size());
        assertNull(back.get("vide"));
        assertEquals(Boolean.TRUE, back.get("vrai"));
        assertEquals(1.0, ((Map<?, ?>) ((List<?>) ((Map<?, ?>) back.get("objet")).get("dedans")).get(0)).get("x"));
    }

    @Test
    @DisplayName("Un tableau et un objet vides se relisent sans erreur")
    void emptyContainers() {
        assertEquals(List.of(), Json.read("[]"));
        assertEquals(Map.of(), Json.read("{}"));
    }

    @Test
    @DisplayName("Les accents et emojis traversent sans dommage")
    void keepsNonAsciiIntact() {
        String s = "exécution — durée : 12 µs ✅";
        assertEquals(s, ((Map<?, ?>) Json.read(Json.write(Map.of("k", s)))).get("k"));
    }
}
