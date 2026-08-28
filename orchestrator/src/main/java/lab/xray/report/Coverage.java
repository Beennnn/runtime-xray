package lab.xray.report;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reading the coverage report (JaCoCo XML).
 *
 * <p>This is the authoritative source on "what code ran": it counts everything, where the
 * time measurements sample and miss the short methods.
 */
public final class Coverage {

    /** Per source file, then per line number: the state and the branch counts. */
    public final Map<String, Map<String, Object>> lines = new LinkedHashMap<>();
    /** Per class: its source file and its methods (name, line, instructions covered). */
    public final Map<String, Object> methods = new LinkedHashMap<>();
    /** Per package: its classes, with instructions covered and missed. */
    public final Map<String, Object> packages = new LinkedHashMap<>();

    public static Coverage parse(Path xml) throws Exception {
        return parse(xml, PackageFilter.NONE);
    }

    /**
     * @param hidden packages not to list — see {@link PackageFilter}. The filtering happens
     *               here, on reading, and not on display: a hidden class must weigh neither
     *               on the totals, nor on the lists, nor on the focused report.
     */
    public static Coverage parse(Path xml, PackageFilter hidden) throws Exception {
        Coverage c = new Coverage();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // The report declares an external DTD: loading it would go out on the network,
        // and brings nothing here.
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xml.toFile());

        NodeList pkgs = doc.getElementsByTagName("package");
        for (int i = 0; i < pkgs.getLength(); i++) {
            Element pkg = (Element) pkgs.item(i);
            String pkgName = pkg.getAttribute("name");
            if (hidden.hidden(pkgName + "/")) continue;
            List<Object> classes = new ArrayList<>();

            for (Element cls : children(pkg, "class")) {
                String clsName = cls.getAttribute("name");
                String source = pkgName + "/" + cls.getAttribute("sourcefilename");

                List<Object> methodList = new ArrayList<>();
                for (Element m : children(cls, "method")) {
                    String lineAttr = m.getAttribute("line");
                    if (lineAttr.isEmpty()) continue;
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("name", m.getAttribute("name"));
                    entry.put("line", Integer.parseInt(lineAttr));
                    entry.put("covered", counter(m, "INSTRUCTION", "covered"));
                    methodList.add(entry);
                }
                methodList.sort((a, b) -> Integer.compare(
                        (Integer) ((Map<?, ?>) a).get("line"),
                        (Integer) ((Map<?, ?>) b).get("line")));

                Map<String, Object> byClass = new LinkedHashMap<>();
                byClass.put("source", source);
                byClass.put("methods", methodList);
                c.methods.put(clsName, byClass);

                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("name", clsName);
                summary.put("simple", clsName.substring(clsName.lastIndexOf('/') + 1));
                summary.put("source", source);
                summary.put("covered", counter(cls, "INSTRUCTION", "covered"));
                summary.put("missed", counter(cls, "INSTRUCTION", "missed"));
                classes.add(summary);
            }
            classes.sort((a, b) -> String.valueOf(((Map<?, ?>) a).get("simple"))
                    .compareTo(String.valueOf(((Map<?, ?>) b).get("simple"))));
            c.packages.put(pkgName, classes);

            for (Element sf : children(pkg, "sourcefile")) {
                Map<String, Object> perLine = new LinkedHashMap<>();
                for (Element ln : children(sf, "line")) {
                    int ci = intAttr(ln, "ci"), mi = intAttr(ln, "mi");
                    int cb = intAttr(ln, "cb"), mb = intAttr(ln, "mb");
                    String state = (ci == 0 && mi > 0) ? "miss" : (mb > 0 ? "part" : "full");
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("s", state);
                    info.put("b", (cb + mb) > 0 ? List.of(cb, cb + mb) : null);
                    perLine.put(ln.getAttribute("nr"), info);
                }
                c.lines.put(pkgName + "/" + sf.getAttribute("name"), perLine);
            }
        }
        return c;
    }

    private static List<Element> children(Element parent, String tag) {
        List<Element> out = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element e && e.getTagName().equals(tag)) out.add(e);
        }
        return out;
    }

    private static int counter(Element parent, String type, String attr) {
        for (Element c : children(parent, "counter")) {
            if (type.equals(c.getAttribute("type"))) return intAttr(c, attr);
        }
        return 0;
    }

    private static int intAttr(Element e, String name) {
        String v = e.getAttribute(name);
        return v.isEmpty() ? 0 : Integer.parseInt(v);
    }
}
