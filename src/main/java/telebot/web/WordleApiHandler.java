package telebot.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import telebot.game.Wordle;
import telebot.storage.WordleSessionStore;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON API backing the Wordle Mini App. Reuses the pure rules in {@link Wordle} and persists
 * per-user sessions via {@link WordleSessionStore}; the answer is kept server-side and only
 * revealed once the player has won.
 *
 * <p>Endpoints (both accept query-string and {@code application/x-www-form-urlencoded} params):
 * <ul>
 *   <li>{@code POST /api/wordle/start} — params {@code initData}, {@code hardMode}; resumes
 *       today's in-progress game or seeds a new one. Returns the current board.</li>
 *   <li>{@code POST /api/wordle/guess} — params {@code initData}, {@code guess}; validates and
 *       records the guess. Returns the updated board.</li>
 * </ul>
 */
public class WordleApiHandler implements HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(WordleApiHandler.class);

    private final TelegramAuth auth;
    private final WordleSessionStore sessions;

    public WordleApiHandler(TelegramAuth auth, WordleSessionStore sessions) {
        this.auth = auth;
        this.sessions = sessions;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                send(exchange, 405, errorJson("Method not allowed"));
                return;
            }

            Map<String, String> params = readParams(exchange);
            Long userId = auth.authenticate(params.get("initData"));
            if (userId == null) {
                send(exchange, 401, errorJson("Unauthorized"));
                return;
            }

            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/start")) {
                handleStart(exchange, userId, params);
            } else if (path.endsWith("/guess")) {
                handleGuess(exchange, userId, params);
            } else if (path.endsWith("/end")) {
                handleEnd(exchange, userId);
            } else {
                send(exchange, 404, errorJson("Not found"));
            }
        } catch (Exception e) {
            logger.error("Wordle API error", e);
            send(exchange, 500, errorJson("Internal error"));
        }
    }

    private void handleStart(HttpExchange exchange, long userId, Map<String, String> params) throws IOException {
        boolean requestedHard = "true".equalsIgnoreCase(params.get("hardMode"));
        String answer = Wordle.fetchSolution();
        if (answer == null) {
            send(exchange, 503, errorJson("Sorry, I couldn't fetch today's Wordle. Please try again later."));
            return;
        }

        // Always seed a fresh game on open rather than resuming the stored session. Mobile
        // Telegram doesn't reliably fire the page-unload beacon that clears the session on close,
        // so resetting here guarantees every reopen starts clean regardless of how the app was
        // closed. (The close-time /end call remains as best-effort immediate cleanup.)
        List<String> guesses = new ArrayList<>();
        sessions.setWordleAnswer(userId, answer);
        sessions.setWordleGuesses(userId, guesses);
        sessions.setWordleHardMode(userId, requestedHard);

        send(exchange, 200, boardJson(guesses, answer, requestedHard, false));
    }

    private void handleGuess(HttpExchange exchange, long userId, Map<String, String> params) throws IOException {
        String answer = sessions.getWordleAnswer(userId);
        if (answer == null || answer.isEmpty()) {
            send(exchange, 409, errorJson("No active game. Start a new one."));
            return;
        }
        boolean hardMode = sessions.isWordleHardMode(userId);
        List<String> guesses = sessions.getWordleGuesses(userId);

        if (isWon(guesses, answer)) {
            // Already solved today — just echo the finished board.
            send(exchange, 200, boardJson(guesses, answer, hardMode, true));
            return;
        }

        String raw = params.getOrDefault("guess", "").trim();
        String guess = raw.toLowerCase();
        if (guess.length() != Wordle.WORD_LENGTH || !guess.chars().allMatch(Character::isLetter)) {
            send(exchange, 200, errorJson("Please enter a valid 5-letter word."));
            return;
        }
        if (!Wordle.isValidWord(guess)) {
            send(exchange, 200, errorJson("\"" + raw + "\" is not in the word list."));
            return;
        }
        if (hardMode) {
            String violation = Wordle.hardModeViolation(guesses, answer, guess);
            if (violation != null) {
                send(exchange, 200, errorJson(violation));
                return;
            }
        }

        guesses.add(guess);
        sessions.setWordleGuesses(userId, guesses);
        send(exchange, 200, boardJson(guesses, answer, hardMode, guess.equals(answer)));
    }

    /**
     * Destroys the user's session when they close the Mini App. Called best-effort from the page's
     * unload hook (typically via {@code navigator.sendBeacon}), so it just clears state and acks.
     */
    private void handleEnd(HttpExchange exchange, long userId) throws IOException {
        sessions.clearWordleSession(userId);
        send(exchange, 200, "{\"ok\":true}");
    }

    private static boolean isWon(List<String> guesses, String answer) {
        return !guesses.isEmpty() && guesses.get(guesses.size() - 1).equals(answer);
    }

    // ---- JSON building (hand-rolled; words are lowercase a-z so no escaping needed there) ----

    /**
     * Renders the full board: every guess with its {@link Wordle#evaluate} array, the aggregated
     * per-letter keyboard state (best of green > yellow > gray), and the win flag. The answer is
     * included only when {@code won}.
     */
    private static String boardJson(List<String> guesses, String answer, boolean hardMode, boolean won) {
        Map<Character, Integer> keyboard = new LinkedHashMap<>();
        StringBuilder rows = new StringBuilder("[");
        for (int g = 0; g < guesses.size(); g++) {
            String word = guesses.get(g);
            int[] eval = Wordle.evaluate(word, answer);
            if (g > 0) rows.append(',');
            rows.append("{\"word\":\"").append(word).append("\",\"eval\":[");
            for (int i = 0; i < eval.length; i++) {
                if (i > 0) rows.append(',');
                rows.append(eval[i]);
                keyboard.merge(word.charAt(i), eval[i], Math::max);
            }
            rows.append("]}");
        }
        rows.append(']');

        StringBuilder kb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<Character, Integer> e : keyboard.entrySet()) {
            if (!first) kb.append(',');
            first = false;
            kb.append('"').append(e.getKey()).append("\":").append(e.getValue());
        }
        kb.append('}');

        StringBuilder sb = new StringBuilder();
        sb.append("{\"ok\":true")
                .append(",\"hardMode\":").append(hardMode)
                .append(",\"wordLength\":").append(Wordle.WORD_LENGTH)
                .append(",\"date\":\"").append(Wordle.puzzleDate()).append('"')
                .append(",\"won\":").append(won)
                .append(",\"guesses\":").append(rows)
                .append(",\"keyboard\":").append(kb);
        if (won) {
            sb.append(",\"answer\":\"").append(answer).append('"');
        }
        sb.append('}');
        return sb.toString();
    }

    private static String errorJson(String message) {
        String escaped = message.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"ok\":false,\"error\":\"" + escaped + "\"}";
    }

    // ---- request parsing ----

    /** Merges query-string and form-body params (body wins on conflict). */
    private static Map<String, String> readParams(HttpExchange exchange) throws IOException {
        Map<String, String> params = new HashMap<>();
        parseInto(params, exchange.getRequestURI().getRawQuery());
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        parseInto(params, body);
        return params;
    }

    private static void parseInto(Map<String, String> params, String encoded) {
        if (encoded == null || encoded.isEmpty()) return;
        for (String pair : encoded.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            params.put(key, value);
        }
    }

    private static void send(HttpExchange exchange, int status, String json) throws IOException {
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(payload);
        }
    }
}
