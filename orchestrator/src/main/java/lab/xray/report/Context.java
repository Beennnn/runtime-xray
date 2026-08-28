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
 * Ce qu'il faut savoir pour répondre à une question, et rien de plus.
 *
 * <h2>Pourquoi ce n'est pas un client d'API</h2>
 *
 * <p>Faire parler un rapport à un modèle de langage ressemble à un problème d'intégration :
 * quelle API, quel format de requête, quelle bibliothèque. Ça n'en est pas un. Le format des
 * requêtes change tous les six mois et diffère d'un fournisseur à l'autre ; ce qui ne change
 * pas, c'est qu'il faut lui donner <b>un texte borné, exact, et qui se comprend seul</b>.
 *
 * <p>C'est ce que produit cette classe, et elle s'arrête là. Aucun appel réseau, aucun nom de
 * fournisseur, aucune clé. Le résultat se colle dans une fenêtre de discussion, se donne à un
 * agent qui lit l'espace de travail, ou se poste par une commande de trois lignes — et le
 * jour où le protocole à la mode change, seule cette commande de trois lignes est à refaire.
 *
 * <h2>Trois décisions</h2>
 *
 * <ul>
 *   <li><b>Le vocabulaire voyage avec les faits.</b> Un modèle qui n'a jamais vu ce format
 *       doit pouvoir le lire ; on lui joint donc sa légende, pas un lien vers elle.</li>
 *   <li><b>Ce qui n'a pas été mesuré passe avant ce qui l'a été.</b> Les indisponibilités
 *       sont en tête et ne sont <b>jamais</b> élaguées, même quand le budget déborde : un
 *       zéro qu'on prend pour une mesure produit une réponse fausse et assurée, ce qui est
 *       pire que pas de réponse du tout.</li>
 *   <li><b>Rien n'est coupé en silence.</b> Quand le budget force à écarter des faits, le
 *       paquet dit combien et lesquels. Un extrait qu'on croit complet fait conclure sur un
 *       échantillon.</li>
 * </ul>
 */
public final class Context {

    /**
     * Ce qu'on accepte de donner à lire, en caractères.
     *
     * <p>Assez pour une campagne ordinaire, assez peu pour tenir dans la fenêtre d'un modèle
     * modeste — ceux qu'on héberge soi-même le sont souvent. Environ dix mille jetons.
     */
    public static final int BUDGET = 40_000;

    /** Les faits qu'on ne retire jamais, quel que soit le budget. */
    private static final List<String> ALWAYS =
            List.of("campaign", "unavailable", "caveat");

    private Context() {}

    /** D'où viennent les familles retenues — ce que l'appelant doit pouvoir dire à l'opérateur. */
    public enum Origin {
        /** Nommées explicitement : aucune interprétation. */
        REQUESTED,
        /** Déduites des mots de la question. */
        KEYWORDS,
        /** Aucun mot reconnu : on a donné la vue d'ensemble. */
        OVERVIEW
    }

    /**
     * Un paquet, et de quoi dire à l'opérateur ce qui a été compris de sa question.
     *
     * <p>Le texte seul ne suffisait pas. Une question posée en toutes lettres a l'air d'être
     * comprise, alors qu'elle n'est que passée au tamis de quelques mots-clés : sans un
     * retour explicite, personne ne sait que « which classes never ran? » se
     * réduit au seul mot « jamais », ni qu'une question en anglais est tombée dans la vue
     * d'ensemble faute d'avoir été reconnue.
     */
    public record Pack(String text, List<String> families, Origin origin) {

        /** La ligne à écrire sur la sortie d'erreur, là où l'opérateur regarde. */
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

    /** Les familles qu'on peut nommer, dans l'ordre où le vocabulaire les présente. */
    public static List<String> knownFamilies() {
        return List.copyOf(Facts.VOCABULARY.keySet());
    }

    /**
     * Le paquet de contexte pour une question.
     *
     * @param question  ce qu'on cherche à savoir. Elle sert à deux choses, et c'est ce qui
     *                  la rend ambiguë : elle <b>choisit</b> les faits, grossièrement, par
     *                  mots-clés ; et elle <b>voyage</b>, recopiée telle quelle en tête du
     *                  paquet, parce que le vrai destinataire n'est pas ce programme.
     * @param demandees les familles nommées explicitement. Non vide, elles priment sur la
     *                  question : c'est le chemin des scripts, qui ont besoin d'un résultat
     *                  reproductible et non d'une phrase interprétée.
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

    /** Le paquet seul, pour qui n'a pas besoin de savoir comment il a été choisi. */
    public static String of(Path dir, String question, int budget) throws IOException {
        return of(dir, question, List.of(), budget).text();
    }

    /**
     * Un rapport au format 1.0 renvoie à la commande qui le régénère, plutôt qu'à un
     * migrateur.
     *
     * <p>Il n'y a pas d'outil de migration, et c'est un constat, pas un oubli :
     * {@code faits.jsonl} est <b>dérivé</b> des mesures de {@code runs/}, comme la page, le
     * diagnostic et les blocs. Un {@code --report-only} le réécrit entièrement dans le
     * format courant, avec au passage tous les correctifs accumulés depuis. Un migrateur
     * n'aurait fait que renommer des clés dans un fichier resté périmé par ailleurs, et il
     * aurait fallu le maintenir, puis penser à le supprimer.
     *
     * <p>Reste le cas d'une archive amputée de {@code runs/} — quelqu'un a zippé la page et
     * les faits, les mesures sont restées derrière. Là, rien ne peut être régénéré, et
     * c'est pourquoi ce message distingue les deux situations au lieu d'en supposer une.
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

    /** Le plus ancien format que ce lecteur comprend. */
    public static final String FORMAT_MINIMUM = "2.0";

    /**
     * Une famille inconnue s'arrête net, avec la liste de celles qui existent.
     *
     * <p>C'est l'inverse du texte libre, et délibérément : là, une phrase qu'on ne
     * reconnaît pas donne la vue d'ensemble plutôt que rien, parce qu'elle vient d'un
     * humain qui cherche. Ici elle vient d'un script, et un script qui demande une famille
     * qui n'existe pas a un défaut : le lui taire produirait un paquet silencieusement
     * différent de ce qu'il croit lire.
     */
    /**
     * Les noms de familles d'avant le format 2.0, acceptés à vie.
     *
     * <p>Un script de recette écrit contre la 1.0 ne doit pas s'arrêter parce que le format
     * a changé de langue. Ils ne sont pas documentés : ils marchent, c'est tout.
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
     * Un fait, et la ligne exacte qui le portait.
     *
     * <p>On garde le texte d'origine pour le recopier tel quel : relire puis réécrire un
     * JSON rendrait {@code 41} en {@code 41.0}, faute pour le format de distinguer un entier
     * d'un flottant. Ce n'est pas faux, c'est le genre de détail qui fait douter du reste.
     * Les champs analysés ne servent qu'à choisir.
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
     * Les familles de faits qui répondent à cette question.
     *
     * <p>Choix par mots-clés, et c'est délibérément grossier : la sélection ne doit jamais
     * être la partie intelligente. Elle réduit le volume ; c'est au modèle de raisonner. Une
     * sélection trop fine écarterait le fait qui contredit l'hypothèse — exactement celui
     * qu'il fallait garder.
     */
    /**
     * Les mots documentés, en anglais : ce que {@code --help} énumère, et ce sur quoi un
     * lecteur peut compter. Un test garde qu'ils y sont tous écrits.
     */
    static final Map<String, String[]> WORDS = new LinkedHashMap<>();

    /**
     * Les mêmes familles en français. Reconnus, <b>délibérément non documentés</b> : l'outil
     * parle anglais, et une seconde table dans l'aide la rendrait illisible pour ceux à qui
     * elle s'adresse. L'aide se contente de dire qu'ils existent.
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
        // « code » n'y figure pas : « dead code » et « code coverage » sont des tournures
        // trop courantes pour qu'un mot aussi général désigne la famille des sources.
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
                // La couverture d'une exécution et celle d'une classe répondent à la même
                // question, posée à deux échelles : on ne les sépare pas.
                if (e.getKey().equals("coverage.run")) families.add("class");
                if (e.getKey().equals("source.missing")) families.add("source.hint");
            }
        }
        return families;
    }

    /**
     * La question, mise à plat : minuscules, accents retirés.
     *
     * <p>Sans cela {@code coût} et {@code cout} seraient deux mots à écrire tous les deux,
     * et {@code exécution} ne reconnaîtrait pas {@code execution}. On en écrit un seul,
     * sans accent, et la question s'y ramène.
     */
    static String normalise(String question) {
        return java.text.Normalizer.normalize(question.toLowerCase(Locale.ROOT),
                        java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
    }

    /** La vue d'ensemble : ce qu'on donne quand la question n'a rien déclenché. */
    static final List<String> OVERVIEW = List.of("run", "coverage.run",
            "class.never_executed", "method.hot", "source.hint");

    /**
     * Les familles pour une question, la vue d'ensemble à défaut.
     *
     * <p>Une question qu'on n'a pas su classer n'est pas une question sans réponse : on
     * donne de quoi répondre plutôt qu'un paquet vide. Mais on le <b>dit</b> — voir
     * {@link Origine#VUE_ENSEMBLE} — faute de quoi le repli passerait pour une lecture
     * réussie de la question.
     */
    static List<String> familiesFor(String question) {
        List<String> families = recognisedFamilies(question);
        return families.isEmpty() ? OVERVIEW : families;
    }

    /** Vrai si au moins un mot de la question a été reconnu. */
    static boolean recognised(String question) {
        return !recognisedFamilies(question).isEmpty();
    }

    /**
     * Vrai si l'un des mots ouvre un mot de la question.
     *
     * <p>La correspondance est un <b>début de mot</b>, et non n'importe où dans la chaîne.
     * Chercher n'importe où paraissait plus généreux et se retournait contre nous :
     * {@code cout} déclenchait sur « é<b>cout</b>er », et l'ajout de mots anglais aurait
     * empiré les choses — {@code hot} vit dans « screens<b>hot</b> », {@code rate} dans
     * « gene<b>rate</b> ». Un début de mot garde en revanche les flexions, qui sont tout
     * l'intérêt : {@code manqu} attrape « manque », « manquant », « manquantes ».
     *
     * <p>Un mot-clé qui contient une espace est cherché tel quel : « not covered » n'est
     * pas un mot, c'est une tournure.
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

        // D'abord ce qui n'a PAS été mesuré. C'est l'ordre qui compte : un lecteur qui lit
        // les chiffres avant les réserves les a déjà interprétés quand il arrive aux réserves.
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

        // Un bloc vide se lit exactement comme un bloc qu'on n'a pas su remplir — c'est le
        // mode d'échec le plus coûteux de cet outil, et il vaut ici comme dans la page. Un
        // modèle qui ne voit rien conclut soit « il n'y en a pas », soit « l'outil a raté » :
        // deux réponses opposées, et rien pour trancher. On tranche donc à sa place.
        if (kept == 0 && droppedCount == 0) {
            sb.append("\n**No fact of these families in this campaign.** This is not an "
                    + "extraction failure: the other families do carry facts. Depending on the "
                    + "family, it reads either \"there are none\" (no dead class, for instance) "
                    + "or \"the matching measurement was not taken\" — the section above says "
                    + "which. Families present in the file: ")
              .append(String.join(", ", present(facts))).append(".\n");
        }

        // Jamais de troncature muette : un extrait qu'on croit complet fait conclure sur un
        // échantillon, et personne ne saura que la conclusion portait sur une partie.
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

    /** Les familles réellement présentes, pour distinguer « rien » de « rien trouvé ». */
    private static List<String> present(List<Fact> facts) {
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        for (Fact f : facts) names.add(f.name());
        return new ArrayList<>(names);
    }

    /**
     * Un compte entier s'écrit sans décimale.
     *
     * <p>Le JSON relu ne distingue pas 29 de 29,0 : tout nombre revient en {@code double}.
     * « 29.0 exécutions » n'est pas faux, c'est pire — c'est le genre de détail qui fait
     * douter du reste, chez un lecteur humain comme chez un modèle.
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
