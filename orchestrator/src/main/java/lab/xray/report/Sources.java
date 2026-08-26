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

    // ================================================================= chercher une racine

    /**
     * Ce qu'on accepte de traverser en cherchant. Une arborescence de projet réelle en compte
     * quelques milliers ; au-delà, on cherche ailleurs qu'où il faut, et il vaut mieux
     * s'arrêter que d'y passer une minute.
     */
    private static final int BUDGET_RECHERCHE = 120_000;

    /** Jusqu'où descendre sous chaque base. Douze niveaux couvrent un paquet profond. */
    private static final int PROFONDEUR = 12;

    /**
     * Ce qu'on ne traverse jamais : ni les métadonnées d'un gestionnaire de versions, ni les
     * dépendances récupérées, ni le bytecode. Aucun {@code .java} utile n'y vit, et ce sont
     * les répertoires qui font exploser le parcours.
     */
    private static final java.util.Set<String> ECARTES = java.util.Set.of(
            ".git", ".svn", ".hg", ".idea", ".vscode", ".gradle", ".m2", ".mvn",
            "node_modules", "classes", "test-classes", "runtime-xray-out");

    /**
     * Les racines qu'il faudrait ajouter — trouvées, et <b>prouvées</b>.
     *
     * <p>Deviner un répertoire de sources d'après une convention serait commode là où on n'en
     * a pas besoin, et faux là où on en aurait besoin : sur la machine où l'application tourne
     * loin de son code, la convention ne désigne rien, ou pire, désigne d'autres sources — et
     * du code faux affiché en face d'une couverture est plus coûteux qu'un panneau vide.
     *
     * <p>On ne devine donc pas : on cherche, et on compte. Chaque {@code .java} rencontré
     * dont le <b>nom</b> figure parmi les classes sans source est lu ; si le paquet qu'il
     * déclare produit exactement une clé manquante, on en déduit sa racine — le chemin
     * amputé de son paquet — et on l'inscrit à son crédit. Une proposition arrive alors avec
     * son chiffre : « cette racine résoudrait 431 des 447 classes manquantes ». Ce n'est plus
     * une devinette, c'est une constatation, et elle se vérifie d'un coup d'œil.
     *
     * @param manquantes les clés {@code paquet/Fichier.java} que la couverture réclame en vain
     * @param bases      les endroits où chercher, du plus probable au moins probable
     * @return au plus {@link #MAX_PISTES} racines, la plus explicative en tête
     */
    public static List<Object> chercherRacines(java.util.Set<String> manquantes, List<Path> bases) {
        if (manquantes.isEmpty() || bases.isEmpty()) return List.of();

        // Par nom de fichier : c'est le seul filtre qu'on puisse appliquer SANS ouvrir le
        // fichier, et il écarte l'immense majorité des candidats pour le prix d'un test.
        java.util.Set<String> noms = new java.util.HashSet<>();
        for (String cle : manquantes) noms.add(cle.substring(cle.lastIndexOf('/') + 1));

        Map<String, int[]> credits = new LinkedHashMap<>();
        Map<String, List<String>> preuves = new LinkedHashMap<>();
        int[] budget = { BUDGET_RECHERCHE };

        for (Path base : dedoublonner(bases)) {
            if (budget[0] <= 0) break;
            explorer(base, base, 0, noms, manquantes, credits, preuves, budget);
        }

        List<Map.Entry<String, int[]>> classees = new ArrayList<>(credits.entrySet());
        // Le plus explicatif d'abord ; à égalité, le chemin le plus court — c'est celui qui
        // couvre le plus large, donc celui qui vieillira le mieux dans une configuration.
        classees.sort((a, b) -> {
            int parLeCompte = Integer.compare(b.getValue()[0], a.getValue()[0]);
            return parLeCompte != 0 ? parLeCompte
                                    : Integer.compare(a.getKey().length(), b.getKey().length());
        });

        List<Object> pistes = new ArrayList<>();
        for (Map.Entry<String, int[]> e : classees) {
            if (pistes.size() >= MAX_PISTES) break;
            Map<String, Object> piste = new LinkedHashMap<>();
            piste.put("racine", e.getKey());
            piste.put("resout", e.getValue()[0]);
            piste.put("surTotal", manquantes.size());
            piste.put("exemples", preuves.get(e.getKey()));
            pistes.add(piste);
        }
        return pistes;
    }

    /** Assez de pistes pour qu'un projet à plusieurs modules s'y retrouve, pas assez pour noyer. */
    private static final int MAX_PISTES = 5;

    /** Trois exemples suffisent à reconnaître un projet ; le compte fait le reste. */
    private static final int PREUVES_PAR_PISTE = 3;

    private static void explorer(Path racineDeRecherche, Path dir, int niveau,
                                 java.util.Set<String> noms, java.util.Set<String> manquantes,
                                 Map<String, int[]> credits, Map<String, List<String>> preuves,
                                 int[] budget) {
        if (niveau > PROFONDEUR || budget[0] <= 0) return;
        try (Stream<Path> enfants = Files.list(dir)) {
            for (Path enfant : (Iterable<Path>) enfants::iterator) {
                if (--budget[0] <= 0) return;
                String nom = enfant.getFileName().toString();
                if (Files.isDirectory(enfant)) {
                    if (!ECARTES.contains(nom) && !nom.startsWith(".")) {
                        explorer(racineDeRecherche, enfant, niveau + 1, noms, manquantes,
                                 credits, preuves, budget);
                    }
                } else if (noms.contains(nom)) {
                    crediter(enfant, nom, manquantes, credits, preuves);
                }
            }
        } catch (IOException | RuntimeException e) {
            // Un répertoire illisible — droits, lien cassé, montage disparu — n'a rien à
            // apprendre : on passe au suivant plutôt que d'abandonner la recherche.
        }
    }

    /**
     * Le fichier porte le bon nom : reste à savoir s'il porte le bon paquet.
     *
     * <p>C'est ce test qui sépare une proposition d'une devinette. Un {@code Application.java}
     * trouvé dans un autre projet ne déclarera pas {@code org.exemple.module}, donc ne comptera pas.
     */
    private static void crediter(Path fichier, String nom, java.util.Set<String> manquantes,
                                 Map<String, int[]> credits, Map<String, List<String>> preuves) {
        String paquet;
        try {
            paquet = paquetDeclare(entete(fichier));
        } catch (IOException e) {
            return;
        }
        if (paquet == null) return;
        String cle = paquet.replace('.', '/') + "/" + nom;
        if (!manquantes.contains(cle)) return;

        // La racine, c'est le chemin amputé de son paquet : le répertoire qu'il aurait fallu
        // désigner. On le donne tel quel, même si le niveau n'a plus d'importance pour
        // l'index — c'est celui qu'un lecteur reconnaîtra comme « le répertoire de sources ».
        Path racine = fichier.getParent();
        for (int i = paquet.split("\\.").length; i > 0 && racine != null; i--) {
            racine = racine.getParent();
        }
        if (racine == null) return;

        String chemin = absolu(racine);
        credits.computeIfAbsent(chemin, k -> new int[1])[0]++;
        List<String> exemples = preuves.computeIfAbsent(chemin, k -> new ArrayList<>());
        if (exemples.size() < PREUVES_PAR_PISTE) exemples.add(cle);
    }

    /**
     * Les premières lignes seulement.
     *
     * <p>La déclaration de paquet est en tête par construction du langage. Lire le fichier
     * entier pour la trouver multiplierait le coût de la recherche par la taille du projet.
     */
    private static List<String> entete(Path p) throws IOException {
        List<String> lignes = new ArrayList<>();
        try (java.io.BufferedReader r = Files.newBufferedReader(p, StandardCharsets.ISO_8859_1)) {
            String ligne;
            while (lignes.size() < 60 && (ligne = r.readLine()) != null) lignes.add(ligne);
        }
        return lignes;
    }

    /**
     * Les bases de recherche, sans redite.
     *
     * <p>Chercher sous un répertoire puis sous son parent parcourt deux fois le même contenu,
     * et sur une arborescence profonde c'est ce qui épuise le budget avant d'avoir trouvé.
     */
    static List<Path> dedoublonner(List<Path> bases) {
        List<Path> retenues = new ArrayList<>();
        List<Path> triees = new ArrayList<>();
        for (Path b : bases) {
            if (b == null) continue;
            Path n = b.toAbsolutePath().normalize();
            if (Files.isDirectory(n) && !triees.contains(n)) triees.add(n);
        }
        // Du plus court au plus long : un parent retenu couvre déjà tous ses descendants.
        triees.sort(java.util.Comparator.comparingInt(x -> x.toString().length()));
        for (Path candidat : triees) {
            boolean dejaCouvert = retenues.stream().anyMatch(candidat::startsWith);
            if (!dejaCouvert) retenues.add(candidat);
        }
        return retenues;
    }
}
