package lab.xray.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reading and writing.
 *
 * <p>Why not take a library: the tool must be droppable onto a machine and launched, with
 * nothing to resolve. A single dependency would be enough to break that promise in a
 * disconnected environment. The format we handle fits in four types — object, array,
 * string, number — and warrants no more.
 */
public final class Json {

    private Json() {}

    /**
     * A map that keeps the order it was written in, for anything that ends up in a file.
     *
     * <p>{@code Map.of} does not: its iteration order is deliberately randomised, and the
     * seed changes at every JVM start. Inside one run it looks perfectly stable — which is
     * how a {@code Map.of} reached {@code faits.jsonl} and made the file come out with its
     * keys in a different order at each generation, for identical content. The existing
     * guard could not see it either: it assembles twice <b>in the same JVM</b>, where that
     * order does not move.
     *
     * <p>So the rule is: anything serialised by {@link #write} is built with this, not with
     * {@code Map.of}. Two generations of the same measurements must give the same bytes —
     * otherwise a diff between two reports says "everything changed" and stops being read.
     */
    public static Map<String, Object> ordered(Object... keysAndValues) {
        if (keysAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("a key without its value");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            m.put(String.valueOf(keysAndValues[i]), keysAndValues[i + 1]);
        }
        return m;
    }

    // ------------------------------------------------------------------ writing

    /** Serialises Map / List / String / Number / Boolean / null. */
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

    /** JSON knows neither NaN nor infinity: they are folded to 0 rather than producing
     *  a document the browser will refuse to read. */
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
                    // U+2028/U+2029 are escaped too: valid in JSON, but they break a line
                    // as far as a JavaScript engine is concerned, and the document is
                    // embedded in a <script> tag.
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

    /** Parses a JSON document. Returns Map, List, String, Double, Boolean or null. */
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
