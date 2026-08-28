package lab.xray;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import lab.xray.json.Json;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le fil d'exécution existe pour un cas précis : l'outil lancé au fond de scripts
 * imbriqués, dont la sortie part dans un tuyau, et où <b>personne ne voit plus rien</b>.
 * Ces tests gardent ce qui rend ce cas jouable — le fichier toujours écrit, à un chemin
 * qu'on connaît, dont chaque ligne se comprend seule, et dont la dernière dit qu'on peut
 * arrêter de regarder.
 */
class SuiviTest {

    private static List<Map<String, Object>> lire(Path dossier) throws Exception {
        Path f = dossier.resolve(Suivi.FICHIER);
        assertTrue(Files.exists(f), "le fil doit être écrit sans qu'on le demande : " + f);
        List<Map<String, Object>> out = new ArrayList<>();
        for (String ligne : Files.readAllLines(f, StandardCharsets.UTF_8)) {
            if (ligne.isBlank()) continue;
            assertFalse(ligne.contains("\n"), "une ligne, un relevé : « tail -f » en dépend");
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) Json.read(ligne);
            out.add(m);
        }
        return out;
    }

    @Test
    @DisplayName("Le fil s'écrit sans serveur : c'est le cas sans terminal qui le justifie")
    void theTrailIsWrittenWithoutAnyServer(@TempDir Path dir) throws Exception {
        try (Suivi suivi = Suivi.ouvrir(dir, dir.resolve("runs/essai"), "essai", "java -jar app.jar", 0)) {
            suivi.avancement(Duration.ofSeconds(1), Duration.ofMillis(900), 120, 0.9);
            suivi.avancement(Duration.ofSeconds(2), Duration.ofMillis(1800), 340, 0.9);
            suivi.fin("terminée", 2);
        }
        List<Map<String, Object>> lignes = lire(dir);
        assertEquals(List.of("start", "progress", "progress", "end"),
                lignes.stream().map(l -> l.get("event")).toList());
    }

    @Test
    @DisplayName("Chaque ligne se comprend seule, et la dernière dit qu'on peut arrêter")
    void eachLineStandsAloneAndTheLastOneSaysStop(@TempDir Path dir) throws Exception {
        try (Suivi suivi = Suivi.ouvrir(dir, dir.resolve("runs/essai"), "recette 1", "java -jar app.jar", 0)) {
            suivi.avancement(Duration.ofSeconds(7), Duration.ofSeconds(12), 4096, 1.7);
            suivi.fin("terminée", 7);
        }
        List<Map<String, Object>> lignes = lire(dir);
        for (Map<String, Object> l : lignes) {
            // Sans le nom de l'exécution sur chaque ligne, un fil qui accumule plusieurs
            // exécutions — ce qu'il fait — devient impossible à relire.
            assertEquals("recette 1", l.get("run"));
            assertTrue(l.containsKey("date"), "une ligne sans horodatage ne se recoupe pas");
        }
        assertEquals("java -jar app.jar", lignes.get(0).get("command"),
                "sans la commande, une exécution qui avance ne dit pas laquelle avance");
        Map<String, Object> avancement = lignes.get(1);
        assertEquals(7L, ((Number) avancement.get("seconds")).longValue());
        assertEquals(1.7, ((Number) avancement.get("cores")).doubleValue(), 0.001);
        assertEquals(4, ((Number) avancement.get("level")).intValue(),
                "1,7 cœur, c'est le palier le plus dense — le même seuil que la bande");
        assertEquals("end", lignes.get(lignes.size() - 1).get("event"),
                "un lecteur qui suit le fichier doit savoir quand s'arrêter");
    }

    @Test
    @DisplayName("Le palier écrit est celui de la bande, jamais un second calcul")
    void theWrittenLevelIsTheOneFromTheBand(@TempDir Path dir) throws Exception {
        // Deux calculs de charge finiraient par diverger, et le fichier contredirait le
        // terminal sans que rien ne le signale. Le palier vient donc de Progression.
        try (Suivi suivi = Suivi.ouvrir(dir, dir.resolve("runs/x"), "x", "", 0)) {
            for (double coeurs : new double[]{0.0, 0.05, 0.3, 1.0, 3.0}) {
                suivi.avancement(Duration.ofSeconds(1), Duration.ZERO, 0, coeurs);
            }
        }
        List<Integer> paliers = lire(dir).stream()
                .filter(l -> "progress".equals(l.get("event")))
                .map(l -> ((Number) l.get("level")).intValue()).toList();
        assertEquals(List.of(0, 1, 2, 3, 4), paliers);
    }

    @Test
    @DisplayName("Un échec d'écriture n'emporte jamais la mesure, qui est ce qu'on est venu chercher")
    void aFailedWriteNeverTakesTheMeasurementDown(@TempDir Path dir) throws Exception {
        // Un chemin impossible : un fichier là où il faudrait un répertoire.
        Path barre = dir.resolve("barre");
        Files.writeString(barre, "pas un répertoire", StandardCharsets.UTF_8);
        try (Suivi suivi = Suivi.ouvrir(barre.resolve("dedans"), dir, "essai", "", 0)) {
            suivi.avancement(Duration.ofSeconds(1), Duration.ZERO, 0, 0.5);
            suivi.fin("terminée", 1);
        }
        // Rien n'a été écrit, et surtout : rien n'a été levé.
        assertFalse(Files.exists(barre.resolve("dedans").resolve(Suivi.FICHIER)));
    }

    @Test
    @DisplayName("La fin du journal est bornée : l'affichage ne se paie pas sur la mesure")
    void theLogTailIsBounded(@TempDir Path dir) throws Exception {
        StringBuilder gros = new StringBuilder();
        while (gros.length() < 300_000) gros.append("une ligne de journal bien ordinaire\n");
        Path journal = dir.resolve("execution.log");
        Files.writeString(journal, gros.toString(), StandardCharsets.UTF_8);

        byte[] queue = Suivi.queue(journal);
        assertTrue(queue.length <= 64 * 1024,
                "une application bavarde écrit des dizaines de mégaoctets : les recopier à "
                + "chaque rafraîchissement ferait payer l'affichage à la machine qui mesure");
        assertTrue(queue.length > 60_000, "et il faut quand même de quoi lire la fin");
        assertEquals(0, Suivi.queue(dir.resolve("jamais-ecrit.log")).length,
                "avant que l'application n'écrive, il n'y a rien, et ce n'est pas une erreur");
    }

    @Test
    @DisplayName("La page voyage dans le jar : elle doit servir sur une machine sans réseau")
    void thePageTravelsInsideTheJar() throws Exception {
        String page = new String(Suivi.page(), StandardCharsets.UTF_8);
        assertTrue(page.contains("progression.jsonl"), "elle relit le fil, elle n'en crée pas");
        assertFalse(page.contains("http://") && page.contains("cdn"),
                "aucune dépendance servie de l'extérieur : la machine visée n'a pas de réseau");
        assertFalse(page.contains("<script src="),
                "rien à aller chercher : la page doit s'afficher entière ou pas du tout");
        assertTrue(page.contains("cœurs occupés"),
                "le compte est en cœurs, pas en part de machine — et la page doit le dire, "
                + "sinon on lit une saturation là où il y a un cœur sur trente-deux");
    }

    @Test
    @DisplayName("Un journal qui n'est pas en UTF-8 est lu quand même, et le repli est dit")
    void aLogThatIsNotUtf8IsStillReadAndTheFallbackIsStated() {
        // L'outil écrit en UTF-8 ; l'application observée écrit dans ce que sa JVM lui a
        // donné, et sur le parc visé c'est souvent CP1252 ou CP850. Servi tel quel en UTF-8,
        // « démarrage terminé » devient du charabia sur la moitié des journaux français.
        byte[] latin1 = "démarrage terminé".getBytes(StandardCharsets.ISO_8859_1);
        String rendu = new String(Suivi.enUtf8(latin1), StandardCharsets.UTF_8);
        assertTrue(rendu.contains("démarrage terminé"), "les accents doivent revenir");
        assertTrue(rendu.contains("ISO-8859-1"),
                "deviner est acceptable ici, le taire ne l'est pas : un lecteur qui voit un "
                + "caractère douteux doit savoir que c'est une interprétation");

        // De l'UTF-8 valide n'est jamais touché — pas de repli, pas d'avertissement.
        byte[] utf8 = "démarrage terminé".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(utf8, Suivi.enUtf8(utf8));
        assertEquals(0, Suivi.enUtf8(new byte[0]).length);
    }
}
