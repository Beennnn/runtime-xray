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
public final class Contexte {

    /**
     * Ce qu'on accepte de donner à lire, en caractères.
     *
     * <p>Assez pour une campagne ordinaire, assez peu pour tenir dans la fenêtre d'un modèle
     * modeste — ceux qu'on héberge soi-même le sont souvent. Environ dix mille jetons.
     */
    public static final int BUDGET = 40_000;

    /** Les faits qu'on ne retire jamais, quel que soit le budget. */
    private static final List<String> TOUJOURS =
            List.of("campagne", "indisponibilite", "reserve");

    private Contexte() {}

    /** D'où viennent les familles retenues — ce que l'appelant doit pouvoir dire à l'opérateur. */
    public enum Origine {
        /** Nommées explicitement : aucune interprétation. */
        DEMANDEES,
        /** Déduites des mots de la question. */
        MOTS_CLES,
        /** Aucun mot reconnu : on a donné la vue d'ensemble. */
        VUE_ENSEMBLE
    }

    /**
     * Un paquet, et de quoi dire à l'opérateur ce qui a été compris de sa question.
     *
     * <p>Le texte seul ne suffisait pas. Une question posée en toutes lettres a l'air d'être
     * comprise, alors qu'elle n'est que passée au tamis de quelques mots-clés : sans un
     * retour explicite, personne ne sait que « quelles classes n'ont jamais tourné ? » se
     * réduit au seul mot « jamais », ni qu'une question en anglais est tombée dans la vue
     * d'ensemble faute d'avoir été reconnue.
     */
    public record Paquet(String texte, List<String> familles, Origine origine) {

        /** La ligne à écrire sur la sortie d'erreur, là où l'opérateur regarde. */
        public String annonce() {
            String liste = String.join(", ", familles);
            return switch (origine) {
                case DEMANDEES -> "   familles demandées : " + liste;
                case MOTS_CLES -> "   familles retenues d'après la question : " + liste;
                case VUE_ENSEMBLE -> "   aucun mot-clé reconnu dans la question — "
                        + "vue d'ensemble : " + liste;
            };
        }
    }

    /** Les familles qu'on peut nommer, dans l'ordre où le vocabulaire les présente. */
    public static List<String> famillesConnues() {
        return List.copyOf(Faits.VOCABULAIRE.keySet());
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
    public static Paquet pour(Path dossier, String question, List<String> demandees, int budget)
            throws IOException {
        Path fichier = dossier.resolve(Faits.FICHIER);
        if (!Files.exists(fichier)) {
            throw new IOException("pas de " + Faits.FICHIER + " sous " + dossier
                    + " — assembler d'abord le rapport (--report-only)");
        }
        String q = question == null ? "" : question;
        List<String> familles;
        Origine origine;
        if (demandees != null && !demandees.isEmpty()) {
            familles = valider(demandees);
            origine = Origine.DEMANDEES;
        } else {
            familles = famillesPour(q);
            origine = reconnue(q) ? Origine.MOTS_CLES : Origine.VUE_ENSEMBLE;
        }
        return new Paquet(rendre(lire(fichier), q, familles, budget), familles, origine);
    }

    /** Le paquet seul, pour qui n'a pas besoin de savoir comment il a été choisi. */
    public static String pour(Path dossier, String question, int budget) throws IOException {
        return pour(dossier, question, List.of(), budget).texte();
    }

    /**
     * Une famille inconnue s'arrête net, avec la liste de celles qui existent.
     *
     * <p>C'est l'inverse du texte libre, et délibérément : là, une phrase qu'on ne
     * reconnaît pas donne la vue d'ensemble plutôt que rien, parce qu'elle vient d'un
     * humain qui cherche. Ici elle vient d'un script, et un script qui demande une famille
     * qui n'existe pas a un défaut : le lui taire produirait un paquet silencieusement
     * différent de ce qu'il croit lire.
     */
    static List<String> valider(List<String> demandees) throws IOException {
        List<String> connues = famillesConnues();
        List<String> out = new ArrayList<>();
        for (String d : demandees) {
            String nom = d.trim();
            if (nom.isEmpty()) continue;
            if (!connues.contains(nom)) {
                throw new IOException("famille inconnue : « " + nom + " » — les familles sont "
                        + String.join(", ", connues));
            }
            if (!out.contains(nom)) out.add(nom);
        }
        if (out.isEmpty()) throw new IOException("--familles ne nomme aucune famille");
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
    record Fait(String ligne, Map<String, Object> champs) {
        Object get(String cle) {
            return champs.get(cle);
        }

        String nom() {
            return String.valueOf(champs.get("fait"));
        }
    }

    @SuppressWarnings("unchecked")
    static List<Fait> lire(Path fichier) throws IOException {
        List<Fait> out = new ArrayList<>();
        for (String ligne : Files.readAllLines(fichier, StandardCharsets.UTF_8)) {
            if (ligne.isBlank()) continue;
            Object lu = Json.read(ligne);
            if (lu instanceof Map<?, ?> m) out.add(new Fait(ligne, (Map<String, Object>) m));
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
    static List<String> famillesReconnues(String question) {
        String q = question.toLowerCase(Locale.ROOT);
        List<String> familles = new ArrayList<>();
        if (contient(q, "jamais", "mort", "morte", "inutilis", "non couvert", "pas couvert",
                "dead", "unused")) {
            familles.add("classe.jamais_executee");
        }
        if (contient(q, "couvert", "couverture", "coverage", "taux", "pourcent")) {
            familles.add("couverture.execution");
            familles.add("classe");
        }
        if (contient(q, "temps", "lent", "lente", "coût", "cout", "chaud", "perf", "rapide",
                "profil")) {
            familles.add("methode.chaude");
        }
        if (contient(q, "source", "introuvable", "manque", "manquant", "racine", "code")) {
            familles.add("source.introuvable");
            familles.add("piste.source");
        }
        if (contient(q, "exécution", "execution", "campagne", "quand", "machine", "commande")) {
            familles.add("execution");
        }
        return familles;
    }

    /** La vue d'ensemble : ce qu'on donne quand la question n'a rien déclenché. */
    static final List<String> VUE_ENSEMBLE = List.of("execution", "couverture.execution",
            "classe.jamais_executee", "methode.chaude", "piste.source");

    /**
     * Les familles pour une question, la vue d'ensemble à défaut.
     *
     * <p>Une question qu'on n'a pas su classer n'est pas une question sans réponse : on
     * donne de quoi répondre plutôt qu'un paquet vide. Mais on le <b>dit</b> — voir
     * {@link Origine#VUE_ENSEMBLE} — faute de quoi le repli passerait pour une lecture
     * réussie de la question.
     */
    static List<String> famillesPour(String question) {
        List<String> familles = famillesReconnues(question);
        return familles.isEmpty() ? VUE_ENSEMBLE : familles;
    }

    /** Vrai si au moins un mot de la question a été reconnu. */
    static boolean reconnue(String question) {
        return !famillesReconnues(question).isEmpty();
    }

    private static boolean contient(String q, String... mots) {
        for (String m : mots) if (q.contains(m)) return true;
        return false;
    }

    static String rendre(List<Fait> faits, String question, int budget) {
        return rendre(faits, question, famillesPour(question), budget);
    }

    static String rendre(List<Fait> faits, String question, List<String> familles, int budget) {
        StringBuilder sb = new StringBuilder();

        Fait tete = premier(faits, "campagne");
        sb.append(enTete(tete, question));

        // D'abord ce qui n'a PAS été mesuré. C'est l'ordre qui compte : un lecteur qui lit
        // les chiffres avant les réserves les a déjà interprétés quand il arrive aux réserves.
        List<Fait> absences = tous(faits, "indisponibilite");
        List<Fait> reserves = tous(faits, "reserve");
        if (!absences.isEmpty() || !reserves.isEmpty()) {
            sb.append("\n## Ce qui N'A PAS été mesuré — à lire avant tout chiffre\n\n");
            for (Fait f : absences) {
                sb.append("- **").append(f.get("quoi")).append("** (exécution ")
                  .append(f.get("execution")).append(") : ").append(f.get("pourquoi"))
                  .append("\n  Conséquence : ").append(f.get("consequence")).append("\n");
            }
            for (Fait f : reserves) {
                sb.append("- Réserve sur ").append(f.get("quoi")).append(" : ")
                  .append(f.get("reserve")).append("\n");
            }
        } else {
            sb.append("\n## Ce qui N'A PAS été mesuré\n\nRien : les trois observateurs ont "
                    + "tous produit leurs données sur toutes les exécutions.\n");
        }

        sb.append("\n## Les faits retenus pour cette question\n\n")
          .append("Un objet JSON par ligne. Familles retenues : ")
          .append(String.join(", ", familles)).append("\n\n```jsonl\n");

        int ecartes = 0;
        int retenus = 0;
        Map<String, Integer> ecartesPar = new LinkedHashMap<>();
        for (Fait f : faits) {
            String nom = f.nom();
            if (TOUJOURS.contains(nom) || !familles.contains(nom)) continue;
            if (sb.length() + f.ligne().length() > budget) {
                ecartes++;
                ecartesPar.merge(nom, 1, Integer::sum);
                continue;
            }
            retenus++;
            sb.append(f.ligne()).append("\n");
        }
        sb.append("```\n");

        // Un bloc vide se lit exactement comme un bloc qu'on n'a pas su remplir — c'est le
        // mode d'échec le plus coûteux de cet outil, et il vaut ici comme dans la page. Un
        // modèle qui ne voit rien conclut soit « il n'y en a pas », soit « l'outil a raté » :
        // deux réponses opposées, et rien pour trancher. On tranche donc à sa place.
        if (retenus == 0 && ecartes == 0) {
            sb.append("\n**Aucun fait de ces familles dans cette campagne.** Ce n'est pas une "
                    + "défaillance de l'extraction : les autres familles, elles, portent des "
                    + "faits. Selon la famille, cela se lit « il n'y en a pas » (aucune classe "
                    + "morte, par exemple) ou « la mesure correspondante n'a pas été prise » — "
                    + "la section précédente le dit. Familles présentes dans le fichier : ")
              .append(String.join(", ", presentes(faits))).append(".\n");
        }

        // Jamais de troncature muette : un extrait qu'on croit complet fait conclure sur un
        // échantillon, et personne ne saura que la conclusion portait sur une partie.
        if (ecartes > 0) {
            sb.append("\n⚠️ **").append(ecartes).append(" fait(s) écarté(s)** faute de place (")
              .append(ecartesPar).append("). Cette sélection est INCOMPLÈTE : ne pas en tirer "
              + "de total ni de classement. Le fichier entier est dans `")
              .append(Faits.FICHIER).append("`.\n");
        }
        return sb.toString();
    }

    private static String enTete(Fait campagne, String question) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Analyse d'exécution — contexte\n\n");
        if (!question.isBlank()) sb.append("Question posée : ").append(question).append("\n\n");
        sb.append("Ces données viennent de l'observation d'une application Java **pendant "
                + "qu'elle tournait** : couverture (JaCoCo), temps (échantillonnage de "
                + "piles), valeurs de paramètres (Arthas).\n\n");

        sb.append("## Trois pièges de lecture\n\n"
                + "1. Un chiffre à **zéro** ne veut pas dire « n'a pas tourné » tant qu'on "
                + "n'a pas lu la section suivante : il peut vouloir dire « n'a pas été "
                + "mesuré ».\n"
                + "2. Une **source introuvable** signifie que la racine de sources n'était "
                + "pas configurée — jamais que le code n'existe pas.\n"
                + "3. Un **taux de couverture** dit par où le code est passé. Il ne dit rien "
                + "de sa justesse, ni de la qualité des tests.\n");

        if (campagne != null) {
            sb.append("\n## La campagne\n\n");
            for (String cle : List.of("outil", "version", "date", "executions")) {
                if (campagne.get(cle) != null) {
                    sb.append("- ").append(cle).append(" : ")
                      .append(nombre(campagne.get(cle))).append("\n");
                }
            }
            if (campagne.get("vocabulaire") instanceof Map<?, ?> vocabulaire) {
                sb.append("\n## Le vocabulaire des faits\n\n");
                for (Map.Entry<?, ?> e : vocabulaire.entrySet()) {
                    sb.append("- `").append(e.getKey()).append("` : ").append(e.getValue())
                      .append("\n");
                }
            }
        }
        return sb.toString();
    }

    /** Les familles réellement présentes, pour distinguer « rien » de « rien trouvé ». */
    private static List<String> presentes(List<Fait> faits) {
        java.util.Set<String> noms = new java.util.LinkedHashSet<>();
        for (Fait f : faits) noms.add(f.nom());
        return new ArrayList<>(noms);
    }

    /**
     * Un compte entier s'écrit sans décimale.
     *
     * <p>Le JSON relu ne distingue pas 29 de 29,0 : tout nombre revient en {@code double}.
     * « 29.0 exécutions » n'est pas faux, c'est pire — c'est le genre de détail qui fait
     * douter du reste, chez un lecteur humain comme chez un modèle.
     */
    static String nombre(Object valeur) {
        if (valeur instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())
                && Math.abs(n.doubleValue()) < 1e15) {
            return String.valueOf((long) n.doubleValue());
        }
        return String.valueOf(valeur);
    }

    private static Fait premier(List<Fait> faits, String nom) {
        for (Fait f : faits) if (nom.equals(f.nom())) return f;
        return null;
    }

    private static List<Fait> tous(List<Fait> faits, String nom) {
        List<Fait> out = new ArrayList<>();
        for (Fait f : faits) if (nom.equals(f.nom())) out.add(f);
        return out;
    }
}
