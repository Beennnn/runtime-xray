package lab.xray.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lecture et écriture JSON minimales.
 *
 * <p>Pourquoi ne pas prendre une bibliothèque : l'outil doit pouvoir être déposé sur une
 * machine et lancé, sans rien résoudre. Une seule dépendance suffirait à casser cette
 * promesse en environnement déconnecté. Le format qu'on manipule tient en quatre types —
 * objet, tableau, chaîne, nombre — et ne justifie pas plus.
 */
public final class Json {

    private Json() {}

    // ------------------------------------------------------------------ écriture

    /** Sérialise Map / List / String / Number / Boolean / null. */
    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeTo(sb, value);
        return sb.toString();
    }

    private static void writeTo(StringBuilder sb, Object v) {
        switch (v) {
            case null -> sb.append("null");
            case String s -> escape(sb, s);
            case Boolean b -> sb.append(b);
            case Number n -> sb.append(finite(n));
            case Map<?, ?> m -> {
                sb.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    if (!first) sb.append(',');
                    first = false;
                    escape(sb, String.valueOf(e.getKey()));
                    sb.append(':');
                    writeTo(sb, e.getValue());
                }
                sb.append('}');
            }
            case Iterable<?> it -> {
                sb.append('[');
                boolean first = true;
                for (Object o : it) {
                    if (!first) sb.append(',');
                    first = false;
                    writeTo(sb, o);
                }
                sb.append(']');
            }
            default -> escape(sb, String.valueOf(v));
        }
    }

    /** JSON ne connaît ni NaN ni l'infini : on les rabat sur 0 plutôt que de produire
     *  un document que le navigateur refusera de lire. */
    private static String finite(Number n) {
        double d = n.doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d)) return "0";
        return n.toString();
    }

    private static void escape(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    // On échappe aussi U+2028/U+2029 : valides en JSON, mais ils coupent
                    // une ligne pour un moteur JavaScript, et le document est embarqué
                    // dans une balise <script>.
                    if (c < 0x20 || c == ' ' || c == ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    // ------------------------------------------------------------------ lecture

    /** Analyse un document JSON. Rend Map, List, String, Double, Boolean ou null. */
    public static Object read(String text) {
        Parser p = new Parser(text);
        p.skipWhitespace();
        Object v = p.value();
        p.skipWhitespace();
        return v;
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) { this.s = s; }

        void skipWhitespace() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        Object value() {
            skipWhitespace();
            if (i >= s.length()) throw new IllegalArgumentException("truncated JSON");
            char c = s.charAt(i);
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; // {
            skipWhitespace();
            if (i < s.length() && s.charAt(i) == '}') { i++; return m; }
            while (true) {
                skipWhitespace();
                String k = string();
                skipWhitespace();
                expect(':');
                m.put(k, value());
                skipWhitespace();
                if (i < s.length() && s.charAt(i) == ',') { i++; continue; }
                expect('}');
                return m;
            }
        }

        List<Object> array() {
            List<Object> l = new ArrayList<>();
            i++; // [
            skipWhitespace();
            if (i < s.length() && s.charAt(i) == ']') { i++; return l; }
            while (true) {
                l.add(value());
                skipWhitespace();
                if (i < s.length() && s.charAt(i) == ',') { i++; continue; }
                expect(']');
                return l;
            }
        }

        String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c != '\\') { sb.append(c); continue; }
                char e = s.charAt(i++);
                switch (e) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> { sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                    default -> sb.append(e);
                }
            }
            throw new IllegalArgumentException("unterminated JSON string");
        }

        Object number() {
            int start = i;
            while (i < s.length() && "-+.eE0123456789".indexOf(s.charAt(i)) >= 0) i++;
            return Double.valueOf(s.substring(start, i));
        }

        Object literal(String word, Object value) {
            if (!s.startsWith(word, i)) throw new IllegalArgumentException("unexpected JSON literal");
            i += word.length();
            return value;
        }

        void expect(char c) {
            if (i >= s.length() || s.charAt(i) != c) {
                throw new IllegalArgumentException("expected '" + c + "' at position " + i);
            }
            i++;
        }
    }
}
