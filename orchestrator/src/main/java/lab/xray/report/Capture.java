package lab.xray.report;

import java.util.Map;

/**
 * Le contrat entre ce qu'une exécution <b>enregistre</b> et ce que l'outil sait <b>relire</b>.
 *
 * <p>Une campagne de recette coûte cher : lancer l'application, jouer dix scénarios, attendre.
 * Ce qu'elle produit — les {@code .exec} de JaCoCo, les piles d'async-profiler, les relevés
 * d'Arthas, le contexte du lancement — n'a aucune raison de vieillir avec l'outil qui les
 * relit. Rejouer une campagne parce que la page a changé de mise en forme serait absurde.
 *
 * <p>D'où ce numéro. La capture porte le sien ; l'outil déclare la <b>version minimale</b>
 * qu'il sait relire. Tant que la capture est au-dessus, une mise à jour de l'outil se contente
 * de réassembler la vue — {@code --report-only} — sans rien relancer. En dessous, l'outil dit
 * précisément ce qui manque, et si une nouvelle mesure est réellement nécessaire.
 *
 * <h2>Ce que le numéro engage</h2>
 *
 * <p>Il porte sur la <b>forme de ce qui est écrit sur le disque</b>, pas sur ce que la page en
 * fait. Concrètement : les chemins ({@code jacoco/jacoco.exec}, {@code jacoco/html/jacoco.xml},
 * {@code async-profiler/profil.collapsed}, {@code arthas/watch-params.txt},
 * {@code arthas/trace-calltree.txt}), et les champs de {@code run-context.json}.
 *
 * <ul>
 *   <li><b>Majeure</b> — un champ ou un fichier <i>disparaît</i>, ou change de sens. Les
 *       captures précédentes ne se relisent plus ; il faut remesurer.</li>
 *   <li><b>Mineure</b> — un champ ou un fichier <i>s'ajoute</i>. Les captures précédentes se
 *       relisent, en moins fourni : la page dit ce qui manque plutôt que de faire semblant.</li>
 * </ul>
 *
 * <p>Une capture sans numéro est réputée {@link #ORIGINE} : c'est la forme d'avant que ce
 * contrat n'existe, et elle reste lisible. Le silence d'hier ne doit pas coûter une campagne.
 */
public final class Capture {

    /** Le champ où la version vit, dans {@code run-context.json}. */
    public static final String CHAMP = "formatCapture";

    /** La forme d'avant le contrat : tout ce qui n'annonce rien en relève. */
    public static final String ORIGINE = "1.0";

    /** Ce qu'une exécution lancée aujourd'hui inscrit dans son contexte. */
    public static final String COURANTE = "1.1";

    /**
     * La plus ancienne que cet outil sait relire.
     *
     * <p>Elle ne monte que lorsqu'une capture ancienne devient réellement illisible — jamais
     * pour un confort de code. La monter oblige tous les utilisateurs à remesurer.
     */
    public static final String MINIMALE = "1.0";

    private Capture() {}

    /** La version annoncée par une capture, ou {@link #ORIGINE} si elle n'annonce rien. */
    public static String versionDe(Map<String, Object> contexte) {
        if (contexte == null) return ORIGINE;
        Object v = contexte.get(CHAMP);
        return v == null || String.valueOf(v).isBlank() ? ORIGINE : String.valueOf(v);
    }

    /** Vrai si cet outil sait relire cette capture sans qu'on relance quoi que ce soit. */
    public static boolean lisible(String version) {
        return compare(version, MINIMALE) >= 0;
    }

    /**
     * Vrai si la capture est plus récente que l'outil.
     *
     * <p>Ce n'est pas une erreur : une capture faite par une version plus avancée porte au
     * pire des champs qu'on ignore. On le dit, on continue — refuser de lire ferait perdre
     * une campagne pour une différence qui ne gêne pas.
     */
    public static boolean plusRecenteQueLOutil(String version) {
        return compare(version, COURANTE) > 0;
    }

    /** Ce qu'on affiche quand une capture ne se relit pas — et ce qu'il faut en faire. */
    public static String pourquoiIllisible(String version) {
        return "capture in format " + version + ", whereas this tool only reads from "
                + MINIMALE + " on. This run must be measured again; the others, not.";
    }

    /** Comparaison numérique par segments : « 1.10 » est postérieur à « 1.9 ». */
    static int compare(String a, String b) {
        String[] ga = a.split("\\."), gb = b.split("\\.");
        for (int i = 0; i < Math.max(ga.length, gb.length); i++) {
            int va = i < ga.length ? entier(ga[i]) : 0;
            int vb = i < gb.length ? entier(gb[i]) : 0;
            if (va != vb) return Integer.compare(va, vb);
        }
        return 0;
    }

    private static int entier(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
