package lab.tools.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Code "dummy" minimal servant de référence de structure pour les modules
 * de comparaison d'outils. Ne dépend d'aucune bibliothèque tierce : il
 * reproduit à la main le cas d'usage de référence (voir docs/METHODOLOGY.md)
 * — un tableau d'une dizaine de lignes avec un total — pour donner un point
 * de comparaison "sans outil" face aux modules qui en évalueront un vrai.
 */
public class ExampleToolReportDemo {

    private record Row(String label, int value) {}

    public static void main(String[] args) throws IOException {
        List<Row> rows = List.of(
                new Row("Ligne 1", 12),
                new Row("Ligne 2", 7),
                new Row("Ligne 3", 25),
                new Row("Ligne 4", 3),
                new Row("Ligne 5", 18),
                new Row("Ligne 6", 9),
                new Row("Ligne 7", 21),
                new Row("Ligne 8", 4),
                new Row("Ligne 9", 15),
                new Row("Ligne 10", 11)
        );

        int total = rows.stream().mapToInt(Row::value).sum();

        String html = buildHtml(rows, total);

        Path outDir = Paths.get("reports-demo", "generated", "example-tool");
        Files.createDirectories(outDir);
        Path outFile = outDir.resolve("report.html");
        Files.writeString(outFile, html);

        System.out.println("Rapport genere : " + outFile.toAbsolutePath());
    }

    private static String buildHtml(List<Row> rows, int total) {
        StringBuilder rowsHtml = new StringBuilder();
        for (Row r : rows) {
            rowsHtml.append("<tr><td>").append(r.label())
                    .append("</td><td>").append(r.value())
                    .append("</td></tr>\n");
        }

        return """
                <!DOCTYPE html>
                <html lang="fr">
                <head>
                  <meta charset="UTF-8">
                  <title>example-tool — rapport de démonstration</title>
                  <style>
                    body { font-family: sans-serif; margin: 2rem; }
                    table { border-collapse: collapse; width: 320px; }
                    th, td { border: 1px solid #ccc; padding: 0.4rem 0.8rem; text-align: left; }
                    tfoot td { font-weight: bold; }
                  </style>
                </head>
                <body>
                  <h1>Rapport de démonstration — example-tool</h1>
                  <p>Génération 100%% statique, aucune bibliothèque de reporting.</p>
                  <table>
                    <thead><tr><th>Libellé</th><th>Valeur</th></tr></thead>
                    <tbody>
                %s    </tbody>
                    <tfoot><tr><td>Total</td><td>%d</td></tr></tfoot>
                  </table>
                </body>
                </html>
                """.formatted(rowsHtml, total);
    }
}
