package lab.xray.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reading the value inspector's records.
 *
 * <p>Two distinct outputs:
 * <ul>
 *   <li><b>the values</b> received by each method, call by call;</li>
 *   <li><b>the trace</b> of one call: which call leaves which line, and in how long.</li>
 * </ul>
 */
public final class Inspection {

    /** Terminal colour codes: the output is made for a screen, not for a file. The escape
     *  character is written \\u001B and never literally: an invisible control byte in a
     *  source file is a source of silent bugs. */
    private static final Pattern ANSI = Pattern.compile("\\u001B\\[[0-9;]*m");
    private static final Pattern METHOD = Pattern.compile("^method=([\\w.$]+)\\s");
    private static final Pattern TS = Pattern.compile("^ts=([\\d\\- :.]+);\\s*\\[cost=([^\\]]*)\\]");
    private static final Pattern ENTRY = Pattern.compile("^(\\s*)@(\\w+)\\[(.*?),?\\s*$");
    private static final Pattern CALL =
            Pattern.compile("[+`]---\\[([^\\]]*)\\]\\s+([\\w.$]+):(\\w+)\\(\\)\\s+#(\\d+)");
    private static final Pattern DURATION = Pattern.compile("([\\d.]+)ms");

    /**
     * Methods added by the tools or by the compiler, never written by anyone. Capturing
     * them produces unreadable lines — an array of booleans for the coverage probe, for
     * instance — and pollutes the list of observed methods.
     */
    private static final Pattern SYNTHETIC =
            Pattern.compile("\\$jacocoInit|\\$jacocoData|^<clinit>$|^access\\$|lambda\\$");

    /** Values per method: {@code package/Class.method -> [ {cost, params, retour} ]}. */
    public final Map<String, Object> values = new LinkedHashMap<>();
    /** Calls per line: {@code "43" -> [ {callee, cost, frame} ]}. */
    public final Map<String, Object> trace = new LinkedHashMap<>();

    public static Inspection read(Path valuesFile, Path traceFile, int limitPerMethod)
            throws IOException {
        Inspection in = new Inspection();
        if (Files.isRegularFile(valuesFile)) in.readValues(valuesFile, limitPerMethod);
        if (Files.isRegularFile(traceFile)) in.readTrace(traceFile);
        return in;
    }

    /**
     * The output has this shape, and it is the <b>indentation</b> that carries the meaning:
     * <pre>
     * method=package.Class.method location=AtExit
     * ts=...; [cost=1.24ms] result=@ArrayList[
     *     @Object[][                    &lt;- the array of arguments
     *         @Leg[Leg[from=Auch...]],  &lt;- one argument per line, indented by 8
     *         @Mode[CAR],
     *     ],
     *     @Double[29.72],               &lt;- indented by 4: the RETURN value
     * ]
     * </pre>
     *
     * <p>Two traps, which produced a wrong display before they were fixed: the brackets are
     * <b>nested</b> — a naive regular expression truncates the value — and the return line
     * is <b>outside</b> the block of arguments, so it is ignored by a capture that stops at
     * the first non-conforming line. Hence the analysis based on indentation.
     */
    private void readValues(Path file, int limitPerMethod) throws IOException {
        List<String> lines = clean(file);
        int i = 0;
        while (i < lines.size()) {
            Matcher m = METHOD.matcher(lines.get(i));
            if (!m.find()) {
                i++;
                continue;
            }

            String fqmn = m.group(1);
            if (SYNTHETIC.matcher(fqmn).find()) {
                i++;
                continue;
            }
            int lastDot = fqmn.lastIndexOf('.');
            String frame = fqmn.substring(0, lastDot).replace('.', '/')
                    + "." + fqmn.substring(lastDot + 1);
            i++;

            String cost = "";
            String ts = "";
            if (i < lines.size()) {
                Matcher t = TS.matcher(lines.get(i));
                if (t.find()) {
                    ts = t.group(1).trim();
                    cost = t.group(2);
                    i++;
                }
            }

            List<Object> params = new ArrayList<>();
            Map<String, Object> returnValue = null;
            while (i < lines.size() && !lines.get(i).startsWith("method=")) {
                String line = lines.get(i);
                String trimmed = line.trim();
                int indent = line.length() - line.stripLeading().length();
                if (trimmed.startsWith("@")) {
                    Map<String, Object> entry = entry(line);
                    if (entry != null) {
                        if (indent >= 8) {
                            params.add(entry);
                        } else if (indent == 4 && !line.contains("@Object[][")) {
                            returnValue = entry;
                        }
                    }
                } else if (indent == 0 && (trimmed.equals("]") || trimmed.equals("],"))) {
                    i++;
                    break;
                }
                i++;
            }

            @SuppressWarnings("unchecked")
            List<Object> calls = (List<Object>) values.computeIfAbsent(frame, k -> new ArrayList<>());
            if (calls.size() < limitPerMethod && (!params.isEmpty() || returnValue != null)) {
                Map<String, Object> call = new LinkedHashMap<>();
                call.put("ts", ts);
                call.put("cost", cost);
                call.put("params", params);
                call.put("retour", returnValue);
                calls.add(call);
            }
        }
    }

    private static Map<String, Object> entry(String line) {
        Matcher m = ENTRY.matcher(line);
        if (!m.find()) {
            return null;
        }
        String value = m.group(3).stripTrailing();
        if (value.endsWith(",")) {
            value = value.substring(0, value.length() - 1).stripTrailing();
        }
        // Remove ONE closing bracket, the one of @Type[...]. Removing more would
        // truncate nested values such as Leg[from=..., to=...].
        if (value.endsWith("]")) {
            value = value.substring(0, value.length() - 1);
        }
        value = value.trim();
        if (value.isEmpty()) {
            return null;
        }
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("type", m.group(2));
        e.put("value", value);
        return e;
    }

    /**
     * Several invocations are traced, and they take different branches: that is precisely
     * what makes it possible to annotate more lines. On the other hand, the same line then
     * comes back several times — so we <b>aggregate</b> per callee rather than piling up
     * duplicates, keeping the number of observations and the range of durations.
     *
     * <p>The aggregate is not enough, however, when the line is <b>inside a loop</b>:
     * saying "called 8 times, between 0.01 and 0.03 ms" hides that the first iteration cost
     * ten times the following ones, which is often the whole story (lazy loading, cold
     * cache, just-in-time compilation). Each pass is therefore kept <b>in order</b>, on top
     * of the aggregate, so that the view can make one entry per iteration out of it.
     */
    /** Beyond that, the list of iterations stops informing and starts weighing. */
    private static final int MAX_ITERATIONS = 60;

    private void readTrace(Path file) throws IOException {
        Map<String, Map<String, Map<String, Object>>> byLine = new LinkedHashMap<>();
        for (String line : clean(file)) {
            Matcher m = CALL.matcher(line);
            if (!m.find()) {
                continue;
            }
            String meta = m.group(1);
            String cls = m.group(2);
            String method = m.group(3);
            String nr = m.group(4);
            String frame = cls.replace('.', '/') + "." + method;

            Map<String, Map<String, Object>> forLine =
                    byLine.computeIfAbsent(nr, k -> new LinkedHashMap<>());
            Map<String, Object> call = forLine.computeIfAbsent(frame, k -> {
                Map<String, Object> fresh = new LinkedHashMap<>();
                fresh.put("callee", cls.substring(cls.lastIndexOf('.') + 1) + "." + method + "()");
                fresh.put("frame", frame);
                fresh.put("n", 0);
                fresh.put("passages", new ArrayList<Object>());
                return fresh;
            });
            call.put("n", ((Number) call.get("n")).intValue() + 1);

            Matcher d = DURATION.matcher(meta.replace(',', '.'));
            if (d.find()) {
                double ms = Double.parseDouble(d.group(1));
                double min = call.containsKey("minMs") ? (Double) call.get("minMs") : ms;
                double max = call.containsKey("maxMs") ? (Double) call.get("maxMs") : ms;
                call.put("minMs", Math.min(min, ms));
                call.put("maxMs", Math.max(max, ms));
                @SuppressWarnings("unchecked")
                List<Object> iterations = (List<Object>) call.get("passages");
                // Bounded: a loop of several thousand turns would produce an unreadable
                // page and an oversized data file. The first passes are the ones that
                // carry the information — that is where the start-up cost shows.
                if (iterations.size() < MAX_ITERATIONS) {
                    iterations.add(ms);
                }
            }
        }
        for (Map.Entry<String, Map<String, Map<String, Object>>> e : byLine.entrySet()) {
            trace.put(e.getKey(), new ArrayList<Object>(e.getValue().values()));
        }
    }

    private static List<String> clean(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        return List.of(ANSI.matcher(text).replaceAll("").split("\n", -1));
    }
}
