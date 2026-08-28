package lab.xray.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ces relevés sont le seul endroit du rapport où figurent des <b>valeurs</b>. Une erreur de
 * lecture y est particulièrement coûteuse : elle ne se voit pas — on affiche simplement une
 * valeur fausse, avec l'autorité d'une mesure. Deux bugs réels sont couverts ici : la
 * troncature des valeurs imbriquées, et la valeur de retour prise pour un paramètre.
 */
class InspectionTest {

    private static final String ESC = "\u001B";

    /** Un relevé conforme à ce que produit l'inspecteur, indentation comprise. */
    private static final String SAMPLE = """
            method=lab.sample.RoutePlanner.legMinutes location=AtExit
            ts=2026-08-20 21:08:16.440; [cost=1.240042ms] result=@ArrayList[
                @Object[][
                    @Leg[Leg[from=Auch, to=Foix, distanceKm=54.0]],
                    @CarSpeed[lab.sample.speed.CarSpeed@299a06ac],
                    @Mode[CAR],
                ],
                @Double[29.720971636363636],
            ]
            method=lab.sample.RoutePlanner.travelTimeMinutes location=AtExit
            ts=2026-08-20 21:08:16.466; [cost=63.983125ms] result=@ArrayList[
                @Object[][
                    @Trip[Trip[id=TRIP-112, mode=CAR]],
                ],
                @Double[64.0],
            ]
            """;

    private Path write(Path dir, String name, String content) throws IOException {
        Path f = dir.resolve(name);
        Files.writeString(f, content, StandardCharsets.UTF_8);
        return f;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstCall(Inspection in, String frame) {
        List<Object> calls = (List<Object>) in.values.get(frame);
        assertNotNull(calls, "aucune valeur pour " + frame + " — clés : " + in.values.keySet());
        return (Map<String, Object>) calls.get(0);
    }

    @Test
    @DisplayName("Les valeurs sont rattachées à CHAQUE méthode, pas à la classe")
    void groupsValuesByMethod(@TempDir Path dir) throws IOException {
        Inspection in = Inspection.read(write(dir, "v.txt", SAMPLE), dir.resolve("absent.txt"), 8);
        assertEquals(2, in.values.size(), "deux méthodes distinctes attendues");
        assertTrue(in.values.containsKey("lab/sample/RoutePlanner.legMinutes"));
        assertTrue(in.values.containsKey("lab/sample/RoutePlanner.travelTimeMinutes"));
    }

    @Test
    @DisplayName("Une valeur imbriquée n'est pas tronquée à son premier crochet fermant")
    void keepsNestedBracketsIntact(@TempDir Path dir) throws IOException {
        Inspection in = Inspection.read(write(dir, "v.txt", SAMPLE), dir.resolve("absent.txt"), 8);
        @SuppressWarnings("unchecked")
        List<Object> params = (List<Object>) firstCall(in, "lab/sample/RoutePlanner.legMinutes").get("params");
        assertEquals("Leg[from=Auch, to=Foix, distanceKm=54.0]",
                ((Map<?, ?>) params.get(0)).get("value"),
                "le crochet fermant de la valeur doit être conservé");
    }

    @Test
    @DisplayName("La valeur de retour est bien la valeur de retour, pas le dernier paramètre")
    void separatesReturnFromParameters(@TempDir Path dir) throws IOException {
        Inspection in = Inspection.read(write(dir, "v.txt", SAMPLE), dir.resolve("absent.txt"), 8);
        Map<String, Object> call = firstCall(in, "lab/sample/RoutePlanner.legMinutes");
        @SuppressWarnings("unchecked")
        List<Object> params = (List<Object>) call.get("params");
        assertEquals(3, params.size(), "trois paramètres attendus");
        assertEquals("CAR", ((Map<?, ?>) params.get(2)).get("value"));
        assertEquals("29.720971636363636", ((Map<?, ?>) call.get("retour")).get("value"));
    }

    @Test
    @DisplayName("Les codes couleur du terminal sont retirés")
    void stripsTerminalColours(@TempDir Path dir) throws IOException {
        String coloured = SAMPLE.replace("@Mode[CAR]", ESC + "[1;31m@Mode[CAR]" + ESC + "[0m");
        Inspection in = Inspection.read(write(dir, "v.txt", coloured), dir.resolve("a.txt"), 8);
        @SuppressWarnings("unchecked")
        List<Object> params = (List<Object>) firstCall(in, "lab/sample/RoutePlanner.legMinutes").get("params");
        assertEquals("CAR", ((Map<?, ?>) params.get(2)).get("value"));
    }

    @Test
    @DisplayName("Les méthodes synthétiques sont écartées : elles n'ont pas été écrites par un humain")
    void skipsSyntheticMethods(@TempDir Path dir) throws IOException {
        String withSynthetic = """
                method=lab.sample.RoutePlanner.$jacocoInit location=AtExit
                ts=2026-08-20 21:08:16.440; [cost=0.1ms] result=@ArrayList[
                    @Object[][
                        @boolean[][true, true],
                    ],
                    @boolean[][true],
                ]
                """ + SAMPLE;
        Inspection in = Inspection.read(write(dir, "v.txt", withSynthetic), dir.resolve("a.txt"), 8);
        assertFalse(in.values.containsKey("lab/sample/RoutePlanner.$jacocoInit"),
                "la sonde de couverture ne doit pas apparaître comme une méthode observée");
        assertEquals(2, in.values.size());
    }

    @Test
    @DisplayName("Le nombre d'appels conservés par méthode est borné")
    void limitsCallsPerMethod(@TempDir Path dir) throws IOException {
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            many.append(SAMPLE);
        }
        Inspection in = Inspection.read(write(dir, "v.txt", many.toString()), dir.resolve("a.txt"), 5);
        @SuppressWarnings("unchecked")
        List<Object> calls = (List<Object>) in.values.get("lab/sample/RoutePlanner.legMinutes");
        assertEquals(5, calls.size());
    }

    @Test
    @DisplayName("La trace associe chaque appel à sa ligne de code et à sa durée")
    void readsTraceLinesAndDurations(@TempDir Path dir) throws IOException {
        String trace = """
                `---ts=2026-08-20 20:10:59.049;thread_name=main;id=1
                    `---[0.416459ms] lab.sample.RoutePlanner:travelTimeMinutes()
                        +---[4,08% 0.017ms ] lab.sample.model.Trip:mode() #43
                        +---[13,35% 0.055583ms ] lab.sample.RoutePlanner:legMinutes() #50
                        `---[2,06% 0.008583ms ] lab.sample.comfort.Breaks:totalMinutes() #67
                """;
        Inspection in = Inspection.read(dir.resolve("absent.txt"), write(dir, "t.txt", trace), 8);
        assertEquals(3, in.trace.size());
        @SuppressWarnings("unchecked")
        List<Object> line50 = (List<Object>) in.trace.get("50");
        Map<?, ?> call = (Map<?, ?>) line50.get(0);
        assertEquals("RoutePlanner.legMinutes()", call.get("callee"));
        assertEquals(1, call.get("n"), "une seule observation ici");
        assertEquals(0.055583, (Double) call.get("minMs"), 1e-9);
        assertEquals(0.055583, (Double) call.get("maxMs"), 1e-9);
        assertEquals("lab/sample/RoutePlanner.legMinutes", call.get("frame"),
                "la frame doit suivre la convention paquet/Classe.methode pour être cliquable");
    }

    @Test
    @DisplayName("Plusieurs invocations d'une même ligne sont agrégées, pas empilées")
    void aggregatesRepeatedObservations(@TempDir Path dir) throws IOException {
        // Tracer plusieurs invocations est ce qui permet d'annoter plus de lignes — mais la
        // même ligne revient alors autant de fois. Empiler les doublons rendrait l'annotation
        // illisible ; on agrège, en gardant l'étendue des durées observées.
        String trace = """
                    +---[1,0% 0.010ms ] a.b.C:calculer() #12
                    +---[1,0% 0.030ms ] a.b.C:calculer() #12
                    +---[1,0% 0.020ms ] a.b.C:calculer() #12
                """;
        Inspection in = Inspection.read(dir.resolve("absent.txt"), write(dir, "t.txt", trace), 8);
        @SuppressWarnings("unchecked")
        List<Object> line = (List<Object>) in.trace.get("12");
        assertEquals(1, line.size(), "une seule entrée pour un même appelé");
        Map<?, ?> call = (Map<?, ?>) line.get(0);
        assertEquals(3, call.get("n"), "les trois observations sont comptées");
        assertEquals(0.010, (Double) call.get("minMs"), 1e-9);
        assertEquals(0.030, (Double) call.get("maxMs"), 1e-9);
    }

    @Test
    @DisplayName("Chaque passage d'une boucle est conservé, dans son ordre")
    void keepsEachIterationInOrder(@TempDir Path dir) throws IOException {
        // L'agrégat dirait « 3 fois, de 0,010 à 0,300 ms » et cacherait que c'est la
        // PREMIÈRE itération qui a tout coûté — souvent l'information qu'on cherchait.
        String trace = """
                    +---[1,0% 0.300ms ] a.b.C:calculer() #12
                    +---[1,0% 0.012ms ] a.b.C:calculer() #12
                    +---[1,0% 0.010ms ] a.b.C:calculer() #12
                """;
        Inspection in = Inspection.read(dir.resolve("absent.txt"), write(dir, "t.txt", trace), 8);
        @SuppressWarnings("unchecked")
        Map<String, Object> call = (Map<String, Object>) ((List<Object>) in.trace.get("12")).get(0);
        @SuppressWarnings("unchecked")
        List<Object> iterations = (List<Object>) call.get("passages");
        assertEquals(List.of(0.300, 0.012, 0.010), iterations,
                "l'ordre des itérations doit être celui de l'exécution");
        assertEquals(3, call.get("n"), "l'agrégat reste disponible à côté");
    }

    @Test
    @DisplayName("Une boucle très longue est bornée plutôt que de faire enfler la page")
    void boundsVeryLongLoops(@TempDir Path dir) throws IOException {
        StringBuilder trace = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            trace.append("    +---[1,0% 0.010ms ] a.b.C:calculer() #12\n");
        }
        Inspection in = Inspection.read(dir.resolve("a.txt"), write(dir, "t.txt", trace.toString()), 8);
        @SuppressWarnings("unchecked")
        Map<String, Object> call = (Map<String, Object>) ((List<Object>) in.trace.get("12")).get(0);
        assertEquals(500, call.get("n"), "le compte total reste exact");
        assertTrue(((List<?>) call.get("passages")).size() <= 60,
                "mais la liste détaillée est bornée");
    }

    @Test
    @DisplayName("Deux appelés différents sur la même ligne restent distincts")
    void keepsDistinctCalleesOnOneLine(@TempDir Path dir) throws IOException {
        String trace = """
                    +---[1,0% 0.01ms ] a.b.C:un() #12
                    +---[2,0% 0.02ms ] a.b.D:deux() #12
                """;
        Inspection in = Inspection.read(dir.resolve("absent.txt"), write(dir, "t.txt", trace), 8);
        assertEquals(2, ((List<?>) in.trace.get("12")).size());
    }

    @Test
    @DisplayName("Des fichiers absents donnent des relevés vides, pas une erreur")
    void missingFilesGiveEmptyResults(@TempDir Path dir) throws IOException {
        Inspection in = Inspection.read(dir.resolve("a.txt"), dir.resolve("b.txt"), 8);
        assertTrue(in.values.isEmpty());
        assertTrue(in.trace.isEmpty());
    }
}
