package telebot.web;

import com.ngrok.Forwarder;
import com.ngrok.Session;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import telebot.storage.RedisConnection;
import telebot.storage.WordleSessionStore;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.Executors;

/**
 * Embedded HTTP server hosting the Wordle Mini App: serves the static page from the classpath
 * ({@code /web/*}) and the JSON API ({@code /api/wordle/*}). Runs on a single-threaded executor
 * so its dedicated {@link RedisConnection} (a non-thread-safe {@link redis.clients.jedis.Jedis})
 * is only ever touched by one thread — independent of the bot's own connection.
 */
public class WebServer {
    private static final Logger logger = LoggerFactory.getLogger(WebServer.class);

    private static final String STATIC_ROOT = "/web";
    private static final String DEFAULT_FILE = "index.html";

    private final HttpServer server;
    private final int port;
    private final String ngrokAuthtoken;

    // Held as fields so the native tunnel isn't torn down by GC while the app runs.
    private Session ngrokSession;
    private Forwarder.Endpoint ngrokForwarder;
    private String publicUrl;

    public WebServer(int port, String botToken, String redisUrl, boolean devBypassAuth, String ngrokAuthtoken)
            throws IOException {
        this.port = port;
        this.ngrokAuthtoken = ngrokAuthtoken;

        RedisConnection redis = new RedisConnection(redisUrl);
        WordleSessionStore sessions = new WordleSessionStore(redis);
        TelegramAuth auth = new TelegramAuth(botToken, devBypassAuth);
        WordleApiHandler api = new WordleApiHandler(auth, sessions);

        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/wordle/", api);
        server.createContext("/", new StaticHandler());
        server.setExecutor(Executors.newSingleThreadExecutor());

        if (devBypassAuth) {
            logger.warn("WebServer auth bypass is ENABLED — Mini App requests are not verified");
        }
    }

    public void start() {
        server.start();
        logger.info("Web server listening on {}", server.getAddress());
        startTunnel();
    }

    /**
     * Opens an ngrok tunnel forwarding public HTTPS traffic to the local server, exposing
     * {@link #getPublicUrl()} for the bot's Mini App button. Started after the local server is
     * up so the upstream is ready. Failures (e.g. missing authtoken) are logged but non-fatal —
     * the app keeps serving on localhost.
     */
    private void startTunnel() {
        if (ngrokAuthtoken == null || ngrokAuthtoken.isBlank()) {
            logger.warn("NGROK_AUTHTOKEN not set — skipping tunnel; Mini App reachable only on localhost:{}", port);
            return;
        }
        try {
            ngrokSession = Session.withAuthtoken(ngrokAuthtoken).connect();
            ngrokForwarder = ngrokSession.httpEndpoint()
                    .forward(URI.create("http://localhost:" + port).toURL());
            publicUrl = ngrokForwarder.getUrl().toString();
            logger.info("ngrok tunnel established: {} -> http://localhost:{}", publicUrl, port);
        } catch (Exception e) {
            logger.error("Failed to start ngrok tunnel; Mini App reachable only on localhost:{}", port, e);
        }
    }

    /** The public HTTPS URL from the ngrok tunnel, or {@code null} if no tunnel is active. */
    public String getPublicUrl() {
        return publicUrl;
    }

    /** Serves a small whitelist-style set of static files from the classpath under {@code /web}. */
    private static class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/")) {
                path = "/" + DEFAULT_FILE;
            }
            // Reject path traversal; only serve simple relative names under STATIC_ROOT.
            if (path.contains("..")) {
                respond(exchange, 400, "text/plain", "Bad request".getBytes());
                return;
            }

            String resource = STATIC_ROOT + path;
            try (InputStream in = WebServer.class.getResourceAsStream(resource)) {
                if (in == null) {
                    respond(exchange, 404, "text/plain", "Not found".getBytes());
                    return;
                }
                respond(exchange, 200, contentType(path), in.readAllBytes());
            }
        }
    }

    private static String contentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        if (path.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (path.endsWith(".json")) return "application/json; charset=utf-8";
        if (path.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

    private static void respond(HttpExchange exchange, int status, String contentType, byte[] body)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }
}
