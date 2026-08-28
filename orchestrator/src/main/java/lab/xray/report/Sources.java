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
    private static final int MAX_FILES = 20_000;

    /** {@code package com.example.app;} — la première ligne qui en a la forme fait foi. */
    private static final Pattern PACKAGE =
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
    public record Index(Map<String, Object> byKey, List<Object> roots,
                        Map<String, Object> byName, boolean truncated) {

        public int files() {
            return byKey.size();
        }

        /** Le diagnostic seul, sans le code : c'est lui qui voyage dans la page et le fichier. */
        public Map<String, Object> diagnostic() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("racines", roots);
            m.put("fichiers", files());
            m.put("tronque", truncated);
            m.put("parNom", byName);
            return m;
        }
    }

    /**
     * @param roots les racines demandées, dans l'ordre ; une racine inexistante est retenue
     *              dans le diagnostic plutôt qu'ignorée en silence
     */
    public static Index load(List<Path> roots) {
        Map<String, Object> byKey = new LinkedHashMap<>();
        Map<String, Object> byName = new LinkedHashMap<>();
        List<Object> rootViews = new ArrayList<>();
        boolean truncated = false;

        for (Path root : roots) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("demandee", root.toString());
            view.put("absolue", absolu(root));
            boolean exists = Files.isDirectory(root);
            view.put("existe", exists);
            view.put("fichiers", 0);
            rootViews.add(view);
            if (!exists) {
                view.put("motif", Files.exists(root)
                        ? "this path exists but is not a directory"
                        : "this path does not exist");
                continue;
            }

            int before = byKey.size();
            // Les liens sont suivis : une arborescence de sources montée ailleurs, ou un
            // répertoire lié, est un cas courant sur les postes de développement.
            try (Stream<Path> files = Files.walk(root, FileVisitOption.FOLLOW_LINKS)) {
                for (Path p : (Iterable<Path>) files.filter(Sources::isSource)::iterator) {
                    if (byKey.size() >= MAX_FILES) {
                        truncated = true;
                        break;
                    }
                    index(root, p, byKey, byName);
                }
            } catch (IOException e) {
                // Une racine illisible ne doit pas faire échouer tout le rapport : on la
                // retient telle quelle dans le diagnostic, et on passe à la suivante.
                view.put("motif", "parcours interrompu : " + e.getMessage());
            }
            view.put("fichiers", byKey.size() - before);
        }
        return new Index(byKey, rootViews, byName, truncated);
    }

    private static boolean isSource(Path p) {
        return p.getFileName() != null && p.getFileName().toString().endsWith(".java");
    }

    private static void index(Path root, Path file,
                                Map<String, Object> byKey, Map<String, Object> byName) {
        List<String> lines;
        try {
            lines = read(file);
        } catch (IOException e) {
            // Un fichier illisible ne doit pas faire échouer tout le rapport :
            // on le signale et on continue.
            System.err.println("   source skipped: " + file + " (" + e.getMessage() + ")");
            return;
        }
        String name = file.getFileName().toString();
        String pkg = declaredPackage(lines);
        String relative = root.relativize(file).toString().replace('\\', '/');
        String key = pkg == null ? relative : pkg.replace('.', '/') + "/" + name;

        byKey.put(key, lines);

        Map<String, Object> where = new LinkedHashMap<>();
        where.put("cle", key);
        where.put("chemin", absolu(file));
        where.put("paquet", pkg);
        where.put("racine", absolu(root));
        // Le même nom de fichier peut vivre dans plusieurs paquets : c'est précisément le
        // cas où il faut montrer les deux plutôt que d'en choisir un.
        @SuppressWarnings("unchecked")
        List<Object> places = (List<Object>) byName.computeIfAbsent(name, k -> new ArrayList<>());
        places.add(where);
    }

    /**
     * Le paquet déclaré par ce fichier, ou {@code null} s'il n'en déclare aucun.
     *
     * <p>On s'arrête à la première ligne qui ouvre le type : au-delà, un {@code package}
     * ne serait plus une déclaration mais du texte — une chaîne, un commentaire.
     */
    static String declaredPackage(List<String> lines) {
        for (String line : lines) {
            Matcher m = PACKAGE.matcher(line);
            if (m.find()) return m.group(1).replaceAll("\\s+", "");
            if (line.matches("^\\s*(public\\s+|final\\s+|abstract\\s+|sealed\\s+|non-sealed\\s+)*"
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
    private static List<String> read(Path p) throws IOException {
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
    private static final int SEARCH_BUDGET = 120_000;

    /** Jusqu'où descendre sous chaque base. Douze niveaux couvrent un paquet profond. */
    private static final int DEPTH = 12;

    /**
     * Ce qu'on ne traverse jamais : ni les métadonnées d'un gestionnaire de versions, ni les
     * dépendances récupérées, ni le bytecode. Aucun {@code .java} utile n'y vit, et ce sont
     * les répertoires qui font exploser le parcours.
     */
    private static final java.util.Set<String> DROPPED = java.util.Set.of(
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
    public static List<Object> searchRoots(java.util.Set<String> missing, List<Path> bases) {
        if (missing.isEmpty() || bases.isEmpty()) return List.of();

        // Par nom de fichier : c'est le seul filtre qu'on puisse appliquer SANS ouvrir le
        // fichier, et il écarte l'immense majorité des candidats pour le prix d'un test.
        java.util.Set<String> names = new java.util.HashSet<>();
        for (String key : missing) names.add(key.substring(key.lastIndexOf('/') + 1));

        Map<String, int[]> credits = new LinkedHashMap<>();
        Map<String, List<String>> evidence = new LinkedHashMap<>();
        int[] budget = { SEARCH_BUDGET };

        for (Path base : deduplicate(bases)) {
            if (budget[0] <= 0) break;
            explore(base, base, 0, names, missing, credits, evidence, budget);
        }

        List<Map.Entry<String, int[]>> classified = new ArrayList<>(credits.entrySet());
        // Le plus explicatif d'abord ; à égalité, le chemin le plus court — c'est celui qui
        // couvre le plus large, donc celui qui vieillira le mieux dans une configuration.
        classified.sort((a, b) -> {
            int byCount = Integer.compare(b.getValue()[0], a.getValue()[0]);
            return byCount != 0 ? byCount
                                    : Integer.compare(a.getKey().length(), b.getKey().length());
        });

        List<Object> leads = new ArrayList<>();
        for (Map.Entry<String, int[]> e : classified) {
            if (leads.size() >= MAX_LEADS) break;
            Map<String, Object> lead = new LinkedHashMap<>();
            lead.put("racine", e.getKey());
            lead.put("resout", e.getValue()[0]);
            lead.put("surTotal", missing.size());
            lead.put("exemples", evidence.get(e.getKey()));
            leads.add(lead);
        }
        return leads;
    }

    /** Assez de pistes pour qu'un projet à plusieurs modules s'y retrouve, pas assez pour noyer. */
    private static final int MAX_LEADS = 5;

    /** Trois exemples suffisent à reconnaître un projet ; le compte fait le reste. */
    private static final int EVIDENCE_PER_LEAD = 3;

    private static void explore(Path searchRoot, Path dir, int level,
                                 java.util.Set<String> names, java.util.Set<String> missing,
                                 Map<String, int[]> credits, Map<String, List<String>> evidence,
                                 int[] budget) {
        if (level > DEPTH || budget[0] <= 0) return;
        try (Stream<Path> children = Files.list(dir)) {
            for (Path child : (Iterable<Path>) children::iterator) {
                if (--budget[0] <= 0) return;
                String name = child.getFileName().toString();
                if (Files.isDirectory(child)) {
                    if (!DROPPED.contains(name) && !name.startsWith(".")) {
                        explore(searchRoot, child, level + 1, names, missing,
                                 credits, evidence, budget);
                    }
                } else if (names.contains(name)) {
                    credit(child, name, missing, credits, evidence);
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
     * trouvé dans un autre projet ne déclarera pas {@code com.example.app}, donc ne comptera pas.
     */
    private static void credit(Path file, String name, java.util.Set<String> missing,
                                 Map<String, int[]> credits, Map<String, List<String>> evidence) {
        String pkg;
        try {
            pkg = declaredPackage(header(file));
        } catch (IOException e) {
            return;
        }
        if (pkg == null) return;
        String key = pkg.replace('.', '/') + "/" + name;
        if (!missing.contains(key)) return;

        // La racine, c'est le chemin amputé de son paquet : le répertoire qu'il aurait fallu
        // désigner. On le donne tel quel, même si le niveau n'a plus d'importance pour
        // l'index — c'est celui qu'un lecteur reconnaîtra comme « le répertoire de sources ».
        Path root = file.getParent();
        for (int i = pkg.split("\\.").length; i > 0 && root != null; i--) {
            root = root.getParent();
        }
        if (root == null) return;

        String path = absolu(root);
        credits.computeIfAbsent(path, k -> new int[1])[0]++;
        List<String> examples = evidence.computeIfAbsent(path, k -> new ArrayList<>());
        if (examples.size() < EVIDENCE_PER_LEAD) examples.add(key);
    }

    /**
     * Les premières lignes seulement.
     *
     * <p>La déclaration de paquet est en tête par construction du langage. Lire le fichier
     * entier pour la trouver multiplierait le coût de la recherche par la taille du projet.
     */
    private static List<String> header(Path p) throws IOException {
        List<String> lines = new ArrayList<>();
        try (java.io.BufferedReader r = Files.newBufferedReader(p, StandardCharsets.ISO_8859_1)) {
            String line;
            while (lines.size() < 60 && (line = r.readLine()) != null) lines.add(line);
        }
        return lines;
    }

    /**
     * Les bases de recherche, sans redite.
     *
     * <p>Chercher sous un répertoire puis sous son parent parcourt deux fois le même contenu,
     * et sur une arborescence profonde c'est ce qui épuise le budget avant d'avoir trouvé.
     */
    static List<Path> deduplicate(List<Path> bases) {
        List<Path> kept = new ArrayList<>();
        List<Path> sortedList = new ArrayList<>();
        for (Path b : bases) {
            if (b == null) continue;
            Path n = b.toAbsolutePath().normalize();
            if (Files.isDirectory(n) && !sortedList.contains(n)) sortedList.add(n);
        }
        // Du plus court au plus long : un parent retenu couvre déjà tous ses descendants.
        sortedList.sort(java.util.Comparator.comparingInt(x -> x.toString().length()));
        for (Path candidate : sortedList) {
            boolean alreadyCovered = kept.stream().anyMatch(candidate::startsWith);
            if (!alreadyCovered) kept.add(candidate);
        }
        return kept;
    }
}
