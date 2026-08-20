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
 * Lecture des relevés de l'inspecteur de valeurs.
 *
 * <p>Deux sorties distinctes :
 * <ul>
 *   <li><b>les valeurs</b> reçues par chaque méthode, appel par appel ;</li>
 *   <li><b>la trace</b> d'un appel : quel appel part de quelle ligne, et en combien de temps.</li>
 * </ul>
 */
public final class Inspection {

    /** Codes de couleur du terminal : la sortie est faite pour un écran, pas pour un fichier.
     *  Le caractère d'échappement s'écrit \\u001B et jamais en clair : un octet de
     *  contrôle invisible dans un source est une source de bugs muets. */
    private static final Pattern ANSI = Pattern.compile("\\u001B\\[[0-9;]*m");
    private static final Pattern METHOD = Pattern.compile("^method=([\\w.$]+)\\s");
    private static final Pattern TS = Pattern.compile("^ts=([\\d\\- :.]+);\\s*\\[cost=([^\\]]*)\\]");
    private static final Pattern ENTRY = Pattern.compile("^(\\s*)@(\\w+)\\[(.*?),?\\s*$");
    private static final Pattern CALL =
            Pattern.compile("[+`]---\\[([^\\]]*)\\]\\s+([\\w.$]+):(\\w+)\\(\\)\\s+#(\\d+)");
    private static final Pattern DURATION = Pattern.compile("([\\d.]+)ms");

    /**
     * Méthodes ajoutées par les outils ou par le compilateur, jamais écrites par personne.
     * Les capturer produit des lignes illisibles — un tableau de booléens pour la sonde de
     * couverture, par exemple — et pollue la liste des méthodes observées.
     */
    private static final Pattern SYNTHETIC =
            Pattern.compile("\\$jacocoInit|\\$jacocoData|^<clinit>$|^access\\$|lambda\\$");

    /** Valeurs par méthode : {@code paquet/Classe.methode -> [ {cost, params, retour} ]}. */
    public final Map<String, Object> values = new LinkedHashMap<>();
    /** Appels par ligne : {@code "43" -> [ {callee, cost, frame} ]}. */
    public final Map<String, Object> trace = new LinkedHashMap<>();

    public static Inspection read(Path valuesFile, Path traceFile, int limitPerMethod)
            throws IOException {
        Inspection in = new Inspection();
        if (Files.isRegularFile(valuesFile)) in.readValues(valuesFile, limitPerMethod);
        if (Files.isRegularFile(traceFile)) in.readTrace(traceFile);
        return in;
    }

    /**
     * La sortie a cette forme, et c'est l'<b>indentation</b> qui porte le sens :
     * <pre>
     * method=paquet.Classe.methode location=AtExit
     * ts=...; [cost=1.24ms] result=@ArrayList[
     *     @Object[][                    &lt;- le tableau des paramètres
     *         @Leg[Leg[from=Auch...]],  &lt;- un paramètre par ligne, indenté de 8
     *         @Mode[CAR],
     *     ],
     *     @Double[29.72],               &lt;- indenté de 4 : la valeur de RETOUR
     * ]
     * </pre>
     *
     * <p>Deux pièges, qui ont produit un affichage faux avant correction : les crochets sont
     * <b>imbriqués</b> — une expression régulière naïve tronque la valeur — et la ligne de
     * retour est <b>hors</b> du bloc des paramètres, donc ignorée par une capture qui
     * s'arrête à la première ligne non conforme. D'où l'analyse fondée sur l'indentation.
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
            Map<String, Object> retour = null;
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
                            retour = entry;
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
            if (calls.size() < limitPerMethod && (!params.isEmpty() || retour != null)) {
                Map<String, Object> call = new LinkedHashMap<>();
                call.put("ts", ts);
                call.put("cost", cost);
                call.put("params", params);
                call.put("retour", retour);
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
        // Retirer UN seul crochet fermant, celui de @Type[...]. En retirer davantage
        // tronquerait les valeurs imbriquées comme Leg[from=..., to=...].
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
     * Plusieurs invocations sont tracées, et elles empruntent des branches différentes :
     * c'est justement ce qui permet d'annoter davantage de lignes. En revanche, la même
     * ligne revient alors plusieurs fois — on <b>agrège</b> par appelé plutôt que d'empiler
     * des doublons, en conservant le nombre d'observations et l'étendue des durées.
     */
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
