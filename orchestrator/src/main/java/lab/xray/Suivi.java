package lab.xray;

import lab.xray.json.Json;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ce qui se passe pendant l'exécution, écrit dans un fichier — et servi à un navigateur
 * quand on le demande.
 *
 * <h2>Pourquoi un fichier, et pas seulement la console</h2>
 *
 * <p>{@link Progression} dessine une bande dans le terminal, et se tait dès qu'il n'y en a
 * pas. C'est le bon comportement pour un affichage de confort — une ligne par seconde dans
 * un journal d'intégration ne dit rien à personne — mais cela laisse sans réponse le cas le
 * plus courant sur le terrain : <b>l'outil est lancé au fond de scripts imbriqués</b>, sa
 * sortie part dans un tuyau ou dans un fichier, et personne ne voit rien.
 *
 * <p>D'où ce fichier. Il s'écrit <b>toujours</b>, sans qu'on le demande, comme
 * {@code diagnostic.json} : une ligne JSON par seconde, dans {@code progression.jsonl}, à
 * la racine du répertoire de sortie — donc à un chemin qu'on connaît sans connaître le nom
 * de l'exécution. {@code tail -f} suffit à le suivre, depuis n'importe quel autre terminal,
 * y compris sur une machine où l'on n'a que ça.
 *
 * <h2>Pourquoi une page, et pas seulement le fichier</h2>
 *
 * <p>Parce qu'une suite de lignes JSON répond mal à la question qu'on se pose vraiment :
 * <i>est-ce que ça avance ?</i> Il faut comparer la ligne courante aux précédentes, et
 * l'œil ne le fait pas sur du texte qui défile. La page, elle, montre la forme : la bande
 * d'activité, la courbe des cœurs occupés, la sortie qui grossit ou qui ne grossit plus.
 * Elle relit le même fichier, elle n'en produit aucun autre.
 *
 * <p>Elle est <b>facultative</b> ({@code --suivi}), et le fichier ne l'est pas : ce qui
 * doit marcher partout ne doit pas dépendre d'un port ouvert.
 *
 * <h2>Ce que cet objet ne fait pas</h2>
 *
 * <p>Il ne mesure rien — même règle que {@link Progression}, et pour la même raison. Le
 * temps processeur vient de ce que le système compte de toute façon, la taille de sortie
 * d'un fichier déjà écrit. Rien n'est ajouté à la JVM observée, et le rapport est le même
 * selon qu'on suit l'exécution ou non. Le serveur, quand il tourne, sert deux fichiers du
 * disque depuis un fil à part ; il n'approche jamais le processus observé.
 */
public final class Suivi implements AutoCloseable {

    /** Le fichier à suivre. À la racine de la sortie : on le connaît sans rien chercher. */
    public static final String FICHIER = "progression.jsonl";

    /** Le port par défaut de la page. Voisin de celui du rapport servi, sans le heurter. */
    public static final int PORT = 8788;

    private final Path fichier;
    private final String execution;
    private final HttpServer serveur;

    private Suivi(Path fichier, String execution, HttpServer serveur) {
        this.fichier = fichier;
        this.execution = execution;
        this.serveur = serveur;
    }

    /**
     * Ouvre le fil d'une exécution.
     *
     * @param port le port de la page, ou 0 pour n'écrire que le fichier
     */
    public static Suivi ouvrir(Path outDir, Path runDir, String execution, String commande,
                               int port) {
        Path fichier = outDir.resolve(FICHIER);
        HttpServer serveur = null;
        if (port > 0) {
            try {
                serveur = servir(fichier, runDir, port);
            } catch (IOException e) {
                // Un port pris ne doit pas coûter l'exécution : l'observation vaut mieux
                // que son affichage, et le fichier reste écrit de toute façon.
                System.out.println("   follow page not served on port " + port + " ("
                        + e.getMessage() + ") — " + FICHIER + " is written anyway");
            }
        }
        Suivi suivi = new Suivi(fichier, execution, serveur);
        // La commande observée sur la ligne de démarrage : sans elle, quelqu'un qui ouvre
        // la page ou le fichier voit une exécution avancer sans savoir laquelle. Elle ne
        // sort pas de la machine — la page n'écoute que la boucle locale.
        suivi.ecrire("start", Map.of("command", commande == null ? "" : commande,
                "output", outDir.toAbsolutePath().toString()));
        return suivi;
    }

    /** Une ligne d'avancement. Les mêmes chiffres que la bande du terminal. */
    public void avancement(Duration ecoule, Duration cpu, long octets, double coeurs) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("seconds", ecoule.toSeconds());
        f.put("cores", Math.round(coeurs * 100) / 100.0);
        f.put("level", Progression.palier(coeurs));
        f.put("cpuSeconds", cpu.toSeconds());
        f.put("outputBytes", octets);
        ecrire("progress", f);
    }

    /** La dernière ligne : ce qui permet à un lecteur de savoir qu'il peut arrêter. */
    public void fin(String statut, long secondes) {
        ecrire("end", Map.of("status", statut, "seconds", secondes));
    }

    private void ecrire(String evenement, Map<String, Object> champs) {
        Map<String, Object> ligne = new LinkedHashMap<>();
        ligne.put("event", evenement);
        ligne.put("run", execution);
        ligne.put("date", Instant.now().toString());
        ligne.putAll(champs);
        try {
            Files.createDirectories(fichier.getParent());
            Files.writeString(fichier, Json.write(ligne) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // Le suivi est un confort. Qu'il échoue ne doit jamais emporter la mesure,
            // qui est, elle, ce qu'on est venu chercher.
        }
    }

    @Override
    public void close() {
        if (serveur != null) serveur.stop(0);
    }

    // ------------------------------------------------------------------ la page

    private static HttpServer servir(Path fichier, Path runDir, int port) throws IOException {
        // Boucle locale seulement : cette page montre une commande et une sortie
        // d'application, qui n'ont pas à être lisibles depuis le réseau.
        HttpServer server = HttpServer.create(
                new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 0);

        server.createContext("/", ex -> {
            String chemin = ex.getRequestURI().getPath();
            try {
                switch (chemin) {
                    case "/", "/index.html" -> envoyer(ex, "text/html; charset=utf-8", page());
                    case "/progression.jsonl" -> envoyer(ex, "application/x-ndjson; charset=utf-8",
                            lireOuVide(fichier));
                    case "/execution.log" -> {
                        byte[] brut = queue(runDir.resolve("execution.log"));
                        envoyer(ex, "text/plain; charset=utf-8", enUtf8(brut));
                    }
                    default -> envoyer(ex, "text/plain; charset=utf-8",
                            "rien ici".getBytes(StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                envoyer(ex, "text/plain; charset=utf-8",
                        String.valueOf(e.getMessage()).getBytes(StandardCharsets.UTF_8));
            }
        });
        server.setExecutor(null);
        server.start();
        System.out.println("   live follow: http://127.0.0.1:" + port
                + "   (or: tail -f " + fichier + ")");
        return server;
    }

    static byte[] page() throws IOException {
        try (InputStream in = Suivi.class.getResourceAsStream("suivi.html")) {
            if (in == null) throw new IOException("follow page missing from the jar");
            return in.readAllBytes();
        }
    }

    private static byte[] lireOuVide(Path f) throws IOException {
        return Files.exists(f) ? Files.readAllBytes(f) : new byte[0];
    }

    /**
     * La fin du journal de l'application.
     *
     * <p>Bornée : une application bavarde écrit des dizaines de mégaoctets, et les
     * recopier à chaque rafraîchissement ferait payer l'affichage à la machine qui mesure.
     * Ce qui intéresse pendant l'attente est de toute façon la fin.
     */
    static byte[] queue(Path journal) throws IOException {
        if (!Files.exists(journal)) return new byte[0];
        long taille = Files.size(journal);
        long depuis = Math.max(0, taille - 64 * 1024);
        try (var canal = Files.newByteChannel(journal)) {
            canal.position(depuis);
            java.nio.ByteBuffer tampon =
                    java.nio.ByteBuffer.allocate((int) Math.min(taille - depuis, 64 * 1024));
            canal.read(tampon);
            return tampon.array();
        }
    }

    /**
     * Le journal de l'application, rendu lisible sans prétendre savoir dans quel jeu de
     * caractères il a été écrit.
     *
     * <p>L'outil écrit en UTF-8 ; l'application observée, non — elle écrit dans ce que sa
     * JVM lui a donné, et sur le parc visé c'est souvent CP1252 ou CP850. Servir ces
     * octets-là en UTF-8 donne du charabia à la place des accents, sur la moitié des
     * journaux français.
     *
     * <p>On ne devine donc qu'une fois, et seulement quand la certitude a échoué : si les
     * octets forment de l'UTF-8 valide, ils le sont ; sinon on les lit en ISO-8859-1, qui
     * ne rejette aucun octet et rend les accents des jeux occidentaux. Ce n'est pas exact
     * dans tous les cas, et le repli est <b>annoncé dans la page</b> plutôt que subi : un
     * lecteur qui voit un caractère douteux doit savoir que c'est une interprétation.
     *
     * <p>Le contraire — deviner en silence — est ce que l'outil refuse ailleurs, pour les
     * racines de sources. La différence tient à ce que coûte l'erreur : du code faux en
     * face d'une couverture se croit, un accent de travers se voit.
     */
    static byte[] enUtf8(byte[] brut) {
        if (brut.length == 0) return brut;
        var strict = StandardCharsets.UTF_8.newDecoder();
        try {
            strict.decode(java.nio.ByteBuffer.wrap(brut));
            return brut;
        } catch (java.nio.charset.CharacterCodingException pasDeLUtf8) {
            String texte = new String(brut, StandardCharsets.ISO_8859_1);
            return ("[runtime-xray: this log is not UTF-8; read as ISO-8859-1]\n"
                    + texte).getBytes(StandardCharsets.UTF_8);
        }
    }

    private static void envoyer(com.sun.net.httpserver.HttpExchange ex, String type, byte[] corps)
            throws IOException {
        ex.getResponseHeaders().add("Content-Type", type);
        // Sans cela, un navigateur sert la première réponse pendant toute l'exécution :
        // la page semblerait figée alors que le fichier grossit.
        ex.getResponseHeaders().add("Cache-Control", "no-store");
        ex.sendResponseHeaders(200, corps.length == 0 ? -1 : corps.length);
        if (corps.length > 0) {
            try (OutputStream out = ex.getResponseBody()) {
                out.write(corps);
            }
        } else {
            ex.close();
        }
    }
}
