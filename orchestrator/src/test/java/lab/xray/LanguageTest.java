package lab.xray;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ce que l'utilisateur lit est en anglais.
 *
 * <h2>Pourquoi un test, et pas une relecture</h2>
 *
 * <p>La bascule vers l'anglais s'est faite par étapes, et chacune a laissé derrière elle un
 * dépôt où <b>les deux langues cohabitent légitimement</b> : le code, les commentaires et
 * les {@code @DisplayName} restent en français jusqu'à leur étape. Une phrase française
 * ajoutée dans un {@code println} ne détonne donc pas à la relecture — elle ressemble à
 * tout ce qui l'entoure. Elle ne se voit qu'à l'exécution, sur le poste de quelqu'un qui ne
 * lit pas le français.
 *
 * <h2>Ce qui est cherché, et ce qui ne l'est pas</h2>
 *
 * <p>Une <b>phrase</b> : au moins quatre mots, et un mot outil français. C'est
 * volontairement étroit. Les identifiants restent hors d'atteinte — et c'est nécessaire,
 * parce que beaucoup sont français à dessein et le resteront : les clés JSON des mesures
 * ({@code methodeRacine}, {@code dureeSecondes}), les noms internes des niveaux
 * ({@code couverture}, {@code arbre}, {@code complet}) et les noms d'options français, qui
 * sont acceptés à vie pour ne casser aucun script déjà déployé.
 *
 * <p>Ce test ne prouve donc pas que tout est traduit ; il attrape la régression qu'on
 * commet vraiment — une phrase entière écrite dans la langue du dépôt plutôt que dans celle
 * de l'outil.
 */
class LanguageTest {

    /** Les mots outils qui trahissent une phrase française. Sans « on », qui est anglais. */
    private static final Pattern FRENCH = Pattern.compile(
            "(^|[^\\w])(le|la|les|des|une|du|de|qui|que|pour|dans|est|sont|pas|sur|avec"
            + "|cette|aux|par|leur|elle|aucun|aucune|été|nous|vous|ils)([^\\w]|$)",
            Pattern.CASE_INSENSITIVE);

    /**
     * Les littéraux, <b>blocs de texte compris</b>.
     *
     * <p>Les oublier a laissé passer le gabarit de configuration : cent trente lignes de
     * français écrites dans un {@code \"\"\"…\"\"\"}, et déposées telles quelles sur le disque
     * de qui lance l'outil sans fichier de configuration. Un test qui ne regarde que les
     * chaînes ordinaires rassure sur ce qu'il ne vérifie pas.
     */
    private static final Pattern LITERAL = Pattern.compile(
            "\"\"\"(.*?)\"\"\"|\"((?:[^\"\\\\\\n]|\\\\.)*)\"", Pattern.DOTALL);

    @Test
    @DisplayName("Aucune phrase française ne subsiste dans ce que l'outil écrit")
    void noFrenchSentenceIsLeftInWhatTheToolWrites() throws IOException {
        Path root = Path.of("src/main/java");
        assertTrue(Files.isDirectory(root), "les sources doivent être là : " + root);

        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(f, StandardCharsets.UTF_8);
                Matcher m = LITERAL.matcher(source);
                while (m.find()) {
                    String text = m.group(1) != null ? m.group(1) : m.group(2);
                    // Quatre mots au moins : en deçà, c'est un identifiant, une clé JSON ou
                    // un fragment de format — et beaucoup sont français pour de bonnes
                    // raisons, à commencer par les noms d'options qui ne casseront jamais.
                    if (text.chars().filter(c -> c == ' ').count() >= 3
                            && FRENCH.matcher(text).find()) {
                        found.add(root.relativize(f) + " : " + text);
                    }
                }
            }
        }
        assertEquals(List.of(), found,
                "phrase(s) française(s) dans un littéral : l'outil parle anglais, et une "
                + "phrase ajoutée dans la langue du dépôt ne se voit qu'à l'exécution, "
                + "sur le poste de quelqu'un qui ne lit pas le français");
    }
}
