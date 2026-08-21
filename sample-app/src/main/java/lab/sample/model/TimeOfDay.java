package lab.sample.model;

/** Moment de la journée. Seule {@code RUSH_HOUR} déclenche le calcul de bouchons,
 *  et seulement en voiture : c'est l'exemple le plus simple d'une fonction appelée
 *  ou non selon le contexte. */
public enum TimeOfDay { QUIET, RUSH_HOUR, NIGHT }
