package lab.xray.report;

import lab.xray.json.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What one needs in order to answer a question, and nothing more.
 *
 * <h2>Why this is not an API client</h2>
 *
 * <p>Getting a report to speak to a language model looks like an integration problem: which
 * API, which request format, which library. It is not one. The request format changes every
 * six months and differs from one provider to the next; what does not change is that the
 * model must be given <b>a bounded, exact text that stands on its own</b>.
 *
 * <p>That is what this class produces, and it stops there. No network call, no provider
 * name, no key. The result is pasted into a chat window, handed to an agent that reads the
 * workspace, or posted by a three-line command — and the day the fashionable protocol
 * changes, only that three-line command has to be redone.
 *
 * <h2>Three decisions</h2>
 *
 * <ul>
 *   <li><b>The vocabulary travels with the facts.</b> A model that has never seen this
 *       format must be able to read it; so its legend is enclosed, not a link to it.</li>
 *   <li><b>What was not measured comes before what was.</b> The unavailabilities are at the
 *       top and are <b>never</b> pruned, even when the budget overflows: a zero taken for a
 *       measurement produces a confident wrong answer, which is worse than no answer at
 *       all.</li>
 *   <li><b>Nothing is cut in silence.</b> When the budget forces facts to be dropped, the
 *       pack says how many and which. An extract believed to be complete makes people
 *       conclude from a sample.</li>
 * </ul>
 */
public final class Context {

    /**
     * What one accepts to hand over to be read, in characters.
     *
     * <p>Enough for an ordinary campaign, little enough to fit in the window of a modest
     * model — the ones hosted in-house often are. About ten thousand tokens.
     */
    public static final int BUDGET = 40_000;

    /** The facts that are never removed, whatever the budget. */
    private static final List<String> ALWAYS =
            List.of("campaign", "unavailable", "caveat");

    private Context() {}

    /** Where the families kept came from — what the caller must be able to tell the operator. */
    public enum Origin {
        /** Named explicitly: no interpretation at all. */
        REQUESTED,
        /** Deduced from the words of the question. */
        KEYWORDS,
        /** No word recognised: the overview was given. */
        OVERVIEW
    }

    /**
     * A pack, and enough to tell the operator what was understood of their question.
     *
     * <p>The text alone was not enough. A question asked in full sentences looks as though
     * it was understood, when it has only been passed through a sieve of a few keywords:
     * without an explicit answer back, nobody knows that "which classes never ran?" comes
     * down to the single word "never", nor that a question fell into the overview for want
     * of being recognised.
     */
    public record Pack(String text, List<String> families, Origin origin) {

        /** The line to write on standard error, where the operator is looking. */
        public String announcement() {
            String list = String.join(", ", families);
            return switch (origin) {
                case REQUESTED -> "   families requested: " + list;
                case KEYWORDS -> "   families kept from the question: " + list;
                case OVERVIEW -> "   no keyword recognised in the question — "
                        + "overview: " + list;
            };
        }
    }

    /** The families one can name, in the order the vocabulary presents them. */
    public static List<String> knownFamilies() {
        return List.copyOf(Facts.VOCABULARY.keySet());
    }

    /**
     * The context pack for one question.
     *
     * @param question  what one is trying to find out. It serves two purposes, and that is
     *                  what makes it ambiguous: it <b>chooses</b> the facts, coarsely, by
     *                  keywords; and it <b>travels</b>, copied as it is at the head of the
     *                  pack, because the real addressee is not this program.
     * @param requested the families named explicitly. When not empty they take priority
     *                  over the question: this is the path scripts take, and they need a
     *                  reproducible result, not an interpreted sentence.
     */
    public static Pack of(Path dir, String question, List<String> requested, int budget)
            throws IOException {
        Path file = dir.resolve(Facts.FILE);
        if (!Files.exists(file)) {
            throw new IOException("no " + Facts.FILE + " under " + dir
                    + " — build the report first (--report-only)");
        }
        String q = question == null ? "" : question;
        List<Fact> facts = read(file);
        refuseFormatOne(facts, dir);
        List<String> families;
        Origin origin;
        if (requested != null && !requested.isEmpty()) {
            families = validate(requested);
            origin = Origin.REQUESTED;
        } else {
            families = familiesFor(q);
            origin = recognised(q) ? Origin.KEYWORDS : Origin.OVERVIEW;
        }
        return new Pack(render(facts, q, families, budget), families, origin);
    }

    /** The pack alone, for those who do not need to know how it was chosen. */
    public static String of(Path dir, String question, int budget) throws IOException {
        return of(dir, question, List.of(), budget).text();
    }

    /**
     * A report in format 1.0 is pointed at the command that regenerates it, rather than at
     * a migrator.
     *
     * <p>There is no migration tool, and that is an observation, not an oversight:
     * {@code faits.jsonl} is <b>derived</b> from the measurements in {@code runs/}, like the
     * page, the diagnostic and the blocks. A {@code --report-only} rewrites it entirely in
     * the current format, picking up along the way every fix accumulated since. A migrator
     * would only have renamed keys in a file that stayed out of date in every other
     * respect, and it would have had to be maintained, then remembered and deleted.
     *
     * <p>There remains the case of an archive with {@code runs/} cut off — somebody zipped
     * the page and the facts, the measurements stayed behind. There, nothing can be
     * regenerated, and that is why this message tells the two situations apart instead of
     * assuming one.
     */
    static void refuseFormatOne(List<Fact> facts, Path dir) throws IOException {
        for (Fact f : facts) {
            if (f.fields().containsKey("fait") && !f.fields().containsKey("fact")) {
                boolean regenerable = Files.isDirectory(dir.resolve("runs"));
                throw new IOException("this report is in facts format 1.0, this tool reads "
                        + FORMAT_MINIMUM + " and above"
                        + (regenerable
                           ? "\n   Rebuild it: runtime-xray --report-only --out " + dir
                           : "\n   Its runs/ directory is gone, so nothing can be rebuilt."
                             + " Re-run the campaign, or read faits.jsonl by hand — its"
                             + " first line still carries its own vocabulary."));
            }
        }
    }

    /** The oldest format this reader understands. */
    public static final String FORMAT_MINIMUM = "2.0";

    /**
     * An unknown family stops dead, with the list of the ones that exist.
     *
     * <p>This is the opposite of the free-text path, and deliberately so: there, a sentence
     * that is not recognised gives the overview rather than nothing, because it comes from
     * a human who is looking. Here it comes from a script, and a script asking for a family
     * that does not exist has a defect: keeping quiet about it would produce a pack
     * silently different from what it believes it is reading.
     */
    /**
     * The family names from before format 2.0, accepted for life.
     *
     * <p>An acceptance script written against 1.0 must not stop because the format changed
     * language. They are not documented: they work, that is all.
     */
    static final Map<String, String> FAMILIES_1_0 = Map.of(
            "campagne", "campaign",
            "execution", "run",
            "indisponibilite", "unavailable",
            "reserve", "caveat",
            "couverture.execution", "coverage.run",
            "classe", "class",
            "classe.jamais_executee", "class.never_executed",
            "methode.chaude", "method.hot",
            "source.introuvable", "source.missing",
            "piste.source", "source.hint");

    static List<String> validate(List<String> requested) throws IOException {
        List<String> known = knownFamilies();
        List<String> out = new ArrayList<>();
        for (String d : requested) {
            String name = FAMILIES_1_0.getOrDefault(d.trim(), d.trim());
            if (name.isEmpty()) continue;
            if (!known.contains(name)) {
                throw new IOException("unknown fact family: \"" + name + "\" — the families are "
                        + String.join(", ", known));
            }
            if (!out.contains(name)) out.add(name);
        }
        if (out.isEmpty()) throw new IOException("--familles names no family");
        return out;
    }

    /**
     * One fact, and the exact line that carried it.
     *
     * <p>The original text is kept so it can be copied as it is: reading a JSON back and
     * rewriting it would turn {@code 41} into {@code 41.0}, the format having no way to
     * tell an integer from a float. That is not wrong, it is the kind of detail that makes
     * one doubt the rest. The parsed fields only serve to choose.
     */
    record Fact(String line, Map<String, Object> fields) {
        Object get(String key) {
            return fields.get(key);
        }

        String name() {
            return String.valueOf(fields.get("fact"));
        }
    }

    @SuppressWarnings("unchecked")
    static List<Fact> read(Path file) throws IOException {
        List<Fact> out = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            Object read = Json.read(line);
            if (read instanceof Map<?, ?> m) out.add(new Fact(line, (Map<String, Object>) m));
        }
        return out;
    }

    /**
     * The fact families that answer this question.
     *
     * <p>Chosen by keywords, and deliberately coarse: the selection must never be the
     * clever part. It reduces the volume; the reasoning is the model's job. Too fine a
     * selection would discard the fact that contradicts the hypothesis — exactly the one
     * that had to be kept.
     */
    /**
     * The documented words, in English: what {@code --help} lists, and what a reader can
     * rely on. A test guards that every one of them is written there.
     */
    static final Map<String, String[]> WORDS = new LinkedHashMap<>();

    /**
     * The same families in French. Recognised, <b>deliberately undocumented</b>: the tool
     * speaks English, and a second table in the help would make it unreadable for the very
     * people it addresses. The help merely says that they exist.
     */
    static final Map<String, String[]> WORDS_FR = new LinkedHashMap<>();

    static {
        WORDS.put("class.never_executed",
                new String[]{"never", "dead", "unused", "uncovered", "not covered"});
        WORDS.put("coverage.run", new String[]{"cover", "coverage", "percent"});
        WORDS.put("method.hot",
                new String[]{"time", "slow", "hot", "cost", "perf", "fast", "profil"});
        WORDS.put("source.missing", new String[]{"source", "missing", "root"});
        WORDS.put("run",
                new String[]{"run", "campaign", "when", "machine", "command"});

        WORDS_FR.put("class.never_executed",
                new String[]{"jamais", "mort", "inutilis", "non couvert", "pas couvert"});
        WORDS_FR.put("coverage.run",
                new String[]{"couvert", "couverture", "taux", "pourcent"});
        WORDS_FR.put("method.hot",
                new String[]{"temps", "lent", "cout", "chaud", "rapide"});
        // "code" is not there: "dead code" and "code coverage" are far too common turns
        // of phrase for so general a word to name the sources family.
        WORDS_FR.put("source.missing",
                new String[]{"introuvable", "manqu", "racine"});
        WORDS_FR.put("run",
                new String[]{"execution", "campagne", "quand", "commande"});
    }

    static List<String> recognisedFamilies(String question) {
        String q = normalise(question);
        List<String> families = new ArrayList<>();
        for (Map.Entry<String, String[]> e : WORDS.entrySet()) {
            if (contains(q, e.getValue()) || contains(q, WORDS_FR.get(e.getKey()))) {
                families.add(e.getKey());
                // A run's coverage and a class's answer the same question, asked at two
                // scales: they are not separated.
                if (e.getKey().equals("coverage.run")) families.add("class");
                if (e.getKey().equals("source.missing")) families.add("source.hint");
            }
        }
        return families;
    }

    /**
     * The question, flattened: lower case, accents removed.
     *
     * <p>Without this, {@code coût} and {@code cout} would be two words both of which would
     * have to be written down, and {@code exécution} would not recognise {@code execution}.
     * Only one is written, without accents, and the question is brought down to it.
     */
    static String normalise(String question) {
        return java.text.Normalizer.normalize(question.toLowerCase(Locale.ROOT),
                        java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
    }

    /** The overview: what is given when the question triggered nothing. */
    static final List<String> OVERVIEW = List.of("run", "coverage.run",
            "class.never_executed", "method.hot", "source.hint");

    /**
     * The families for a question, the overview failing that.
     *
     * <p>A question we could not classify is not a question without an answer: we give
     * enough to answer with rather than an empty pack. But we <b>say so</b> — see
     * {@link Origin#OVERVIEW} — without which the fallback would pass for a successful
     * reading of the question.
     */
    static List<String> familiesFor(String question) {
        List<String> families = recognisedFamilies(question);
        return families.isEmpty() ? OVERVIEW : families;
    }

    /** True when at least one word of the question was recognised. */
    static boolean recognised(String question) {
        return !recognisedFamilies(question).isEmpty();
    }

    /**
     * True when one of the words opens a word of the question.
     *
     * <p>The match is on a <b>word start</b>, and not anywhere in the string. Searching
     * anywhere looked more generous and turned against us: {@code cout} fired on
     * "é<b>cout</b>er", and adding English words would have made things worse — {@code hot}
     * lives inside "screens<b>hot</b>", {@code rate} inside "gene<b>rate</b>". A word start,
     * on the other hand, keeps the inflections, which are the whole point: {@code manqu}
     * catches "manque", "manquant", "manquantes".
     *
     * <p>A keyword containing a space is searched for as it is: "not covered" is not a
     * word, it is a turn of phrase.
     */
    private static boolean contains(String q, String... words) {
        for (String m : words) {
            if (m.indexOf(' ') >= 0) {
                if (q.contains(m)) return true;
            } else {
                for (String word : q.split("[^\\p{L}\\p{N}]+")) {
                    if (word.startsWith(m)) return true;
                }
            }
        }
        return false;
    }

    static String render(List<Fact> facts, String question, int budget) {
        return render(facts, question, familiesFor(question), budget);
    }

    static String render(List<Fact> facts, String question, List<String> families, int budget) {
        StringBuilder sb = new StringBuilder();

        Fact head = first(facts, "campaign");
        sb.append(header(head, question));

        // First what was NOT measured. The order is what matters: a reader who reads the
        // figures before the caveats has already interpreted them by the time they reach
        // the caveats.
        List<Fact> unavailabilities = tous(facts, "unavailable");
        List<Fact> caveats = tous(facts, "caveat");
        if (!unavailabilities.isEmpty() || !caveats.isEmpty()) {
            sb.append("\n## What was NOT measured — read this before any figure\n\n");
            for (Fact f : unavailabilities) {
                sb.append("- **").append(f.get("what")).append("** (run ")
                  .append(f.get("run")).append("): ").append(f.get("why"))
                  .append("\n  Consequence: ").append(f.get("consequence")).append("\n");
            }
            for (Fact f : caveats) {
                sb.append("- Caveat on ").append(f.get("what")).append(": ")
                  .append(f.get("caveat")).append("\n");
            }
        } else {
            sb.append("\n## What was NOT measured\n\nNothing: all three observers produced "
                    + "their data on every run.\n");
        }

        sb.append("\n## The facts kept for this question\n\n")
          .append("One JSON object per line. Families kept: ")
          .append(String.join(", ", families)).append("\n\n```jsonl\n");

        int droppedCount = 0;
        int kept = 0;
        Map<String, Integer> droppedPerFamily = new LinkedHashMap<>();
        for (Fact f : facts) {
            String name = f.name();
            if (ALWAYS.contains(name) || !families.contains(name)) continue;
            if (sb.length() + f.line().length() > budget) {
                droppedCount++;
                droppedPerFamily.merge(name, 1, Integer::sum);
                continue;
            }
            kept++;
            sb.append(f.line()).append("\n");
        }
        sb.append("```\n");

        // An empty block reads exactly like a block we failed to fill — that is this
        // tool's costliest failure mode, and it holds here as it does in the page. A model
        // that sees nothing concludes either "there are none" or "the tool failed": two
        // opposite answers, and nothing to decide between them. So we decide for it.
        if (kept == 0 && droppedCount == 0) {
            sb.append("\n**No fact of these families in this campaign.** This is not an "
                    + "extraction failure: the other families do carry facts. Depending on the "
                    + "family, it reads either \"there are none\" (no dead class, for instance) "
                    + "or \"the matching measurement was not taken\" — the section above says "
                    + "which. Families present in the file: ")
              .append(String.join(", ", present(facts))).append(".\n");
        }

        // Never a silent truncation: an extract believed to be complete makes people
        // conclude from a sample, and nobody will know the conclusion covered only part.
        if (droppedCount > 0) {
            sb.append("\n⚠️ **").append(droppedCount).append(" fact(s) left out** for lack of room (")
              .append(droppedPerFamily).append("). This selection is INCOMPLETE: do not draw a total "
              + "or a ranking from it. The whole file is in `")
              .append(Facts.FILE).append("`.\n");
        }
        return sb.toString();
    }

    private static String header(Fact campaign, String question) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Runtime analysis — context\n\n");
        if (!question.isBlank()) sb.append("Question asked: ").append(question).append("\n\n");
        sb.append("This data comes from observing a Java application **while it was "
                + "running**: coverage (JaCoCo), time (stack sampling), argument values "
                + "(Arthas).\n\n");

        sb.append("## Three reading traps\n\n"
                + "1. A figure at **zero** does not mean \"never ran\" until the next section "
                + "has been read: it may mean \"was not measured\".\n"
                + "2. A **missing source** means the source root was not configured — never "
                + "that the code does not exist.\n"
                + "3. A **coverage rate** says where the code went. It says nothing about "
                + "whether it is correct, nor about the quality of the tests.\n");

        if (campaign != null) {
            sb.append("\n## The campaign\n\n");
            for (String key : List.of("tool", "version", "date", "runs")) {
                if (campaign.get(key) != null) {
                    sb.append("- ").append(key).append(" : ")
                      .append(number(campaign.get(key))).append("\n");
                }
            }
            if (campaign.get("vocabulary") instanceof Map<?, ?> vocabulary) {
                sb.append("\n## The vocabulary of the facts\n\n");
                for (Map.Entry<?, ?> e : vocabulary.entrySet()) {
                    sb.append("- `").append(e.getKey()).append("` : ").append(e.getValue())
                      .append("\n");
                }
            }
        }
        return sb.toString();
    }

    /** The families actually present, to tell "none" from "none found". */
    private static List<String> present(List<Fact> facts) {
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        for (Fact f : facts) names.add(f.name());
        return new ArrayList<>(names);
    }

    /**
     * A whole count is written without a decimal.
     *
     * <p>JSON read back does not tell 29 from 29.0: every number comes back as a
     * {@code double}. "29.0 runs" is not wrong, it is worse — it is the kind of detail that
     * makes one doubt the rest, in a human reader as much as in a model.
     */
    static String number(Object value) {
        if (value instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())
                && Math.abs(n.doubleValue()) < 1e15) {
            return String.valueOf((long) n.doubleValue());
        }
        return String.valueOf(value);
    }

    private static Fact first(List<Fact> facts, String name) {
        for (Fact f : facts) if (name.equals(f.name())) return f;
        return null;
    }

    private static List<Fact> tous(List<Fact> facts, String name) {
        List<Fact> out = new ArrayList<>();
        for (Fact f : facts) if (name.equals(f.name())) out.add(f);
        return out;
    }
}
