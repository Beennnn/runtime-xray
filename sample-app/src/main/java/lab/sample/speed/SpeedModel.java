package lab.sample.speed;

import lab.sample.model.Leg;

/** Vitesse moyenne d'un mode de transport sur une étape.
 *
 *  <p>Point de <b>dispatch virtuel</b> du programme : l'appel part toujours de
 *  l'interface, mais c'est l'implémentation concrète qui travaille. Un bon arbre
 *  d'appel doit nommer l'implémentation réellement exécutée ; certains outils
 *  s'arrêtent à l'interface, et c'est une différence qui se voit tout de suite. */
public interface SpeedModel {
    double kmPerHour(Leg leg);
}
