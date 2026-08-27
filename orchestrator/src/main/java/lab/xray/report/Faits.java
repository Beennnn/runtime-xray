package lab.xray.report;

import lab.xray.json.Json;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Le même rapport, écrit pour être <b>filtré</b> plutôt que parcouru.
 *
 * <p>La page range les mesures en arbre — paquets, classes, méthodes — parce que c'est ainsi
 * qu'un humain descend vers ce qu'il cherche. Un programme, lui, ne descend pas : il
 * <b>sélectionne</b>. « Quelles classes n'ont jamais tourné ? » se répond ici par un
 * {@code grep}, là par une traversée complète de l'arbre. Sur 450 classes, la différence
 * n'est pas de confort : c'est un ordre de grandeur de ce qu'il faut lire pour répondre.
 *
 * <p>D'où ce fichier : <b>un fait par ligne</b>, plat, chaque ligne se suffisant à
 * elle-même. Pas de contexte à reconstituer, pas d'ordre à respecter, pas de fichier
 * compagnon à ouvrir. On peut en lire dix lignes au milieu et les comprendre.
 *
 * <pre>
 * grep '"classe.jamais_executee"' faits.jsonl | head -50
 * jq -r 'select(.fait=="indisponibilite") | .pourquoi' faits.jsonl
 * </pre>
 *
 * <h2>Ce qu'il n'est pas</h2>
 *
 * <p>Il <b>n'enlève rien</b>. La page, ses blocs, {@code diagnostic.json} et
 * {@code rapport.md} sont écrits comme avant, au bit près : ce fichier s'ajoute à côté. Il
 * n'y a donc pas deux formats à maintenir en parallèle, il y a une seconde sortie du même
 * calcul — et rien qui lise l'ancien n'a à changer.
 *
 * <p>Il n'est pas non plus une réplique : il ne porte <b>ni le code source, ni les valeurs
 * capturées</b>, qui sont volumineux et n'ont d'intérêt qu'ouverts. Il porte les faits, et
 * pour chacun de quoi aller chercher le reste.
 *
 * <h2>Ce qui compte le plus</h2>
 *
 * <p>Les lignes {@code indisponibilite} et {@code reserve}. Un « 0 % de temps » ne dit pas
 * s'il signifie « ce code n'a pas tourné » ou « aucun profil n'a pu être pris sur cette
 * plateforme ». Un lecteur qui ne sait pas trancher tranchera quand même — et se trompera
 * avec assurance. Ces lignes-là existent pour que cette question ne se pose jamais.
 */
public final class Faits {

    /** Le vocabulaire des faits. Il ne bouge qu'en ajoutant, jamais en renommant. */
    public static final String FORMAT = "1.0";

    /** Le fichier, à la racine de la sortie : c'est la première chose qu'un outil ouvre. */
    public static final String FICHIER = "faits.jsonl";

    /** Bornes : un fichier de faits doit rester lisible d'un coup, sinon il redevient un tas. */
    static final int MAX_SOURCES_MANQUANTES = 500;
    static final int MAX_METHODES_CHAUDES = 25;

    /**
     * Ce que chaque nom de fait veut dire.
     *
     * <p>Écrit dans le fichier lui-même, pas à côté : c'est ce qui permet à un programme de
     * lire une sortie qu'il n'a jamais vue. Toute addition au vocabulaire s'ajoute ici, sans
     * quoi elle serait muette.
     */
    static final Map<String, String> VOCABULAIRE = new LinkedHashMap<>();
    static {
        VOCABULAIRE.put("campagne", "l'en-tête : outil, version, date, et où trouver le reste");
        VOCABULAIRE.put("execution", "une exécution observée : identité, commande, machine");
        VOCABULAIRE.put("indisponibilite",
                "une mesure qui N'A PAS été prise, et pourquoi — à lire AVANT tout chiffre "
                + "à zéro, qui sans cela se confond avec « n'a pas tourné »");
        VOCABULAIRE.put("reserve", "une mesure prise, mais dont l'outil limite lui-même la portée");
        VOCABULAIRE.put("couverture.execution",
                "instructions couvertes sur le total, pour une exécution (mesure JaCoCo)");
        VOCABULAIRE.put("classe",
                "une classe et sa meilleure couverture sur la campagne, avec les exécutions "
                + "qui l'ont couverte");
        VOCABULAIRE.put("classe.jamais_executee",
                "une classe analysée qu'aucune exécution n'a atteinte — le fait le plus "
                + "souvent cherché");
        VOCABULAIRE.put("methode.chaude",
                "une méthode parmi les plus coûteuses en temps, en relevés de pile");
        VOCABULAIRE.put("source.introuvable",
                "un fichier dont la couverture est connue mais pas le code : racine de "
                + "sources non configurée, jamais « code inexistant »");
        VOCABULAIRE.put("piste.source",
                "une racine à ajouter à SOURCE_DIRS, avec le nombre de fichiers qu'elle "
                + "résoudrait — la seule information directement actionnable du lot");
    }

    private Faits() {}

    /**
     * Écrit les faits de la campagne.
     *
     * @param diagnostic ce que {@link Diagnostic} vient de produire — on ne recalcule rien,
     *                   on en tire les pistes de sources, qui sont la seule information
     *                   directement actionnable du lot.
     */
    public static Path ecrire(Path commonDir, List<Object> runs, Sources.Index index,
                              Map<String, Object> diagnostic) throws IOException {
        Path fichier = commonDir.resolve(FICHIER);
        try (BufferedWriter w = Files.newBufferedWriter(fichier, StandardCharsets.UTF_8)) {
            ligne(w, campagne(commonDir, runs, diagnostic));
            for (Object o : runs) {
                if (!(o instanceof Map<?, ?> run)) continue;
                ligne(w, execution(run));
                for (Map<String, Object> f : indisponibilites(run)) ligne(w, f);
                for (Map<String, Object> f : reserves(run)) ligne(w, f);
                ligne(w, couvertureDe(run));
                for (Map<String, Object> f : methodesChaudes(run)) ligne(w, f);
            }
            for (Map<String, Object> f : classes(runs, index)) ligne(w, f);
            for (Map<String, Object> f : sourcesManquantes(runs, index)) ligne(w, f);
            for (Map<String, Object> f : pistes(diagnostic)) ligne(w, f);
        }
        return fichier;
    }

    private static void ligne(BufferedWriter w, Map<String, Object> fait) throws IOException {
        w.write(Json.write(fait));
        w.write("\n");
    }

    // ------------------------------------------------------------------ la campagne

    private static Map<String, Object> campagne(Path commonDir, List<Object> runs,
                                                Map<String, Object> diagnostic) {
        Map<String, Object> f = fait("campagne");
        f.put("formatFaits", FORMAT);
        f.put("formatRapport", Blocs.FORMAT);
        f.put("outil", diagnostic.get("outil"));
        f.put("version", diagnostic.get("version"));
        f.put("date", diagnostic.get("date"));
        f.put("sortie", commonDir.toAbsolutePath().normalize().toString());
        f.put("executions", runs.size());
        // Où aller quand un fait ne suffit pas. Un lecteur qui arrive par ce fichier n'a
        // aucune raison de deviner que la page et le diagnostic existent.
        f.put("aussi", Map.of(
                "page", "index.html",
                "diagnostic", "diagnostic.json",
                "markdown", "rapport.md",
                "manifeste", Blocs.GLOBAL + "/manifeste.json"));
        // Le fichier se décrit lui-même, dès sa première ligne. Un lecteur qui n'a que ce
        // fichier — c'est le cas d'un dossier zippé, ou d'un programme qui n'a lu que la
        // tête — doit pouvoir en comprendre le reste sans documentation extérieure. Une
        // documentation séparée se perd ; celle-ci voyage avec la donnée.
        f.put("vocabulaire", VOCABULAIRE);
        return f;
    }

    private static Map<String, Object> execution(Map<?, ?> run) {
        Map<String, Object> f = fait("execution");
        f.put("execution", run.get("uuid"));
        f.put("nom", run.get("nom"));
        f.put("chemin", run.get("chemin"));
        f.put("mesures", releves(run));
        f.put("intervalleMs", run.get("intervalMs"));
        if (run.get("context") instanceof Map<?, ?> ctx) {
            for (String cle : List.of("commande", "machine", "systeme", "java", "debut",
                    "fin", "duree", "statut", "methodeRacine")) {
                if (ctx.get(cle) != null) f.put(cle, ctx.get(cle));
            }
        }
        return f;
    }

    // ------------------------------------------------- ce qui n'a PAS été mesuré, et pourquoi

    /**
     * L'absence, déclarée.
     *
     * <p>C'est la raison d'être du fichier. Un observateur qui n'a pas tourné laisse
     * exactement la même trace qu'un code qui n'a pas été atteint : zéro. La différence ne
     * se déduit d'aucun chiffre — il faut l'écrire.
     */
    static List<Map<String, Object>> indisponibilites(Map<?, ?> run) {
        List<Map<String, Object>> out = new ArrayList<>();
        String uuid = String.valueOf(run.get("uuid"));

        if (releves(run) == 0) {
            Map<String, Object> f = fait("indisponibilite");
            f.put("execution", uuid);
            f.put("quoi", "temps");
            f.put("pourquoi", "aucun relevé de pile n'a été pris : async-profiler ne publie "
                    + "que des binaires Linux et macOS, et le niveau « couverture » ne "
                    + "l'active pas");
            f.put("consequence", "aucun pourcentage de temps n'est calculable pour cette "
                    + "exécution ; un temps à zéro n'y signifie pas « jamais appelé »");
            f.put("remede", "relancer sous Linux ou macOS, en niveau « arbre » ou « complet »");
            out.add(f);
        }
        if (vide(run.get("values"))) {
            Map<String, Object> f = fait("indisponibilite");
            f.put("execution", uuid);
            f.put("quoi", "valeurs");
            f.put("pourquoi", "Arthas n'a capturé aucun appel : soit --root n'a pas été "
                    + "donné, soit l'application s'est terminée avant l'attachement");
            f.put("consequence", "aucune valeur de paramètre n'est disponible ; cela ne dit "
                    + "rien sur ce qui a été exécuté");
            f.put("remede", "relancer avec --root paquet.Classe::methode, ou baisser "
                    + "--attach-after");
            out.add(f);
        }
        if (vide(run.get("coverage"))) {
            Map<String, Object> f = fait("indisponibilite");
            f.put("execution", uuid);
            f.put("quoi", "couverture");
            f.put("pourquoi", "aucune donnée JaCoCo pour cette exécution");
            f.put("consequence", "ni couverture ni liste de classes exécutées");
            f.put("remede", "vérifier que l'agent a bien été chargé — voir diagnostic.json");
            out.add(f);
        }
        return out;
    }

    /** Les réserves que l'outil pose lui-même sur une mesure qui, elle, existe. */
    static List<Map<String, Object>> reserves(Map<?, ?> run) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String cle : List.of("profileNote", "stacksNote")) {
            Object note = run.get(cle);
            if (note == null || String.valueOf(note).isBlank()) continue;
            Map<String, Object> f = fait("reserve");
            f.put("execution", run.get("uuid"));
            f.put("quoi", "temps");
            f.put("reserve", note);
            out.add(f);
        }
        return out;
    }

    // ------------------------------------------------------------------ les mesures

    private static Map<String, Object> couvertureDe(Map<?, ?> run) {
        long couvertes = 0, manquees = 0;
        for (Object classes : paquets(run).values()) {
            for (Map<?, ?> c : classesDe(classes)) {
                couvertes += entier(c.get("covered"));
                manquees += entier(c.get("missed"));
            }
        }
        Map<String, Object> f = fait("couverture.execution");
        f.put("execution", run.get("uuid"));
        f.put("instructionsCouvertes", couvertes);
        f.put("instructionsTotal", couvertes + manquees);
        f.put("pct", pourcent(couvertes, couvertes + manquees));
        f.put("mesure", "jacoco.instructions");
        return f;
    }

    /**
     * Une ligne par classe, valable pour toute la campagne.
     *
     * <p>Une classe apparaît dans plusieurs exécutions avec des couvertures différentes. La
     * question posée devant un rapport de recette n'est pas « combien dans celle-ci ? » mais
     * « <b>quelque chose</b> l'a-t-il couverte, et laquelle ? ». On garde donc le meilleur
     * chiffre, et la liste de ceux qui y ont contribué.
     */
    static List<Map<String, Object>> classes(List<Object> runs, Sources.Index index) {
        Map<String, Map<String, Object>> parClasse = new TreeMap<>();
        Map<String, Set<String>> couvrantes = new LinkedHashMap<>();
        Map<String, Set<String>> analysees = new LinkedHashMap<>();

        for (Object o : runs) {
            if (!(o instanceof Map<?, ?> run)) continue;
            String uuid = String.valueOf(run.get("uuid"));
            for (Object classes : paquets(run).values()) {
                for (Map<?, ?> c : classesDe(classes)) {
                    String nom = String.valueOf(c.get("name"));
                    long couv = entier(c.get("covered"));
                    long total = couv + entier(c.get("missed"));
                    analysees.computeIfAbsent(nom, k -> new LinkedHashSet<>()).add(uuid);
                    if (couv > 0) {
                        couvrantes.computeIfAbsent(nom, k -> new LinkedHashSet<>()).add(uuid);
                    }
                    Map<String, Object> f = parClasse.get(nom);
                    if (f == null || entier(f.get("instructionsCouvertes")) < couv) {
                        f = f == null ? new LinkedHashMap<>() : f;
                        f.put("classe", nom.replace('/', '.'));
                        f.put("fichier", c.get("source"));
                        f.put("instructionsCouvertes", couv);
                        f.put("instructionsTotal", total);
                        f.put("pct", pourcent(couv, total));
                        parClasse.put(nom, f);
                    }
                }
            }
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> e : parClasse.entrySet()) {
            Set<String> qui = couvrantes.getOrDefault(e.getKey(), Set.of());
            Map<String, Object> f = fait(qui.isEmpty() ? "classe.jamais_executee" : "classe");
            f.putAll(e.getValue());
            // Triées : sans cela, deux campagnes des mêmes exécutions donnent des lignes
            // différentes selon l'ordre de lecture des répertoires, et plus rien ne se
            // compare — or comparer deux rapports est exactement ce qu'on veut pouvoir faire.
            f.put("executionsCouvrantes", triees(qui));
            f.put("executionsAnalysee", triees(analysees.getOrDefault(e.getKey(), Set.of())));
            Object cle = e.getValue().get("fichier");
            f.put("sourceDisponible", cle != null && index.parCle().containsKey(String.valueOf(cle)));
            f.put("mesure", "jacoco.instructions");
            out.add(f);
        }
        return out;
    }

    /** Les méthodes les plus coûteuses d'une exécution, si elle a un profil. */
    static List<Map<String, Object>> methodesChaudes(Map<?, ?> run) {
        if (!(run.get("calltree") instanceof Map<?, ?> arbre)) return List.of();
        long total = entier(arbre.get("total"));
        if (total <= 0) return List.of();

        Map<String, Long> poids = new LinkedHashMap<>();
        cumuler(arbre, poids);
        List<Map<String, Object>> out = new ArrayList<>();
        poids.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(MAX_METHODES_CHAUDES)
                .forEach(e -> {
                    Map<String, Object> f = fait("methode.chaude");
                    f.put("execution", run.get("uuid"));
                    f.put("methode", e.getKey().replace('/', '.'));
                    f.put("releves", e.getValue());
                    f.put("pct", pourcent(e.getValue(), total));
                    f.put("mesure", "async-profiler.echantillons");
                    out.add(f);
                });
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void cumuler(Map<?, ?> noeud, Map<String, Long> poids) {
        Object enfants = noeud.get("children");
        if (!(enfants instanceof List<?> liste)) return;
        for (Object k : liste) {
            if (!(k instanceof Map<?, ?> kid)) continue;
            poids.merge(String.valueOf(kid.get("name")), entier(kid.get("total")), Long::sum);
            cumuler(kid, poids);
        }
    }

    // -------------------------------------------------------------- ce qu'on n'a pas pu lire

    /** Les fichiers mesurés dont on n'a pas le code : la cause n°1 d'un rapport décevant. */
    static List<Map<String, Object>> sourcesManquantes(List<Object> runs, Sources.Index index) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String cle : Diagnostic.mesures(runs)) {
            if (index.parCle().containsKey(cle)) continue;
            if (out.size() >= MAX_SOURCES_MANQUANTES) break;
            Map<String, Object> f = fait("source.introuvable");
            f.put("cle", cle);
            f.put("consequence", "la couverture de ce fichier est connue, son code ne l'est "
                    + "pas : la vue de code ne peut rien montrer");
            f.put("remede", "ajouter la racine qui contient ce paquet à SOURCE_DIRS "
                    + "— voir les faits « piste.source »");
            out.add(f);
        }
        return out;
    }

    /** Les racines proposées, avec leur preuve : la seule information directement actionnable. */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> pistes(Map<String, Object> diagnostic) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!(diagnostic.get("rapprochement") instanceof Map<?, ?> r)) return out;
        if (!(r.get("pistes") instanceof List<?> liste)) return out;
        for (Object p : liste) {
            if (!(p instanceof Map<?, ?> piste)) continue;
            Map<String, Object> f = fait("piste.source");
            f.putAll((Map<String, Object>) piste);
            out.add(f);
        }
        return out;
    }

    // ------------------------------------------------------------------ menue monnaie

    private static List<String> triees(Set<String> s) {
        List<String> l = new ArrayList<>(s);
        l.sort(Comparator.naturalOrder());
        return l;
    }

    private static Map<String, Object> fait(String nom) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("fait", nom);
        return f;
    }

    private static Map<?, ?> paquets(Map<?, ?> run) {
        return run.get("packages") instanceof Map<?, ?> p ? p : Map.of();
    }

    private static List<Map<?, ?>> classesDe(Object classes) {
        List<Map<?, ?>> out = new ArrayList<>();
        if (classes instanceof List<?> liste) {
            for (Object c : liste) if (c instanceof Map<?, ?> m) out.add(m);
        }
        return out;
    }

    private static long releves(Map<?, ?> run) {
        if (run.get("calltree") instanceof Map<?, ?> arbre) return entier(arbre.get("total"));
        return entier(run.get("mesures"));
    }

    private static boolean vide(Object o) {
        return o == null || (o instanceof Map<?, ?> m && m.isEmpty());
    }

    private static long entier(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    private static double pourcent(long part, long total) {
        if (total <= 0) return 0;
        return Math.round(1000.0 * part / total) / 10.0;
    }

    /** L'ordre des faits, pour qui trie : le plus général d'abord. */
    static final Comparator<Map<String, Object>> ORDRE =
            Comparator.comparing(f -> String.valueOf(f.get("fait")));
}
