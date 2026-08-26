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
 * Les données du rapport, sorties de la page et écrites en blocs chargeables à la demande.
 *
 * <p>Une page qui porte tout ne s'ouvre plus passé une certaine taille : 217 Mo constatés le
 * 26 août 2026, que Firefox a renoncé à afficher. Or on ne regarde jamais tout — on ouvre une
 * classe, puis une autre, et on coche deux exécutions sur dix. Ce qui doit être présent au
 * premier affichage tient en quelques dizaines de kilo-octets ; le reste peut attendre d'être
 * demandé.
 *
 * <h2>Le format, et pourquoi celui-là</h2>
 *
 * <p>Chaque bloc est un fichier {@code .js} dont <b>chaque ligne est un enregistrement</b> :
 *
 * <pre>XR.bloc("src","org/exemple/module/Application.java",["package org.exemple.module;","…"]);</pre>
 *
 * <p>Trois propriétés, et il les faut toutes :
 * <ul>
 *   <li><b>lisible en ASCII, à la ligne de commande</b> — une ligne par enregistrement, donc
 *       {@code grep}, {@code wc -l}, {@code head} et {@code cut} y travaillent directement.
 *       Retirer l'enrobage donne du JSON pur pour {@code jq} :
 *       {@code sed 's/^XR.bloc(//; s/);$//'} ;</li>
 *   <li><b>chargeable depuis {@code file://}</b> — c'est du JavaScript, donc un
 *       {@code <script src>} l'accepte. Un {@code fetch} sur un {@code .json} voisin, non :
 *       le navigateur le refuse au titre de l'origine, et le rapport ne s'ouvrirait plus en
 *       double-cliquant dessus ;</li>
 *   <li><b>libérable</b> — un bloc chargé peut être oublié : la page retire son script et
 *       efface son entrée, ce qu'un gros littéral embarqué dans la page ne permet jamais.</li>
 * </ul>
 *
 * <p>Le JSON reste la vérité écrite : {@code diagnostic.json} et les sorties des outils ne
 * changent pas. Ces blocs en sont une <b>projection pour l'affichage</b>, engendrée, jetable
 * et reconstructible — jamais une source de vérité de plus.
 */
public final class Blocs {

    /** Le répertoire des blocs, à côté de la page. */
    public static final String REPERTOIRE = "donnees";

    private Blocs() {}

    /**
     * Écrit les blocs, et rend le sommaire — ce qui doit être présent au premier affichage.
     *
     * @param commonDir le répertoire de sortie, où {@code donnees/} est créé
     * @param runs      les exécutions telles que la vue les reçoit
     * @param sources   les sources affichables, par clé {@code paquet/Fichier.java}
     * @return le sommaire : la même forme que la page attend, mais allégée de tout ce qui
     *         part en blocs, chaque exécution portant le nom de son bloc
     */
    public static Map<String, Object> ecrire(Path commonDir, List<Object> runs,
                                             Map<String, Object> sources) throws IOException {
        Path dir = commonDir.resolve(REPERTOIRE);
        Files.createDirectories(dir);

        List<Object> sommaire = new ArrayList<>();
        int n = 0;
        for (Object r : runs) {
            if (!(r instanceof Map<?, ?> run)) continue;
            String bloc = "run-" + (++n) + ".js";
            sommaire.add(alleger(run, bloc));
            ecrireRun(dir.resolve(bloc), run);
        }
        ecrireSources(dir, sources);
        return Map.of("runs", sommaire);
    }

    /**
     * Ce qu'une exécution garde dans la page, et ce qu'elle envoie en bloc.
     *
     * <p>La frontière n'est pas arbitraire : reste ce que la <b>première image</b> montre —
     * l'identité de l'exécution, ses rapports, et l'arbre des classes avec leurs compteurs.
     * Part ce qui ne s'affiche qu'une fois une classe ouverte ou une exécution cochée : la
     * couverture ligne à ligne, l'arbre d'appel, les valeurs relevées, les traces.
     */
    static Map<String, Object> alleger(Map<?, ?> run, String bloc) {
        Map<String, Object> leger = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : run.entrySet()) {
            String cle = String.valueOf(e.getKey());
            if (!LOURD.contains(cle)) leger.put(cle, e.getValue());
        }
        leger.put("bloc", REPERTOIRE + "/" + bloc);
        return leger;
    }

    /** Ce qui ne s'affiche qu'après un geste, et n'a donc pas à être là avant. */
    private static final List<String> LOURD =
            List.of("coverage", "calltree", "values", "trace");

    private static void ecrireRun(Path fichier, Map<?, ?> run) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(fichier, StandardCharsets.UTF_8)) {
            String uuid = String.valueOf(run.get("uuid"));
            for (String cle : LOURD) {
                Object valeur = run.get(cle);
                if (valeur != null) ligne(w, "run", uuid + "/" + cle, valeur);
            }
        }
    }

    /**
     * Les sources, groupées par paquet.
     *
     * <p>Un fichier par source ferait des milliers de fichiers — pénible à copier, lent sur
     * un partage réseau, et illisible en ligne de commande. Un fichier par paquet garde des
     * blocs de quelques dizaines de kilo-octets, qui est la granularité à laquelle on
     * navigue : on ouvre rarement une classe sans regarder ses voisines.
     */
    private static void ecrireSources(Path dir, Map<String, Object> sources) throws IOException {
        Map<String, Map<String, Object>> parPaquet = new TreeMap<>();
        for (Map.Entry<String, Object> e : sources.entrySet()) {
            String cle = e.getKey();
            int i = cle.lastIndexOf('/');
            String paquet = i < 0 ? "(defaut)" : cle.substring(0, i);
            parPaquet.computeIfAbsent(paquet, k -> new LinkedHashMap<>()).put(cle, e.getValue());
        }
        for (Map.Entry<String, Map<String, Object>> e : parPaquet.entrySet()) {
            Path fichier = dir.resolve("src-" + nomDeFichier(e.getKey()) + ".js");
            try (BufferedWriter w = Files.newBufferedWriter(fichier, StandardCharsets.UTF_8)) {
                for (Map.Entry<String, Object> f : e.getValue().entrySet()) {
                    ligne(w, "src", f.getKey(), f.getValue());
                }
            }
        }
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
     * <p>Les paquets ne contiennent que des lettres, des chiffres et des points, mais un
     * fichier sans paquet ou une clé inattendue ne doit pas pouvoir écrire ailleurs que dans
     * {@code donnees/} — d'où le filtre, et non une simple substitution.
     */
    static String nomDeFichier(String paquet) {
        StringBuilder sb = new StringBuilder();
        for (char c : paquet.toCharArray()) {
            sb.append(Character.isLetterOrDigit(c) ? c : '-');
        }
        String s = sb.toString().replaceAll("-+", "-").replaceAll("(^-|-$)", "");
        return s.isEmpty() ? "divers" : s;
    }
}
