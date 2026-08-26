package lab.xray.report;

import lab.xray.json.Json;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Les données du rapport, sorties de la page et rangées là où elles appartiennent.
 *
 * <p>Une page qui porte tout ne s'ouvre plus passé une certaine taille : 217 Mo constatés le
 * 26 août 2026, que Firefox a renoncé à afficher. Or on ne regarde jamais tout — on ouvre une
 * classe, puis une autre, et on coche deux exécutions sur dix. Ce qui doit être là au premier
 * affichage tient en quelques dizaines de kilo-octets ; le reste attend d'être demandé.
 *
 * <h2>Où va quoi</h2>
 *
 * <pre>
 * runtime-xray-out/
 *   index.html                      la vue
 *   diagnostic.json                 ce qui explique un rapport décevant
 *   runs/&lt;exécution&gt;/            LA CAPTURE, inchangée — jacoco/, arthas/, run-context.json
 *   runs/&lt;exécution&gt;/vue/       ce qui se déduit de CETTE exécution, et d'elle seule
 *       couverture.js  arbre.js  valeurs.js  traces.js
 *   vue/                            ce qui CROISE les exécutions, donc n'appartient à aucune
 *       sources/&lt;paquet&gt;.js      le code, groupé par paquet
 *       cumul.js                    par ligne, quelles exécutions l'ont couverte
 *       poids.js                    par méthode, son temps dans chaque exécution
 *       manifeste.json              ce que ce répertoire contient, en clair
 * </pre>
 *
 * <p>La règle est simple et elle décide de tout : <b>ce qui se déduit d'une exécution vit
 * sous elle</b>, ce qui en croise plusieurs vit au-dessus. Copier un répertoire d'exécution
 * emporte donc tout ce qui la concerne ; retirer une exécution ne laisse pas d'orphelin
 * ailleurs, sauf les index croisés — qu'un réassemblage refait en quelques secondes.
 *
 * <h2>Le format, et pourquoi celui-là</h2>
 *
 * <p>Chaque bloc est un fichier {@code .js} dont <b>chaque ligne est un enregistrement</b> :
 *
 * <pre>XR.bloc("src","org/exemple/module/Application.java",["package org.exemple.module;","…"]);</pre>
 *
 * <p>Trois propriétés, et il les faut toutes :
 * <ul>
 *   <li><b>lisible en ASCII, à la ligne de commande</b> — une ligne par enregistrement, la
 *       clé en tête, donc {@code grep}, {@code wc -l} et {@code head} y travaillent
 *       directement. Retirer l'enrobage donne du JSON pur pour {@code jq} :
 *       {@code sed 's/^XR.bloc(//; s/);$//'} ;</li>
 *   <li><b>chargeable depuis {@code file://}</b> — c'est du JavaScript, donc un
 *       {@code <script src>} l'accepte. Un {@code fetch} sur un {@code .json} voisin, non :
 *       le navigateur le refuse au titre de l'origine, et le rapport ne s'ouvrirait plus en
 *       double-cliquant dessus ;</li>
 *   <li><b>libérable</b> — un bloc chargé peut être oublié : la page retire son script et
 *       efface son entrée, ce qu'un littéral embarqué dans la page ne permet jamais.</li>
 * </ul>
 *
 * <p>Ces fichiers sont une <b>projection pour l'affichage</b> : engendrés, jetables,
 * reconstructibles d'un {@code --report-only}. Jamais une source de vérité de plus — celle-ci
 * reste la capture, sous {@code runs/}.
 */
public final class Blocs {

    /** Le répertoire des index qui croisent les exécutions, à côté de la page. */
    public static final String GLOBAL = "vue";

    /** Le sous-répertoire d'index, sous chaque exécution. */
    public static final String PAR_EXECUTION = "vue";

    /**
     * Le format du rapport engendré — la page et ses blocs.
     *
     * <p>Distinct du {@link Capture format de capture} : celui-ci porte sur ce que l'outil
     * <b>produit</b>, celui-là sur ce qu'une exécution <b>a enregistré</b>. Le premier se
     * jette et se reconstruit ; le second coûterait une campagne.
     *
     * <p>La page l'exige et refuse ce qu'elle ne connaît pas. C'est délibéré : la vue n'a pas
     * à porter les formes d'hier, puisque les reconstruire ne coûte rien. C'est le format de
     * capture qui protège ce qui coûte, et lui seul.
     */
    public static final String FORMAT = "2.0";

    /** Ce qui ne s'affiche qu'après un geste, et n'a donc pas à être là avant. */
    private static final List<String> LOURD =
            List.of("coverage", "calltree", "values", "trace");

    /** Le nom de fichier de chaque donnée lourde, sous {@code <exécution>/vue/}. */
    private static final Map<String, String> FICHIERS = Map.of(
            "coverage", "couverture.js",
            "calltree", "arbre.js",
            "values", "valeurs.js",
            "trace", "traces.js");

    private Blocs() {}

    /**
     * Écrit les blocs, et rend le sommaire — ce qui doit être présent au premier affichage.
     *
     * @param commonDir le répertoire de sortie
     * @param runs      les exécutions telles que la vue les reçoit
     * @param sources   les sources affichables, par clé {@code paquet/Fichier.java}
     */
    public static Map<String, Object> ecrire(Path commonDir, List<Object> runs,
                                             Map<String, Object> sources) throws IOException {
        Path global = commonDir.resolve(GLOBAL);
        Files.createDirectories(global);

        List<Object> sommaire = new ArrayList<>();
        for (Object r : runs) {
            if (!(r instanceof Map<?, ?> run)) continue;
            String sousRep = String.valueOf(run.get("chemin")) + PAR_EXECUTION + "/";
            Path dir = commonDir.resolve(sousRep);
            Files.createDirectories(dir);
            sommaire.add(alleger(run, sousRep));
            ecrireRun(dir, run);
        }

        Map<String, String> blocParCle = ecrireSources(global.resolve("sources"), sources);
        ecrireCumul(global.resolve("cumul.js"), runs);
        ecrirePoids(global.resolve("poids.js"), runs);
        ecrirePresence(global.resolve("presence.js"), runs);
        ecrireManifeste(global.resolve("manifeste.json"), runs, sources.size());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("format", FORMAT);
        out.put("runs", sommaire);
        // Les CLÉS des sources restent dans la page, jamais leur contenu : l'arbre doit
        // pouvoir dire « celle-ci a son code » sans charger le code pour le savoir, sinon la
        // première image redemanderait tout ce qu'on vient d'en sortir.
        out.put("sourcesDisponibles", blocParCle);
        out.put("global", GLOBAL + "/");
        return out;
    }

    /**
     * Ce qu'une exécution garde dans la page, et ce qu'elle envoie sous son répertoire.
     *
     * <p>La frontière n'est pas arbitraire : reste ce que la <b>première image</b> montre —
     * l'identité de l'exécution, ses rapports, et l'arbre des classes avec leurs compteurs.
     * Part ce qui ne s'affiche qu'une fois une classe ouverte ou une exécution cochée.
     */
    static Map<String, Object> alleger(Map<?, ?> run, String sousRep) {
        Map<String, Object> leger = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : run.entrySet()) {
            String cle = String.valueOf(e.getKey());
            if (!LOURD.contains(cle)) leger.put(cle, e.getValue());
        }
        List<Object> blocs = new ArrayList<>();
        for (String cle : LOURD) if (run.get(cle) != null) blocs.add(sousRep + FICHIERS.get(cle));
        leger.put("blocs", blocs);
        return leger;
    }

    private static void ecrireRun(Path dir, Map<?, ?> run) throws IOException {
        String uuid = String.valueOf(run.get("uuid"));
        for (String cle : LOURD) {
            Object valeur = run.get(cle);
            if (valeur == null) continue;
            try (BufferedWriter w = Files.newBufferedWriter(dir.resolve(FICHIERS.get(cle)),
                    StandardCharsets.UTF_8)) {
                ligne(w, "run", uuid + "/" + cle, valeur);
            }
        }
    }

    /**
     * Les sources, groupées par paquet.
     *
     * <p>Un fichier par source ferait des milliers de fichiers — pénible à copier, lent sur
     * un partage réseau. Un fichier par paquet garde des blocs de quelques dizaines de
     * kilo-octets, qui est la granularité à laquelle on navigue : on ouvre rarement une
     * classe sans regarder ses voisines.
     */
    private static Map<String, String> ecrireSources(Path dir, Map<String, Object> sources)
            throws IOException {
        Files.createDirectories(dir);
        Map<String, Map<String, Object>> parPaquet = new TreeMap<>();
        for (Map.Entry<String, Object> e : sources.entrySet()) {
            String cle = e.getKey();
            int i = cle.lastIndexOf('/');
            String paquet = i < 0 ? "(defaut)" : cle.substring(0, i);
            parPaquet.computeIfAbsent(paquet, k -> new LinkedHashMap<>()).put(cle, e.getValue());
        }
        Map<String, String> blocParCle = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> e : parPaquet.entrySet()) {
            String nom = nomDeFichier(e.getKey()) + ".js";
            try (BufferedWriter w = Files.newBufferedWriter(dir.resolve(nom),
                    StandardCharsets.UTF_8)) {
                for (Map.Entry<String, Object> f : e.getValue().entrySet()) {
                    ligne(w, "src", f.getKey(), f.getValue());
                    blocParCle.put(f.getKey(), GLOBAL + "/sources/" + nom);
                }
            }
        }
        return blocParCle;
    }

    /**
     * L'index du cumul : pour chaque ligne, <b>quelles exécutions</b> l'ont couverte.
     *
     * <p>Sans lui, réunir les exécutions cochées demande, pour chaque ligne affichée, de
     * consulter la couverture de chacune — dix exécutions, dix recherches par ligne, refaites
     * à chaque changement de case. C'est un parcours en {@code lignes × exécutions}, et il
     * obligerait à charger toutes les exécutions pour en afficher une.
     *
     * <p>On le calcule donc une fois, sous forme de <b>masques de bits</b> : le bit <i>i</i>
     * dit que l'exécution <i>i</i> a couvert cette ligne. Réunir un sous-ensemble devient un
     * ET binaire — une opération par ligne, quel que soit le nombre d'exécutions. Deux
     * masques, parce que « couverte » et « partiellement couverte » ne se confondent pas.
     */
    private static void ecrireCumul(Path fichier, List<Object> runs) throws IOException {
        Map<String, Map<Integer, int[]>> index = new TreeMap<>();
        int bit = 0;
        for (Object r : runs) {
            if (!(r instanceof Map<?, ?> run)) continue;
            if (bit > 30) break;   // au-delà, le masque déborde : on s'arrête plutôt que mentir
            int drapeau = 1 << bit++;
            if (!(run.get("coverage") instanceof Map<?, ?> couverture)) continue;
            for (Map.Entry<?, ?> f : couverture.entrySet()) {
                if (!(f.getValue() instanceof Map<?, ?> lignes)) continue;
                Map<Integer, int[]> parLigne =
                        index.computeIfAbsent(String.valueOf(f.getKey()), k -> new TreeMap<>());
                for (Map.Entry<?, ?> l : lignes.entrySet()) {
                    if (!(l.getValue() instanceof Map<?, ?> etat)) continue;
                    String s = String.valueOf(etat.get("s"));
                    if ("miss".equals(s)) continue;
                    int[] masques = parLigne.computeIfAbsent(entier(l.getKey()), k -> new int[2]);
                    masques["full".equals(s) ? 0 : 1] |= drapeau;
                }
            }
        }
        try (BufferedWriter w = Files.newBufferedWriter(fichier, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, Map<Integer, int[]>> f : index.entrySet()) {
                List<Object> plat = new ArrayList<>();
                for (Map.Entry<Integer, int[]> l : f.getValue().entrySet()) {
                    plat.add(List.of(l.getKey(), l.getValue()[0], l.getValue()[1]));
                }
                ligne(w, "cumul", f.getKey(), plat);
            }
        }
    }

    /**
     * L'index des poids : pour chaque méthode, son temps dans chaque exécution.
     *
     * <p>La vue d'ensemble classe les méthodes les plus coûteuses <b>toutes exécutions
     * cochées confondues</b>. Le calculer à l'affichage demande de parcourir l'arbre d'appel
     * de chacune — donc de les charger toutes, pour une liste de six lignes. C'est le
     * parcours croisé qui coûte le plus cher, et le seul qui empêchait de ne charger que ce
     * qu'on regarde.
     *
     * <p>Une ligne par méthode, un nombre par exécution, dans l'ordre du sommaire.
     */
    private static void ecrirePoids(Path fichier, List<Object> runs) throws IOException {
        Map<String, long[]> poids = new TreeMap<>();
        int n = runs.size(), i = 0;
        for (Object r : runs) {
            if (!(r instanceof Map<?, ?> run)) { i++; continue; }
            final int rang = i++;
            if (run.get("calltree") instanceof Map<?, ?> arbre) {
                parcourir(arbre, (nom, total) ->
                        poids.computeIfAbsent(nom, k -> new long[n])[rang] += total);
            }
        }
        try (BufferedWriter w = Files.newBufferedWriter(fichier, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, long[]> e : poids.entrySet()) {
                List<Object> valeurs = new ArrayList<>();
                for (long v : e.getValue()) valeurs.add(v);
                ligne(w, "poids", e.getKey(), valeurs);
            }
        }
    }

    /** Descend un arbre d'appel, en annonçant chaque nœud et son total. */
    private static void parcourir(Map<?, ?> noeud, java.util.function.BiConsumer<String, Long> vu) {
        if (!(noeud.get("children") instanceof Iterable<?> enfants)) return;
        for (Object e : enfants) {
            if (!(e instanceof Map<?, ?> enfant)) continue;
            Object nom = enfant.get("name");
            Object total = enfant.get("total");
            if (nom != null && total instanceof Number t) vu.accept(String.valueOf(nom), t.longValue());
            parcourir(enfant, vu);
        }
    }

    /**
     * L'index de présence : pour chaque méthode, en combien d'endroits chaque exécution
     * l'appelle.
     *
     * <p>C'est ce que la vue affiche sous « aussi appelée dans » : elle doit savoir, pour une
     * méthode ouverte, quelles <b>autres</b> exécutions la traversent et combien de fois. Le
     * compter à l'affichage demande de descendre l'arbre d'appel de chacune — donc de charger
     * toutes les exécutions pour en regarder une seule.
     *
     * <p>Le compte suffit à l'affichage ; le chemin lui-même n'est lu qu'au clic, et alors
     * l'exécution visée est chargée pour de bon. On sépare ainsi ce qui se montre toujours de
     * ce qui se demande rarement.
     */
    private static void ecrirePresence(Path fichier, List<Object> runs) throws IOException {
        Map<String, int[]> presence = new TreeMap<>();
        int n = runs.size(), i = 0;
        for (Object r : runs) {
            if (!(r instanceof Map<?, ?> run)) { i++; continue; }
            final int rang = i++;
            if (run.get("calltree") instanceof Map<?, ?> arbre) {
                parcourir(arbre, (nom, total) ->
                        presence.computeIfAbsent(nom, k -> new int[n])[rang]++);
            }
        }
        try (BufferedWriter w = Files.newBufferedWriter(fichier, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, int[]> e : presence.entrySet()) {
                List<Object> valeurs = new ArrayList<>();
                for (int v : e.getValue()) valeurs.add(v);
                ligne(w, "presence", e.getKey(), valeurs);
            }
        }
    }

    /**
     * Ce que le répertoire contient, en clair.
     *
     * <p>Un dossier de rapport se recopie d'un poste à l'autre, se dézippe six mois plus tard,
     * se retrouve amputé d'un répertoire. Un manifeste lisible dit alors ce qu'il devrait y
     * avoir — sans lui, il faut ouvrir la page pour découvrir ce qui manque.
     */
    private static void ecrireManifeste(Path fichier, List<Object> runs, int sources)
            throws IOException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("format", FORMAT);
        m.put("formatCaptureMinimal", Capture.MINIMALE);
        List<Object> executions = new ArrayList<>();
        for (Object r : runs) {
            if (!(r instanceof Map<?, ?> run)) continue;
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("nom", run.get("nom"));
            e.put("uuid", run.get("uuid"));
            e.put("chemin", run.get("chemin"));
            e.put("vue", String.valueOf(run.get("chemin")) + PAR_EXECUTION + "/");
            executions.add(e);
        }
        m.put("executions", executions);
        m.put("sourcesAffichables", sources);
        m.put("indexGlobaux", List.of("cumul.js", "poids.js", "presence.js", "sources/"));
        Files.writeString(fichier, Json.write(m), StandardCharsets.UTF_8);
    }

    /** Un enregistrement, une ligne — c'est cette règle qui rend le fichier greppable. */
    private static void ligne(BufferedWriter w, String type, String cle, Object valeur)
            throws IOException {
        w.write("XR.bloc(");
        w.write(Json.write(type));
        w.write(",");
        w.write(Json.write(cle));
        w.write(",");
        w.write(Json.write(valeur));
        w.write(");\n");
    }

    /**
     * Un nom de paquet transformé en nom de fichier sûr.
     *
     * <p>Les paquets ne contiennent que des lettres, des chiffres et des points, mais une clé
     * inattendue ne doit pas pouvoir écrire ailleurs que dans le répertoire prévu — d'où le
     * filtre, et non une simple substitution.
     */
    static String nomDeFichier(String paquet) {
        StringBuilder sb = new StringBuilder();
        for (char c : paquet.toCharArray()) {
            sb.append(Character.isLetterOrDigit(c) ? c : '-');
        }
        String s = sb.toString().replaceAll("-+", "-").replaceAll("(^-|-$)", "");
        return s.isEmpty() ? "divers" : s;
    }

    private static int entier(Object o) {
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
