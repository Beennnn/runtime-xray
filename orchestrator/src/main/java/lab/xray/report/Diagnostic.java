package lab.xray.report;

import lab.xray.json.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Ce qu'il faut savoir quand la vue ne montre pas ce qu'on attendait.
 *
 * <p>Un rapport qui manque quelque chose ne dit pas pourquoi : un panneau de code vide se
 * lit exactement comme un panneau de code qu'on n'a pas su remplir. Le 26 août 2026, une
 * analyse portant sur 447 classes affichait « Source indisponible » sur chacune, et rien
 * dans le rapport ne permettait de trancher entre les quatre causes possibles — racine non
 * renseignée, racine inexistante, racine au mauvais niveau, ou sources réellement absentes
 * de la machine. Il a fallu demander une capture d'écran.
 *
 * <p>D'où ce fichier. Il est écrit <b>à chaque assemblage</b>, à côté de la page, et il est
 * fait pour être <b>renvoyé tel quel</b> : il porte ce qu'on aurait posé comme questions.
 * Ce qui a été demandé, ce qui a été trouvé, où, et — quand un rapprochement échoue — ce
 * qu'il aurait fallu écrire pour qu'il réussisse.
 *
 * <p><b>Il ne contient aucun secret</b> : ni le jeton du serveur partagé, ni les valeurs de
 * paramètres capturées. Il porte en revanche des chemins absolus et des noms de classes,
 * comme le rapport lui-même — c'est un fichier à faire circuler avec le même soin.
 */
public final class Diagnostic {

    /** Assez pour comprendre, pas assez pour recopier le rapport. */
    private static final int EXEMPLES = 40;

    /**
     * Le nombre de classes recensées dans le bytecode, toutes racines confondues.
     *
     * <p>Cette liste voyage dans la page — c'est elle qui alimente l'arbre et la recherche.
     * Un classpath applicatif en compte quelques centaines à quelques milliers ; au-delà on
     * s'arrête, et on le dit, plutôt que de faire une page de plusieurs mégaoctets.
     */
    private static final int MAX_CLASSES = 4_000;

    private Diagnostic() {}

    /**
     * @param commonDir  le répertoire de sortie, où le fichier est déposé
     * @param runs       les exécutions telles que la vue les reçoit
     * @param index      l'index des sources, avec ce qu'il a vu en le construisant
     * @param contexte   ce que seul l'appelant sait — configuration, composants, options —
     *                   ou {@code null} quand la vue est réassemblée sans lui
     * @return le contenu écrit, pour que l'appelant puisse en tirer un résumé sans le relire
     */
    public static Map<String, Object> write(Path commonDir, List<Object> runs,
                                            Sources.Index index, Map<String, Object> contexte)
            throws IOException {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("outil", "runtime-xray");
        d.put("version", version());
        d.put("date", ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        d.put("machine", environnement());
        if (contexte != null && !contexte.isEmpty()) d.put("lancement", contexte);
        d.put("sortie", commonDir.toAbsolutePath().normalize().toString());
        d.put("executions", executions(runs));
        d.put("sources", index.diagnostic());
        List<Object> bytecode = bytecode(contexte);
        d.put("bytecode", bytecode);
        Map<String, Object> rapprochement = rapprochement(runs, index);
        // La recherche ne part que s'il y a quelque chose à chercher : elle parcourt le
        // disque, et sur un rapport complet elle n'aurait rien à trouver.
        if (nombre(rapprochement.get("fichiersSansSource")) > 0) {
            rapprochement.put("pistes", Sources.chercherRacines(
                    manquantes(runs, index), ouChercher(commonDir, index, bytecode)));
        }
        d.put("rapprochement", rapprochement);

        Path out = commonDir.resolve("diagnostic.json");
        Files.writeString(out, Json.write(d), StandardCharsets.UTF_8);
        return d;
    }

    /**
     * Le rapprochement couverture ↔ sources, classe par classe.
     *
     * <p>C'est le cœur du fichier : la couverture énumère les fichiers qu'elle a mesurés,
     * l'index énumère ceux qu'on a lus, et l'intersection est ce que la vue saura montrer.
     * Pour chaque fichier manquant on cherche le même nom ailleurs dans l'index — c'est le
     * cas d'une racine mal placée, et il se corrige d'un chemin.
     */
    static Map<String, Object> rapprochement(List<Object> runs, Sources.Index index) {
        Set<String> attendus = mesures(runs);

        List<Object> trouves = new ArrayList<>();
        List<Object> manquants = new ArrayList<>();
        for (String cle : attendus) {
            if (index.parCle().containsKey(cle)) {
                if (trouves.size() < EXEMPLES) trouves.add(cle);
            } else {
                if (manquants.size() < EXEMPLES) manquants.add(manquant(cle, index));
            }
        }

        int nbManquants = 0;
        for (String cle : attendus) if (!index.parCle().containsKey(cle)) nbManquants++;

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fichiersMesures", attendus.size());
        m.put("fichiersAvecSource", attendus.size() - nbManquants);
        m.put("fichiersSansSource", nbManquants);
        m.put("exemplesTrouves", trouves);
        m.put("exemplesManquants", manquants);
        m.put("conclusion", conclusion(attendus.size(), nbManquants, index));
        return m;
    }

    /**
     * Les fichiers que la couverture dit avoir mesurés, toutes exécutions confondues.
     *
     * <p>Toutes, et non celle qu'on regarde : une classe absente d'une exécution mais
     * présente dans une autre reste une classe dont on veut le code.
     */
    static Set<String> mesures(List<Object> runs) {
        Set<String> attendus = new LinkedHashSet<>();
        for (Object r : runs) {
            if (r instanceof Map<?, ?> run && run.get("packages") instanceof Map<?, ?> pkgs) {
                for (Object classes : pkgs.values()) {
                    if (!(classes instanceof Iterable<?> list)) continue;
                    for (Object c : list) {
                        if (c instanceof Map<?, ?> cls && cls.get("source") instanceof String s) {
                            attendus.add(s);
                        }
                    }
                }
            }
        }
        return attendus;
    }

    /** Les clés que la couverture réclame et que l'index n'a pas. */
    static java.util.Set<String> manquantes(List<Object> runs, Sources.Index index) {
        java.util.Set<String> out = new LinkedHashSet<>();
        for (String cle : mesures(runs)) {
            if (!index.parCle().containsKey(cle)) out.add(cle);
        }
        return out;
    }

    /**
     * Où chercher les sources qui manquent, du plus probable au moins probable.
     *
     * <p>Aucun de ces endroits n'est une convention : ce sont les seuls que l'exécution nous
     * ait fait connaître. Le bytecode analysé est le meilleur indice de tous — un
     * {@code <projet>/target/classes} désigne le projet à deux répertoires près, et c'est là
     * que sont ses sources. La racine déjà configurée en est un autre : quand elle est d'un
     * cran à côté, le bon répertoire est son voisin immédiat.
     *
     * <p>On ne remonte jamais plus haut que ces indices, et jamais vers la racine du disque :
     * une recherche qui balaie tout finirait par proposer les sources d'un autre projet.
     */
    static List<Path> ouChercher(Path commonDir, Sources.Index index, List<Object> bytecode) {
        List<Path> bases = new ArrayList<>();
        // 1. autour du bytecode réellement analysé
        for (Object o : bytecode) {
            if (o instanceof Map<?, ?> b && b.get("absolu") instanceof String chemin) {
                remonter(Path.of(chemin), 2, bases);
            }
        }
        // 2. autour des racines de sources déjà données — le cas « d'un cran à côté »
        for (Object o : index.racines()) {
            if (o instanceof Map<?, ?> r && r.get("absolue") instanceof String chemin) {
                remonter(Path.of(chemin), 1, bases);
            }
        }
        // 3. le répertoire depuis lequel l'analyse a été lancée
        bases.add(Path.of(System.getProperty("user.dir")));
        // 4. à défaut, le voisinage du rapport lui-même
        remonter(commonDir, 1, bases);
        return bases;
    }

    /** Un chemin et ses ascendants, jusqu'à {@code crans} — jamais au-delà. */
    private static void remonter(Path depart, int crans, List<Path> bases) {
        Path p = depart.toAbsolutePath().normalize();
        if (Files.isRegularFile(p)) p = p.getParent();          // un jar désigne son répertoire
        for (int i = 0; i <= crans && p != null && p.getParent() != null; i++) {
            bases.add(p);
            p = p.getParent();
        }
    }

    private static long nombre(Object o) {
        return o instanceof Number n ? n.longValue() : 0;
    }

    /**
     * Un fichier mesuré dont on n'a pas la source — et ce qu'on sait d'approchant.
     *
     * <p>Le même nom trouvé sous un autre paquet est presque toujours le bon fichier vu
     * depuis une racine décalée. On donne alors le chemin réel : c'est de lui qu'on déduit
     * la racine à passer.
     */
    private static Map<String, Object> manquant(String cle, Sources.Index index) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cherche", cle);
        String nom = cle.substring(cle.lastIndexOf('/') + 1);
        Object homonymes = index.parNom().get(nom);
        m.put("memeNomAilleurs", homonymes);
        m.put("explication", homonymes == null
                ? "aucun fichier de ce nom sous les racines passées"
                : "un fichier de ce nom existe, mais son paquet déclaré n'est pas celui "
                  + "que la couverture rapporte — ce n'est probablement pas la même classe");
        return m;
    }

    /**
     * Ce que chaque racine de bytecode contient réellement.
     *
     * <p>« Classes introuvables » et « sources introuvables » se ressemblent dans un rapport
     * vide, et se corrigent de deux façons opposées. Les séparer demande de savoir ce que
     * l'outil a effectivement ouvert : quels répertoires, quels jar, et quelles classes il y
     * a vues. On recense donc, pour chaque entrée du classpath, les noms de classes qu'elle
     * porte — de quoi répondre, dans la page, à « où est cette classe ? » sans rien relancer.
     *
     * <p>Les classes internes ({@code Machin$1}) sont écartées : elles n'ont pas de fichier
     * source à elles, et elles tripleraient la liste sans rien apprendre.
     */
    static List<Object> bytecode(Map<String, Object> contexte) {
        List<Object> out = new ArrayList<>();
        if (contexte == null) return out;
        Object racines = contexte.get("racinesClasses");
        if (!(racines instanceof Iterable<?> liste)) return out;

        int budget = MAX_CLASSES;
        for (Object o : liste) {
            if (!(o instanceof Map<?, ?> racine)) continue;
            String chemin = String.valueOf(racine.get("absolu"));
            Path p = Path.of(chemin);
            Map<String, Object> vue = new LinkedHashMap<>();
            vue.put("chemin", String.valueOf(racine.get("chemin")));
            vue.put("absolu", chemin);
            boolean jar = Files.isRegularFile(p);
            vue.put("type", jar ? "jar" : Files.isDirectory(p) ? "répertoire" : "absent");
            vue.put("existe", Files.exists(p));

            List<Object> classes = new ArrayList<>();
            boolean tronque = false;
            try {
                for (String nom : jar ? classesDuJar(p) : classesDuRepertoire(p)) {
                    if (classes.size() >= budget) { tronque = true; break; }
                    classes.add(nom);
                }
            } catch (IOException e) {
                vue.put("motif", "lecture impossible : " + e.getMessage());
            }
            budget -= classes.size();
            vue.put("classes", classes);
            vue.put("nombre", classes.size());
            vue.put("tronque", tronque);
            out.add(vue);
        }
        return out;
    }

    private static List<String> classesDuJar(Path jar) throws IOException {
        List<String> noms = new ArrayList<>();
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jar.toFile())) {
            for (java.util.Enumeration<? extends java.util.zip.ZipEntry> e = zip.entries();
                 e.hasMoreElements(); ) {
                String nom = e.nextElement().getName();
                if (retenue(nom)) noms.add(nom.substring(0, nom.length() - ".class".length()));
            }
        }
        noms.sort(null);
        return noms;
    }

    private static List<String> classesDuRepertoire(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return List.of();
        List<String> noms = new ArrayList<>();
        try (java.util.stream.Stream<Path> fichiers = Files.walk(dir)) {
            for (Path f : (Iterable<Path>) fichiers::iterator) {
                String rel = dir.relativize(f).toString().replace('\\', '/');
                if (retenue(rel)) noms.add(rel.substring(0, rel.length() - ".class".length()));
            }
        }
        noms.sort(null);
        return noms;
    }

    /** Une classe de premier niveau, et pas un artefact du compilateur. */
    private static boolean retenue(String nom) {
        return nom.endsWith(".class") && !nom.contains("$") && !nom.endsWith("package-info.class")
                && !nom.startsWith("META-INF/");
    }

    /** Une phrase, celle qu'on lirait en premier. */
    private static String conclusion(int attendus, int manquants, Sources.Index index) {
        if (index.racines().isEmpty()) {
            return "aucun répertoire de sources n'a été passé : le code annoté ne peut pas "
                 + "s'afficher. Renseigner SOURCE_DIRS dans la configuration, ou --sources "
                 + "en ligne de commande.";
        }
        if (index.fichiers() == 0) {
            return "les racines passées n'ont fourni aucun fichier .java — voir sources.racines "
                 + "pour le chemin absolu de chacune et la raison.";
        }
        if (attendus == 0) {
            return "aucune couverture à rapprocher : la mesure n'a rien enregistré.";
        }
        if (manquants == 0) {
            return "toutes les classes mesurées ont leur source.";
        }
        if (manquants == attendus) {
            return "aucune classe mesurée n'a sa source, alors que " + index.fichiers()
                 + " fichier(s) .java ont été lus : les sources trouvées ne sont pas celles "
                 + "de l'application analysée, ou elles n'en sont qu'une partie.";
        }
        return manquants + " classe(s) mesurée(s) sur " + attendus + " n'ont pas leur source.";
    }

    private static List<Object> executions(List<Object> runs) {
        List<Object> out = new ArrayList<>();
        for (Object r : runs) {
            if (!(r instanceof Map<?, ?> run)) continue;
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("nom", run.get("nom"));
            e.put("uuid", run.get("uuid"));
            e.put("chemin", run.get("chemin"));
            e.put("rapports", run.get("rapports"));
            e.put("paquetsMasques", run.get("paquetsMasques"));
            e.put("contexte", run.get("context"));
            // Des tailles, pas des contenus : de quoi voir qu'une mesure est vide.
            e.put("classesMesurees", compter(run.get("packages")));
            e.put("fichiersCouverts", taille(run.get("coverage")));
            e.put("methodesInspectees", taille(run.get("values")));
            out.add(e);
        }
        return out;
    }

    private static int compter(Object packages) {
        int n = 0;
        if (packages instanceof Map<?, ?> m) {
            for (Object v : m.values()) {
                if (v instanceof java.util.Collection<?> c) n += c.size();
            }
        }
        return n;
    }

    private static int taille(Object o) {
        return o instanceof Map<?, ?> m ? m.size() : 0;
    }

    private static Map<String, Object> environnement() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("java", System.getProperty("java.version"));
        m.put("jvm", System.getProperty("java.vm.name"));
        m.put("javaHome", System.getProperty("java.home"));
        m.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " (" + System.getProperty("os.arch") + ")");
        m.put("repertoireCourant", System.getProperty("user.dir"));
        // L'encodage et la locale expliquent des symptômes qu'on impute d'ordinaire au code :
        // accents illisibles, sources refusées à la lecture, séparateurs décimaux inattendus.
        m.put("encodageFichiers", System.getProperty("file.encoding"));
        m.put("encodageSortie", System.getProperty("stdout.encoding"));
        m.put("locale", Locale.getDefault().toLanguageTag());
        m.put("separateurChemin", java.io.File.separator);
        return m;
    }

    /** La version telle que le jar la déclare, ou « inconnue » quand on tourne sur les classes. */
    private static String version() {
        String v = Diagnostic.class.getPackage().getImplementationVersion();
        return v == null ? "inconnue" : v;
    }
}
