package lab.sample.model;

/** Comment on se déplace. C'est le principal <b>aiguilleur</b> du calcul : chaque mode
 *  active une famille de calculs différente (bouchons pour la voiture, correspondances
 *  pour le train, météo pour le vélo et la marche).
 *
 *  <p>{@code PLANE} n'est jamais choisi par les scénarios : la classe de vitesse
 *  correspondante doit rester à 0 % dans un rapport de couverture. */
public enum Mode { CAR, TRAIN, BIKE, WALK, PLANE }
