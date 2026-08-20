package lab.sample.speed;

import lab.sample.model.Mode;

/** Fabrique : associe un mode de transport à son modèle de vitesse. */
public final class Speeds {

    // Instances partagées : les modèles de vitesse sont sans état. Les allouer à chaque
    // appel captait ~25 % des échantillons — du bruit d'allocation qui masquait le calcul.
    private static final SpeedModel CAR = new CarSpeed();
    private static final SpeedModel TRAIN = new TrainSpeed();
    private static final SpeedModel BIKE = new BikeSpeed();
    private static final SpeedModel WALK = new WalkSpeed();

    private Speeds() {}

    public static SpeedModel forMode(Mode mode) {
        return switch (mode) {
            case CAR -> CAR;
            case TRAIN -> TRAIN;
            case BIKE -> BIKE;
            case WALK -> WALK;
            // Instancié ici et non partagé comme les autres : ainsi PlaneSpeed n'est
            // JAMAIS chargée ni exécutée, et reste à 0 % dans le rapport de couverture.
            // Un champ static final l'aurait fait construire au chargement de Speeds.
            case PLANE -> new PlaneSpeed();
        };
    }
}
