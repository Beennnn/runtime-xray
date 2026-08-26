package lab.xray.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Le code source, indexé comme le rapport de couverture l'indexe : {@code paquet/Fichier.java}.
 * C'est cette convention commune qui permet de rapprocher une ligne de code et sa couverture.
 *
 * <p><b>La clé vient du paquet déclaré, pas de l'arborescence.</b> Indexer d'après le chemin
 * relatif à la racine passée oblige à désigner exactement le répertoire au-dessus du premier
 * paquet — {@code src/main/java} et non {@code src}, ni {@code src/main/java/coo}. Une racine
 * d'un cran trop haut ou trop bas donnait un index entier de clés décalées, donc aucune
 * correspondance, donc « Source indisponible » sur toutes les classes à la fois. Or le fichier
 * dit lui-même à quel paquet il appartient, et c'est cette déclaration qui fait autorité pour
 * JaCoCo comme pour le compilateur. La lire rend le niveau de la racine indifférent : on peut
 * pointer le projet entier, ou un répertoire de paquet, cela retombe sur ses pieds.
 *
 * <p>Le chemin relatif reste la clé de repli, pour le cas — rare, mais réel dans du code
 * ancien — d'un fichier sans déclaration de paquet.
 *
 * <p>L'index rapporte en outre <b>ce qu'il a vu</b> : chaque racine demandée, résolue en
 * absolu, existante ou non, et le nombre de fichiers qu'elle a fournis ; puis, par nom de
 * fichier, où chaque source a été trouvée. Sans cela, une source manquante ne se manifeste
 * que par un panneau vide, qui ne dit ni ce qu'on cherchait, ni où l'on a regardé.
 */
public final class Sources {

    /**
     * Au-delà, on cesse de parcourir. Une racine mal désignée — la racine d'un disque, un
     * répertoire personnel — se traverse sinon pendant des minutes, pour un index inutile.
     * Le fait d'avoir tronqué est dit, car il change la lecture du diagnostic.
     */
    private static final int MAX_FICHIERS = 20_000;

    /** {@code package org.exemple.module;} — la première ligne qui en a la forme fait foi. */
    private static final Pattern PAQUET =
            Pattern.compile("^\\s*package\\s+([\\p{L}_$][\\p{L}\\p{N}_$]*(?:\\s*\\.\\s*[\\p{L}_$][\\p{L}\\p{N}_$]*)*)\\s*;");

    private Sources() {}

    /**
     * Ce que l'index contient, et ce qu'il a fallu pour l'obtenir.
     *
     * @param parCle   {@code paquet/Fichier.java} → les lignes du fichier
     * @param racines  une entrée par racine demandée : ce qu'on en a tiré
     * @param parNom   {@code Fichier.java} → les endroits où ce nom a été trouvé
     * @param tronque  vrai si le parcours s'est arrêté sur la limite
     */
    public record Index(Map<String, Object> parCle, List<Object> racines,
                        Map<String, Object> parNom, boolean tronque) {

        public int fichiers() {
            return parCle.size();
        }

        /** Le diagnostic seul, sans le code : c'est lui qui voyage dans la page et le fichier. */
        public Map<String, Object> diagnostic() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("racines", racines);
            m.put("fichiers", fichiers());
            m.put("tronque", tronque);
            m.put("parNom", parNom);
            return m;
        }
    }

    /**
     * @param roots les racines demandées, dans l'ordre ; une racine inexistante est retenue
     *              dans le diagnostic plutôt qu'ignorée en silence
     */
    public static Index load(List<Path> roots) {
        Map<String, Object> parCle = new LinkedHashMap<>();
        Map<String, Object> parNom = new LinkedHashMap<>();
        List<Object> racines = new ArrayList<>();
        boolean tronque = false;

        for (Path root : roots) {
            Map<String, Object> vue = new LinkedHashMap<>();
            vue.put("demandee", root.toString());
            vue.put("absolue", absolu(root));
            boolean existe = Files.isDirectory(root);
            vue.put("existe", existe);
            vue.put("fichiers", 0);
            racines.add(vue);
            if (!existe) {
                vue.put("motif", Files.exists(root)
                        ? "ce chemin existe mais n'est pas un répertoire"
                        : "ce chemin n'existe pas");
                continue;
            }

            int avant = parCle.size();
            // Les liens sont suivis : une arborescence de sources montée ailleurs, ou un
            // répertoire lié, est un cas courant sur les postes de développement.
            try (Stream<Path> files = Files.walk(root, FileVisitOption.FOLLOW_LINKS)) {
                for (Path p : (Iterable<Path>) files.filter(Sources::estSource)::iterator) {
                    if (parCle.size() >= MAX_FICHIERS) {
                        tronque = true;
                        break;
                    }
                    indexer(root, p, parCle, parNom);
                }
            } catch (IOException e) {
                // Une racine illisible ne doit pas faire échouer tout le rapport : on la
                // retient telle quelle dans le diagnostic, et on passe à la suivante.
                vue.put("motif", "parcours interrompu : " + e.getMessage());
            }
            vue.put("fichiers", parCle.size() - avant);
        }
        return new Index(parCle, racines, parNom, tronque);
    }

    private static boolean estSource(Path p) {
        return p.getFileName() != null && p.getFileName().toString().endsWith(".java");
    }

    private static void indexer(Path root, Path fichier,
                                Map<String, Object> parCle, Map<String, Object> parNom) {
        List<String> lignes;
        try {
            lignes = lire(fichier);
        } catch (IOException e) {
            // Un fichier illisible ne doit pas faire échouer tout le rapport :
            // on le signale et on continue.
            System.err.println("   source ignorée : " + fichier + " (" + e.getMessage() + ")");
            return;
        }
        String nom = fichier.getFileName().toString();
        String paquet = paquetDeclare(lignes);
        String relatif = root.relativize(fichier).toString().replace('\\', '/');
        String cle = paquet == null ? relatif : paquet.replace('.', '/') + "/" + nom;

        parCle.put(cle, lignes);

        Map<String, Object> ou = new LinkedHashMap<>();
        ou.put("cle", cle);
        ou.put("chemin", absolu(fichier));
        ou.put("paquet", paquet);
        ou.put("racine", absolu(root));
        // Le même nom de fichier peut vivre dans plusieurs paquets : c'est précisément le
        // cas où il faut montrer les deux plutôt que d'en choisir un.
        @SuppressWarnings("unchecked")
        List<Object> endroits = (List<Object>) parNom.computeIfAbsent(nom, k -> new ArrayList<>());
        endroits.add(ou);
    }

    /**
     * Le paquet déclaré par ce fichier, ou {@code null} s'il n'en déclare aucun.
     *
     * <p>On s'arrête à la première ligne qui ouvre le type : au-delà, un {@code package}
     * ne serait plus une déclaration mais du texte — une chaîne, un commentaire.
     */
    static String paquetDeclare(List<String> lignes) {
        for (String ligne : lignes) {
            Matcher m = PAQUET.matcher(ligne);
            if (m.find()) return m.group(1).replaceAll("\\s+", "");
            if (ligne.matches("^\\s*(public\\s+|final\\s+|abstract\\s+|sealed\\s+|non-sealed\\s+)*"
                    + "(class|interface|enum|record)\\s+.*")) {
                return null;
            }
        }
        return null;
    }

    /**
     * UTF-8 d'abord, ISO-8859-1 en dernier recours.
     *
     * <p>Les sources d'un projet ancien sont souvent en ISO-8859-1 ou en cp1252, et depuis
     * Java 18 l'encodage par défaut de la plateforme est UTF-8 : réessayer avec lui ne
     * rattrape rien. ISO-8859-1, si — tout octet y a une image, donc la lecture n'échoue
     * jamais. Un accent isolé peut en ressortir de travers ; c'est très au-dessus de
     * l'alternative, qui était de ne pas afficher le fichier du tout.
     */
    private static List<String> lire(Path p) throws IOException {
        try {
            return Files.readAllLines(p, StandardCharsets.UTF_8);
        } catch (java.nio.charset.CharacterCodingException e) {
            return Files.readAllLines(p, StandardCharsets.ISO_8859_1);
        }
    }

    private static String absolu(Path p) {
        try {
            return p.toAbsolutePath().normalize().toString();
        } catch (RuntimeException e) {
            return p.toString();
        }
    }
}
