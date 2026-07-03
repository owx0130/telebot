package telebot.game;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Self-contained Wordle game logic: word-list validation, fetching the daily NYT
 * solution, evaluating guesses, hard-mode hint checking, and rendering the board.
 *
 * <p>Stateless utility: the word list is loaded once into a static set at class load, so all
 * members are {@code static} and the class is never instantiated.
 */
public final class Wordle {
    private static final Logger logger = LoggerFactory.getLogger(Wordle.class);

    private static final String WORD_LIST_RESOURCE = "/wordle-words.txt";
    private static final String NYT_URL_TEMPLATE = "https://www.nytimes.com/svc/wordle/v2/%s.json";
    // Which calendar date's puzzle to fetch — resolved in GMT+8 (Singapore, no DST).
    private static final ZoneId PUZZLE_ZONE = ZoneId.of("Asia/Singapore");
    // Testing override: set to an ISO date (e.g. "2025-01-15") to always fetch that
    // day's puzzle instead of today's. Leave null for normal (current-date) behaviour.
    private static final String TEST_DATE_OVERRIDE = null;
    private static final Pattern SOLUTION_PATTERN = Pattern.compile("\"solution\"\\s*:\\s*\"([a-z]+)\"");
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    // Tile states returned by evaluate() (0 = gray/absent).
    public static final int YELLOW = 1;
    public static final int GREEN = 2;

    public static final int WORD_LENGTH = 5;

    private static final Set<String> VALID_WORDS = loadWordList();

    private Wordle() {
        // Static utility; not instantiable.
    }

    private static Set<String> loadWordList() {
        Set<String> words = new HashSet<>();
        try (InputStream in = Wordle.class.getResourceAsStream(WORD_LIST_RESOURCE)) {
            if (in == null) {
                logger.error("Wordle word list resource {} not found", WORD_LIST_RESOURCE);
                return words;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String word = line.trim().toLowerCase();
                    if (word.length() == WORD_LENGTH) {
                        words.add(word);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Failed to load Wordle word list", e);
        }
        return words;
    }

    /** True if the (already lowercased) guess is a known 5-letter word. */
    public static boolean isValidWord(String guess) {
        return VALID_WORDS.contains(guess);
    }

    /**
     * The calendar date (ISO {@code yyyy-MM-dd}) of the puzzle currently in play — resolved in
     * GMT+8, or the test override when set. This is the same date {@link #fetchSolution()} fetches.
     */
    public static String puzzleDate() {
        return TEST_DATE_OVERRIDE != null
                ? TEST_DATE_OVERRIDE
                : LocalDate.now(PUZZLE_ZONE).format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    /**
     * Fetches today's Wordle solution from the NYT endpoint (date resolved in GMT+8).
     * Returns the lowercase answer, or {@code null} on any failure.
     */
    public static String fetchSolution() {
        String date = puzzleDate();
        String url = String.format(NYT_URL_TEMPLATE, date);
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                logger.error("NYT Wordle fetch returned status {} for {}", response.statusCode(), url);
                return null;
            }
            Matcher matcher = SOLUTION_PATTERN.matcher(response.body());
            if (matcher.find()) {
                return matcher.group(1);
            }
            logger.error("Could not find solution field in NYT Wordle response");
            return null;
        } catch (IOException e) {
            logger.error("Failed to fetch NYT Wordle solution", e);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching NYT Wordle solution", e);
            return null;
        }
    }

    /**
     * Standard two-pass Wordle evaluation. Returns a per-position array of
     * {@link #GREEN}/{@link #YELLOW}, or 0 for gray (absent).
     */
    public static int[] evaluate(String guess, String answer) {
        int[] result = new int[WORD_LENGTH];
        char[] ans = answer.toCharArray();
        boolean[] used = new boolean[WORD_LENGTH];

        // First pass: exact (green) matches.
        for (int i = 0; i < WORD_LENGTH; i++) {
            if (guess.charAt(i) == ans[i]) {
                result[i] = GREEN;
                used[i] = true;
            }
        }
        // Second pass: present-but-misplaced (yellow), constrained by remaining letters.
        for (int i = 0; i < WORD_LENGTH; i++) {
            if (result[i] == GREEN) continue;
            for (int j = 0; j < WORD_LENGTH; j++) {
                if (!used[j] && guess.charAt(i) == ans[j]) {
                    result[i] = YELLOW;
                    used[j] = true;
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Hard-mode validation: every previously revealed green must stay in place and every
     * previously revealed yellow letter must be reused. Returns a human-readable reason,
     * or {@code null} if the guess is allowed.
     */
    public static String hardModeViolation(List<String> priorGuesses, String answer, String guess) {
        char[] greenAt = new char[WORD_LENGTH];
        Set<Character> requiredLetters = new HashSet<>();

        for (String prior : priorGuesses) {
            int[] eval = evaluate(prior, answer);
            for (int i = 0; i < WORD_LENGTH; i++) {
                if (eval[i] == GREEN) {
                    greenAt[i] = prior.charAt(i);
                } else if (eval[i] == YELLOW) {
                    requiredLetters.add(prior.charAt(i));
                }
            }
        }

        for (int i = 0; i < WORD_LENGTH; i++) {
            if (greenAt[i] != 0 && guess.charAt(i) != greenAt[i]) {
                return "Letter " + (i + 1) + " must be "
                        + Character.toUpperCase(greenAt[i]) + " (hard mode).";
            }
        }
        for (char c : requiredLetters) {
            if (guess.indexOf(c) < 0) {
                return "Guess must contain " + Character.toUpperCase(c) + " (hard mode).";
            }
        }
        return null;
    }
}
