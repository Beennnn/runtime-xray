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
 * The JSON produced here is <b>embedded in a script tag</b>: an escaping mistake does not
 * break a data file, it breaks the whole page. Hence the insistence on the awkward
 * characters.
 */
class JsonTest {

    @Test
    @DisplayName("A string with quotes and newlines reads back identically")
    void escapesAndReadsBack() {
        String tricky = "il a dit \"bonjour\"\nligne 2\tfin\\";
        Object back = Json.read(Json.write(Map.of("k", tricky)));
        assertEquals(tricky, ((Map<?, ?>) back).get("k"));
    }

    @Test
    @DisplayName("The Unicode line separators are escaped: they would break the script")
    void escapesUnicodeLineSeparators() {
        // U+2028 and U+2029 are valid in JSON but end a line as far as a JavaScript engine
        // is concerned: unescaped, the page no longer loads.
        String json = Json.write(Map.of("k", "avant\u2028apres\u2029fin"));
        assertTrue(json.contains("\\u2028"), "U+2028 must be escaped: " + json);
        assertTrue(json.contains("\\u2029"), "U+2029 must be escaped: " + json);
        assertEquals("avant\u2028apres\u2029fin", ((Map<?, ?>) Json.read(json)).get("k"));
    }

    @Test
    @DisplayName("The control characters are escaped")
    void escapesControlCharacters() {
        String json = Json.write(Map.of("k", "a\u0001b"));
        assertTrue(json.contains("\\u0001"), json);
    }

    @Test
    @DisplayName("NaN and infinity become 0 rather than an unreadable document")
    void neutralisesNonFiniteNumbers() {
        String json = Json.write(Map.of("a", Double.NaN, "b", Double.POSITIVE_INFINITY));
        assertTrue(json.contains("\"a\":0"), json);
        assertTrue(json.contains("\"b\":0"), json);
        assertInstanceOf(Map.class, Json.read(json));
    }

    @Test
    @DisplayName("Nested structures: objects, arrays, null, booleans, numbers")
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
    @DisplayName("An empty array and an empty object read back without error")
    void emptyContainers() {
        assertEquals(List.of(), Json.read("[]"));
        assertEquals(Map.of(), Json.read("{}"));
    }

    @Test
    @DisplayName("Accents and emoji come through undamaged")
    void keepsNonAsciiIntact() {
        String s = "exécution — durée : 12 µs ✅";
        assertEquals(s, ((Map<?, ?>) Json.read(Json.write(Map.of("k", s)))).get("k"));
    }
}
