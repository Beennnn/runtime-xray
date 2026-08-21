package lab.sample.transfer;

import lab.sample.model.Leg;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consultation des horaires — la seule opération <b>bloquante</b> du programme.
 *
 * <p>Elle simule un appel à un service extérieur par un {@code Thread.sleep}. Sa
 * présence n'est pas décorative : elle crée du <em>temps d'attente</em> à côté du temps
 * de calcul, et c'est un excellent discriminant entre outils. Un profiler qui échantillonne
 * le CPU ne la verra pratiquement pas ; un profiler en temps réel (wall clock) la verra
 * dominer. Deux outils peuvent donc donner deux réponses opposées à « où passe le temps ? »
 * sur exactement la même exécution — et il faut savoir laquelle on regarde.
 *
 * <p>Le résultat est mis en cache : un horaire ne se demande qu'une fois par liaison.
 */
public final class Timetable {

    private static final Map<String, Integer> CACHE = new ConcurrentHashMap<>();
    private static final int LOOKUP_MILLIS = 3;

    private Timetable() {}

    /** @return la fréquence de passage, en minutes, pour cette liaison. */
    public static int frequencyMinutes(Leg leg) {
        return CACHE.computeIfAbsent(leg.from() + ">" + leg.to(), key -> {
            try {
                Thread.sleep(LOOKUP_MILLIS); // appel réseau simulé
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return 10 + (Math.abs(key.hashCode()) % 50);
        });
    }
}
