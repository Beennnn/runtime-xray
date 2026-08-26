package lab.xray;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * Ce qui se passe pendant qu'on attend, dit en une ligne qui se réécrit.
 *
 * <p>Une analyse dure ce que dure l'application observée — dix secondes ou vingt minutes,
 * et rien à l'écran ne le disait jusqu'ici : entre « Attente de la fin de l'exécution » et
 * le rapport, l'outil se taisait. Une attente muette se lit comme un blocage, et la seule
 * façon de savoir était de tuer le processus, ce qui perd la capture.
 *
 * <p>Chaque carré vaut un intervalle de temps, et sa densité l'activité de la JVM observée
 * pendant cet intervalle. La bande défile : on voit d'un coup d'œil si le programme
 * travaille encore, s'il attend une saisie, ou s'il s'est endormi sur une socket.
 *
 * <p><b>Ce que cet affichage ne fait pas</b>, et c'est ce qui l'autorise : il ne mesure
 * rien. La charge vient du temps processeur que le système comptabilise de toute façon
 * pour le processus observé ({@link ProcessHandle.Info#totalCpuDuration()}), et la
 * progression de la sortie, de la taille d'un fichier déjà écrit. Aucune option n'est
 * ajoutée à la JVM observée, aucun agent n'est sollicité, rien n'est échantillonné en plus
 * — donc rien de ce que le rapport montre ne change selon que cette bande s'affiche ou non.
 *
 * <p>Elle ne s'affiche que devant un terminal. Dans un tuyau, un journal d'intégration ou
 * une redirection, une ligne qui se réécrit toutes les secondes donne des milliers de
 * lignes illisibles : là, l'objet est muet et ne coûte même pas la lecture des compteurs.
 *
 * <p>Les caractères choisis — {@code · ░ ▒ ▓ █} — existent dans CP850 comme en UTF-8 :
 * un terminal Windows mal réglé les affiche encore (voir CLAUDE.md, « Terminal Windows »).
 * La couleur ne fait que redoubler la densité, jamais la porter seule ; {@code NO_COLOR}
 * la retire.
 */
public final class Progression {

    /**
     * Du plus calme au plus occupé. La densité porte l'information, pas la couleur.
     *
     * <p>Le point d'inactivité sert aussi de séparateur ailleurs dans l'outil : la ligne
     * en emploie donc un autre, faute de quoi compter les carrés — ce que fait un test —
     * en trouverait un de trop.
     */
    static final char[] PALIERS = {'·', '░', '▒', '▓', '█'};

    private static final String[] TEINTES = {
            "\u001b[90m", "\u001b[36m", "\u001b[32m", "\u001b[33m", "\u001b[31m"};
    private static final String NEUTRE = "\u001b[0m";

    /** Combien d'intervalles restent visibles. Au-delà, la bande défile. */
    static final int LARGEUR = 28;

    private final Appendable sortie;
    private final boolean active;
    private final boolean couleur;

    private final Deque<Integer> bande = new ArrayDeque<>();
    private Duration ecoulePrecedent = Duration.ZERO;
    private Duration cpuPrecedent = Duration.ZERO;
    private long octetsPrecedent = -1;
    private boolean cpuConnu;
    private int longueurPrecedente;

    Progression(Appendable sortie, boolean active, boolean couleur) {
        this.sortie = sortie;
        this.active = active;
        this.couleur = couleur;
    }

    /**
     * L'affichage pour un terminal, ou le silence.
     *
     * <p>{@code System.console()} répond au vrai terminal, et non au flux : {@code Main}
     * remplace {@code System.out} par un flux UTF-8, ce qui ne change pas la réponse.
     */
    public static Progression pour(Appendable sortie) {
        boolean terminal = System.console() != null;
        return new Progression(sortie, terminal, terminal && System.getenv("NO_COLOR") == null);
    }

    /** Un objet qui n'écrit jamais rien — pour les appelants qui n'ont pas de terminal. */
    public static Progression muette() {
        return new Progression(new StringBuilder(), false, false);
    }

    /** Vrai si quelque chose s'affiche : inutile de relever des compteurs sinon. */
    public boolean active() {
        return active;
    }

    /**
     * Un intervalle de plus.
     *
     * @param ecoule     depuis le lancement de l'application observée
     * @param cpuCumule  temps processeur consommé par elle et ses descendants, cumulé
     * @param octets     taille de sa sortie standard telle qu'écrite sur le disque
     */
    public void avancement(Duration ecoule, Duration cpuCumule, long octets) {
        if (!active) return;
        bande.addLast(palier(charge(ecoule, cpuCumule, octets)));
        while (bande.size() > LARGEUR) bande.removeFirst();
        ecoulePrecedent = ecoule;
        cpuPrecedent = cpuCumule;
        octetsPrecedent = octets;
        ecrire(ligne(ecoule, cpuCumule, octets));
    }

    /** Rend la ligne à ce qui suit : la bande ne doit pas rester à moitié écrite. */
    public void fin() {
        if (!active || longueurPrecedente == 0) return;
        ecrire("");
        try {
            sortie.append(System.lineSeparator());
        } catch (IOException ignored) {
            // Un affichage de confort ne fait pas échouer une analyse réussie.
        }
    }

    /**
     * Combien de cœurs la JVM observée a occupés en moyenne pendant l'intervalle.
     *
     * <p>Le compte est en <b>cœurs</b> et non en pourcentage de la machine : sur un serveur
     * à trente-deux cœurs, une application qui en sature un travaille à plein régime, et la
     * ramener à 3 % la ferait passer pour endormie. La question posée devant une attente
     * est « est-ce que ça avance ? », pas « est-ce que la machine est pleine ? ».
     *
     * <p>Quand le système ne publie pas le temps processeur — cela arrive selon la
     * plateforme et les droits — la sortie de l'application sert de témoin : elle ne dit
     * pas <i>combien</i> le programme travaille, mais elle dit qu'il vit encore, ce qui
     * est justement la question.
     */
    private double charge(Duration ecoule, Duration cpuCumule, long octets) {
        if (!cpuCumule.isZero()) cpuConnu = true;
        long intervalle = ecoule.toMillis() - ecoulePrecedent.toMillis();
        if (intervalle <= 0) return 0;
        if (cpuConnu) {
            long cpu = Math.max(0, cpuCumule.toMillis() - cpuPrecedent.toMillis());
            return cpu / (double) intervalle;
        }
        if (octetsPrecedent < 0) return 0;
        return octets > octetsPrecedent ? 0.3 : 0;
    }

    /**
     * Le carré qui va avec une charge, en cœurs occupés. Les seuils sont visuels, pas
     * normatifs : ils séparent « rien », « un peu », « un cœur », « plusieurs ».
     */
    static int palier(double coeurs) {
        if (coeurs <= 0.01) return 0;
        if (coeurs < 0.15) return 1;
        if (coeurs < 0.50) return 2;
        if (coeurs < 1.50) return 3;
        return 4;
    }

    String ligne(Duration ecoule, Duration cpuCumule, long octets) {
        StringBuilder sb = new StringBuilder("   ").append(horloge(ecoule)).append("  ");
        for (int niveau : bande) {
            if (couleur) sb.append(TEINTES[niveau]);
            sb.append(PALIERS[niveau]);
        }
        if (couleur) sb.append(NEUTRE);
        for (int i = bande.size(); i < LARGEUR; i++) sb.append(' ');
        sb.append("  ");
        if (cpuConnu) {
            sb.append("cpu ")
              .append(String.format(Locale.FRENCH, "%.1f", cpuCumule.toMillis() / 1000.0))
              .append(" s");
        } else {
            sb.append("en cours");
        }
        return sb.append(" | sortie ").append(taille(octets)).toString();
    }

    static String horloge(Duration ecoule) {
        long s = Math.max(0, ecoule.toSeconds());
        return String.format(Locale.FRENCH, "%02d:%02d", s / 60, s % 60);
    }

    static String taille(long octets) {
        if (octets < 1024) return octets + " o";
        if (octets < 1024 * 1024) {
            return String.format(Locale.FRENCH, "%.1f Ko", octets / 1024.0);
        }
        return String.format(Locale.FRENCH, "%.1f Mo", octets / (1024.0 * 1024));
    }

    /**
     * Réécrit la ligne en place.
     *
     * <p>On complète avec des espaces jusqu'à la longueur précédente : une ligne plus
     * courte que celle qu'elle remplace en laisserait la queue à l'écran — « 12,4 s »
     * effaçant « 112,4 s » donnerait « 12,4 ss ».
     */
    private void ecrire(String ligne) {
        int visible = couleur ? sansCodes(ligne) : ligne.length();
        StringBuilder sb = new StringBuilder("\r").append(ligne);
        for (int i = visible; i < longueurPrecedente; i++) sb.append(' ');
        longueurPrecedente = visible;
        try {
            sortie.append(sb);
            if (sortie instanceof java.io.Flushable f) f.flush();
        } catch (IOException ignored) {
            // Idem : perdre la bande est sans conséquence sur la capture.
        }
    }

    private static int sansCodes(String ligne) {
        return ligne.replaceAll("\u001b\\[[0-9;]*m", "").length();
    }
}
