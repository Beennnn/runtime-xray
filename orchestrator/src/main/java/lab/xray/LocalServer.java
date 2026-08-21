package lab.xray;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import lab.xray.json.Json;
import lab.xray.report.Annotations;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Le rapport servi, pour que les annotations cessent d'être prisonnières d'un navigateur.
 *
 * <p>Une page ouverte comme fichier ne peut rien écrire sur le disque — c'est une règle du
 * navigateur, et c'est aussi ce qui rend la page transmissible telle quelle. Les noms,
 * descriptions, étiquettes et élagages saisis dedans vivent donc dans le navigateur de
 * chacun. C'est le mode le plus simple, et il suffit souvent.
 *
 * <p>Ce serveur ouvre les deux autres :
 * <ul>
 *   <li><b>sur son poste</b> — {@code --serve} : la page écrit ses annotations à côté des
 *       exécutions — voir {@link Annotations} pour les emplacements possibles — et le
 *       rapport est régénéré, pour que l'annotation y soit acquise même hors serveur ;</li>
 *   <li><b>déployé quelque part</b> — {@code --serve --serve-host 0.0.0.0} : on y dépose
 *       les résultats, tout le monde y accède par un navigateur, et <b>plusieurs
 *       personnes annotent en parallèle</b>. Les exécutions déposées pendant qu'il tourne
 *       sont prises en compte toutes seules : voir la veille, plus bas.</li>
 * </ul>
 *
 * <p>Le parallélisme est la seule difficulté réelle, et elle est traitée là où elle se
 * pose : <b>l'écriture porte sur une exécution</b>, pas sur le fichier entier. Deux
 * personnes qui annotent deux exécutions ne se voient donc jamais. Deux personnes sur la
 * <b>même</b> exécution sont départagées par l'empreinte de ce qu'elles avaient sous les
 * yeux : la seconde reçoit un refus et la version courante, plutôt que d'écraser en
 * silence le travail de la première.
 *
 * <p>Ce qu'il n'est pas : un service authentifié. Il n'écoute que la boucle locale par
 * défaut, et la seule écriture qu'il accepte est l'annotation d'une exécution, dans un
 * fichier dont il choisit lui-même le nom. Déployé au-delà, il se met derrière ce qui
 * filtre déjà les accès de l'entreprise — l'avertissement est imprimé au démarrage.
 */
public final class LocalServer {

    private static final int MAX_BODY = 4 * 1024 * 1024;

    /** Les écritures sont sérialisées : lire, fondre, écrire ne doit pas s'entrelacer. */
    private static final ReentrantLock LOCK = new ReentrantLock();

    private LocalServer() {}

    /**
     * Sert {@code outDir} et bloque jusqu'à l'interruption.
     *
     * @param host  interface d'écoute — {@code 127.0.0.1} par défaut
     * @param rebuild régénère la page après une écriture, en arrière-plan : sans lui, une
     *                page rouverte comme fichier afficherait l'annotation d'avant
     */
    public static void serve(Path outDir, String host, int port, Callable<Void> rebuild)
            throws IOException {
        HttpServer server = start(outDir, host, port, rebuild);
        annonce(outDir.toAbsolutePath().normalize(), host, port);

        CountDownLatch stop = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(0);
            stop.countDown();
        }));
        try {
            stop.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Monte et démarre le serveur, sans bloquer.
     *
     * <p>Séparé de {@link #serve} pour que les tests puissent le lancer sur un port choisi
     * par le système, l'interroger, puis l'arrêter — un serveur qui ne rend jamais la main
     * ne se teste pas.
     */
    static HttpServer start(Path outDir, String host, int port, Callable<Void> rebuild)
            throws IOException {
        Path root = outDir.toAbsolutePath().normalize();
        InetAddress address = "0.0.0.0".equals(host) || "*".equals(host)
                ? new InetSocketAddress(port).getAddress()
                : InetAddress.getByName(host);
        HttpServer server = HttpServer.create(new InetSocketAddress(address, port), 0);
        Rebuilder rebuilder = new Rebuilder(rebuild);

        // La page interroge ce chemin au chargement : c'est ce qui lui dit qu'elle peut
        // proposer « Enregistrer » plutôt que le seul export de fichier.
        server.createContext("/__xray/ping", ex -> {
            noCache(ex);
            json(ex, 200, Map.of("peutEcrire", true, "fichier", Annotations.DANS_LE_RUN));
        });

        // Les annotations partagées, avec l'empreinte de chacune : c'est elle qui permet de
        // détecter qu'une exécution a bougé pendant qu'on l'annotait.
        server.createContext("/__xray/noms", ex -> {
            noCache(ex);
            try {
                String method = ex.getRequestMethod().toUpperCase(Locale.ROOT);
                String rest = ex.getRequestURI().getPath().substring("/__xray/noms".length());
                String uuid = rest.startsWith("/") ? rest.substring(1) : "";

                if (method.equals("GET")) {
                    Map<String, Object> tout = annotationsEffectives(root);
                    // La révision dit à la page si le rapport a changé sous ses pieds —
                    // une exécution déposée, une autre retirée. Elle la relit à intervalle
                    // régulier de toute façon : autant qu'elle l'apprenne là.
                    String revision = revision(root);
                    // Une empreinte pour CHAQUE exécution, y compris celles qui n'ont pas
                    // encore d'annotation : sans elle, la première écriture n'aurait rien à
                    // comparer, et deux personnes qui créent la même annotation en même
                    // temps ne se verraient pas.
                    Map<String, Object> empreintes = new LinkedHashMap<>();
                    Annotations.runsByUuid(root).forEach(
                            (uuidRun, dir) -> empreintes.put(uuidRun, fingerprint(tout.get(uuidRun))));
                    json(ex, 200, Map.of("annotations", tout, "empreintes", empreintes,
                            "revision", revision, "executions", empreintes.size()));
                    return;
                }
                if (!method.equals("POST") && !method.equals("PUT")) {
                    text(ex, 405, "méthode non acceptée");
                    return;
                }
                if (uuid.isBlank()) {
                    text(ex, 400, "l'écriture porte sur une exécution : /__xray/noms/<uuid>");
                    return;
                }
                // Un corps illisible est une faute de l'appelant, pas une panne du
                // serveur : répondre 500 enverrait chercher le problème du mauvais côté.
                Object lu;
                try {
                    lu = Json.read(read(ex.getRequestBody()));
                } catch (Exception malforme) {
                    text(ex, 400, "corps illisible : " + malforme.getMessage());
                    return;
                }
                if (!(lu instanceof Map<?, ?> corps)) {
                    text(ex, 400, "le corps attendu est un objet JSON");
                    return;
                }
                ecrire(root, uuid, corps, ex, rebuilder);
            } catch (Exception e) {
                System.err.println("   écriture refusée : " + e.getMessage());
                text(ex, 500, String.valueOf(e.getMessage()));
            }
        });

        server.createContext("/", ex -> {
            try {
                Path file = resolve(root, ex.getRequestURI().getPath());
                if (file == null || !Files.isRegularFile(file)) {
                    text(ex, 404, "introuvable");
                    return;
                }
                noCache(ex);
                send(ex, 200, contentType(file), Files.readAllBytes(file));
            } catch (Exception e) {
                text(ex, 500, String.valueOf(e.getMessage()));
            }
        });

        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4));
        server.start();
        veiller(root, rebuilder);
        return server;
    }

    /**
     * Surveille l'arrivée d'exécutions déposées pendant que le serveur tourne.
     *
     * <p>C'est le scénario même du serveur partagé : on y dépose des résultats, et tout le
     * monde les lit. Sans cette veille, il faudrait redémarrer le serveur à chaque dépôt —
     * autant dire que personne ne le ferait, et que le répertoire et la page finiraient par
     * dire deux choses différentes.
     *
     * <p>Par sondage, et non par surveillance du système de fichiers : les résultats
     * arrivent souvent par un partage réseau, où les notifications de modification sont au
     * mieux irrégulières. Dix secondes suffisent — on ne dépose pas une exécution dix fois
     * par minute.
     */
    private static void veiller(Path root, Rebuilder rebuilder) {
        Thread.ofPlatform().daemon().name("runtime-xray-veille").start(() -> {
            String connu = revision(root);
            while (true) {
                try {
                    Thread.sleep(10_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                String maintenant = revision(root);
                if (!maintenant.equals(connu)) {
                    connu = maintenant;
                    System.out.println("   exécutions modifiées sur le disque — la page est "
                            + "réassemblée");
                    rebuilder.demander();
                }
            }
        });
    }

    /**
     * Ce qui distingue un état du répertoire d'un autre : les exécutions présentes et la
     * date de leur contexte. Deux dépôts successifs donnent deux révisions différentes ;
     * une simple relecture, non.
     */
    static String revision(Path root) {
        StringBuilder sb = new StringBuilder();
        for (Path run : Annotations.runDirs(root)) {
            sb.append(run.getFileName()).append(':');
            try {
                sb.append(Files.getLastModifiedTime(run.resolve("run-context.json")).toMillis());
            } catch (IOException e) {
                sb.append('?');
            }
            sb.append(';');
        }
        return fingerprint(sb.toString());
    }

    /**
     * Écrit l'annotation d'une exécution, là où elle vit déjà ou dans son répertoire.
     *
     * <p>Le corps porte {@code base}, l'empreinte de ce que l'auteur avait sous les yeux.
     * Si elle ne correspond plus, quelqu'un d'autre est passé entre-temps : on refuse, et
     * on rend la version courante pour qu'il puisse décider — écraser serait perdre le
     * travail d'un tiers sans que personne ne s'en aperçoive.
     */
    @SuppressWarnings("unchecked")
    private static void ecrire(Path root, String uuid, Map<?, ?> corps, HttpExchange ex,
                               Rebuilder rebuilder) throws IOException {
        Object valeur = corps.get("valeur");
        String base = corps.get("base") == null ? null : String.valueOf(corps.get("base"));

        LOCK.lock();
        try {
            Path runDir = Annotations.runsByUuid(root).get(uuid);
            if (runDir == null) {
                text(ex, 404, "aucune exécution ne porte l'identifiant " + uuid);
                return;
            }
            Object courante = Annotations.forRun(runDir, uuid, Annotations.readCentral(root));
            String actuelle = fingerprint(courante);
            if (base != null && !base.equals(actuelle)) {
                json(ex, 409, Map.of("conflit", true,
                        "valeur", courante == null ? Map.of() : courante,
                        "empreinte", actuelle));
                return;
            }
            Map<String, Object> annotation = valeur instanceof Map<?, ?> m
                    ? new LinkedHashMap<>((Map<String, Object>) m)
                    : new LinkedHashMap<>();
            Path file = Annotations.write(runDir, annotation);
            System.out.println("   annotation enregistrée : " + file);
            json(ex, 200, Map.of("ok", true, "fichier", file.toString(),
                    "empreinte", fingerprint(annotation.isEmpty() ? null : annotation)));
        } finally {
            LOCK.unlock();
        }
        // Après la réponse : régénérer la page prend une seconde, et personne n'a de raison
        // d'attendre dessus pour continuer à annoter.
        rebuilder.demander();
    }

    /**
     * Les annotations telles que la page doit les voir : pour chaque exécution, celle qui
     * l'emporte parmi les trois emplacements possibles — voir {@link Annotations}.
     */
    private static Map<String, Object> annotationsEffectives(Path root) {
        Map<String, Object> central = Annotations.readCentral(root);
        Map<String, Object> out = new LinkedHashMap<>();
        Annotations.runsByUuid(root).forEach((uuid, runDir) -> {
            Object valeur = Annotations.forRun(runDir, uuid, central);
            if (valeur != null) out.put(uuid, valeur);
        });
        return out;
    }

    /** Empreinte d'une annotation : elle change dès que son contenu change, et pas avant. */
    static String fingerprint(Object valeur) {
        String texte = valeur == null ? "" : Json.write(valeur);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(texte.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", hash[i]));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(texte.hashCode());
        }
    }

    /**
     * Régénération de la page, une à la fois et jamais en rafale.
     *
     * <p>Dix personnes qui enregistrent en même temps ne doivent pas déclencher dix
     * assemblages concurrents : une demande pendant qu'un assemblage tourne se contente
     * d'en réclamer un de plus, à la fin.
     */
    private static final class Rebuilder {
        private final Callable<Void> action;
        private boolean enCours;
        private boolean redemande;

        Rebuilder(Callable<Void> action) { this.action = action; }

        synchronized void demander() {
            if (enCours) { redemande = true; return; }
            enCours = true;
            Thread.ofPlatform().daemon().start(this::boucle);
        }

        private void boucle() {
            while (true) {
                try {
                    action.call();
                } catch (Exception e) {
                    System.err.println("   régénération de la page impossible : " + e.getMessage());
                }
                synchronized (this) {
                    if (!redemande) { enCours = false; return; }
                    redemande = false;
                }
            }
        }
    }

    private static void annonce(Path root, String host, int port) {
        boolean local = host.startsWith("127.") || host.equals("localhost");
        System.out.println();
        System.out.println("▶ Rapport servi sur http://" + (local ? "localhost" : host)
                + ":" + port + "/");
        System.out.println("   Les annotations saisies dans la page s'écrivent dans le");
        System.out.println("   répertoire de chaque exécution (" + Annotations.DANS_LE_RUN
                + "), et la page est régénérée :");
        System.out.println("   elles valent alors pour tout le monde, et suivent l'exécution "
                + "si on la déplace.");
        if (!local) {
            System.out.println();
            System.out.println("   ⚠️ Écoute au-delà de la boucle locale, SANS authentification :");
            System.out.println("      quiconque atteint ce port peut lire les rapports et annoter.");
            System.out.println("      À placer derrière ce qui filtre déjà les accès.");
        }
        System.out.println("   Ctrl-C pour arrêter.");
    }

    /**
     * Le fichier demandé, ou {@code null} s'il sort du répertoire servi.
     *
     * <p>Un chemin de requête peut contenir n'importe quoi, y compris de quoi remonter
     * l'arborescence : on normalise, puis on vérifie que le résultat est bien sous la
     * racine — la vérification porte sur le chemin résolu, jamais sur ce qui a été écrit.
     */
    static Path resolve(Path root, String requestPath) {
        String decoded = java.net.URLDecoder.decode(requestPath, StandardCharsets.UTF_8);
        if (decoded.isBlank() || decoded.equals("/")) decoded = "/index.html";
        Path candidate = root.resolve(decoded.substring(1)).normalize();
        if (!candidate.startsWith(root)) return null;
        return Files.isDirectory(candidate) ? candidate.resolve("index.html") : candidate;
    }

    private static String contentType(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".json") || name.endsWith(".cpuprofile")) return "application/json";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".xml")) return "application/xml";
        return "text/plain; charset=utf-8";
    }

    private static void noCache(HttpExchange ex) {
        // La page est régénérée sous les pieds du navigateur : une version gardée en cache
        // afficherait l'annotation d'avant, ce qui ressemblerait à une écriture perdue.
        ex.getResponseHeaders().add("Cache-Control", "no-store");
    }

    private static String read(InputStream in) throws IOException {
        return new String(in.readNBytes(MAX_BODY), StandardCharsets.UTF_8);
    }

    private static void json(HttpExchange ex, int code, Map<String, Object> body)
            throws IOException {
        send(ex, code, "application/json", Json.write(body).getBytes(StandardCharsets.UTF_8));
    }

    private static void text(HttpExchange ex, int code, String body) throws IOException {
        send(ex, code, "text/plain; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange ex, int code, String type, byte[] body)
            throws IOException {
        ex.getResponseHeaders().add("Content-Type", type);
        ex.sendResponseHeaders(code, body.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(body);
        }
    }
}
